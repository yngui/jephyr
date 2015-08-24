/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Igor Konev
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jvnet.zephyr.thread.continuation;

import org.jvnet.zephyr.continuation.Continuation;
import org.jvnet.zephyr.thread.ThreadAccess;
import org.jvnet.zephyr.thread.ThreadImpl;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

final class ContinuationThreadImpl<T extends Runnable> extends ThreadImpl<T> {

    private static final int NONE = 0;
    private static final int PARK = 1;
    private static final int TIMED_PARK = 2;
    private static final int YIELD = 3;

    private final AtomicInteger state = new AtomicInteger(NEW);
    private final Runnable unparkTask = new UnparkTask();
    private final AtomicReference<Node<T>> joiner = new AtomicReference<>();
    private final T thread;
    private final ThreadAccess<T> threadAccess;
    private final Executor executor;
    private final ScheduledExecutorService scheduler;
    private final Continuation continuation;
    private final Runnable executeTask;
    private volatile boolean interrupted;
    private volatile boolean unparked;
    private ScheduledFuture<?> cancelable;
    private int action;
    private volatile Thread javaThread;

    ContinuationThreadImpl(T thread, ThreadAccess<T> threadAccess, Executor executor,
            ScheduledExecutorService scheduler) {
        this.thread = thread;
        this.threadAccess = threadAccess;
        this.executor = executor;
        this.scheduler = scheduler;
        continuation = Continuation.create(thread);
        executeTask = executor instanceof AdaptingExecutor ? ((AdaptingExecutor) executor).adapt(new ExecuteTask()) :
                new ExecuteTask();
    }

    @Override
    public int getState() {
        return state.get();
    }

    @Override
    public boolean isAlive() {
        int state = this.state.get();
        return state != NEW && state != TERMINATED;
    }

    @Override
    public void start() {
        if (!state.compareAndSet(NEW, RUNNABLE)) {
            throw new IllegalStateException();
        }
        executor.execute(executeTask);
    }

    @Override
    public void park() {
        if (unparked) {
            unparked = false;
        } else {
            action = PARK;
            Continuation.suspend();
            if (action != NONE) {
                javaThread = Thread.currentThread();
                state.set(WAITING);
                if (unparked && state.compareAndSet(WAITING, RUNNABLE)) {
                    unparked = false;
                    javaThread = null;
                } else {
                    while (javaThread != null) {
                        LockSupport.park();
                    }
                }
            }
        }
    }

    @Override
    public void park(long timeout, TimeUnit unit) {
        if (unparked) {
            unparked = false;
        } else {
            cancelable = scheduler.schedule(unparkTask, timeout, unit);
            action = TIMED_PARK;
            Continuation.suspend();
            if (action != NONE) {
                javaThread = Thread.currentThread();
                state.set(TIMED_WAITING);
                if (unparked && state.compareAndSet(TIMED_WAITING, RUNNABLE)) {
                    cancelable.cancel(false);
                    cancelable = null;
                    unparked = false;
                    javaThread = null;
                } else {
                    while (javaThread != null) {
                        LockSupport.park();
                    }
                }
            }
        }
    }

    @Override
    public void parkUntil(long deadline) {
        long delay = deadline - System.currentTimeMillis();
        if (delay > 0) {
            park(delay, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void unpark() {
        unparked = true;
        while (true) {
            int state = this.state.get();
            if (state == WAITING) {
                if (this.state.compareAndSet(WAITING, RUNNABLE)) {
                    unparked = false;
                    Thread javaThread = this.javaThread;
                    if (javaThread == null) {
                        executor.execute(executeTask);
                    } else {
                        this.javaThread = null;
                        LockSupport.unpark(javaThread);
                    }
                    break;
                }
            } else if (state == TIMED_WAITING) {
                if (this.state.compareAndSet(TIMED_WAITING, RUNNABLE)) {
                    cancelable.cancel(false);
                    cancelable = null;
                    unparked = false;
                    Thread javaThread = this.javaThread;
                    if (javaThread == null) {
                        executor.execute(executeTask);
                    } else {
                        this.javaThread = null;
                        LockSupport.unpark(javaThread);
                    }
                    break;
                }
            } else {
                break;
            }
        }
    }

    @Override
    public void sleep(long timeout, TimeUnit unit) throws InterruptedException {
        if (timeout < 0) {
            throw new IllegalArgumentException();
        }
        long start = System.currentTimeMillis();
        long remaining = timeout;
        while (remaining > 0) {
            park(remaining, unit);
            if (interrupted()) {
                throw new InterruptedException();
            }
            remaining = start - System.currentTimeMillis() + timeout;
        }
    }

    @Override
    public void join() throws InterruptedException {
        T thread = threadAccess.currentThread();
        ThreadImpl<T> impl = threadAccess.getImpl(thread);

        Node<T> node;
        Node<T> next;
        do {
            next = joiner.get();
            node = new Node<>(thread, next);
        } while (!joiner.compareAndSet(next, node));

        while (isAlive()) {
            impl.park();
            if (impl.interrupted()) {
                throw new InterruptedException();
            }
        }

        node.thread.set(null);
    }

    @Override
    public void join(long timeout, TimeUnit unit) throws InterruptedException {
        if (timeout < 0) {
            throw new IllegalArgumentException("timeout value is negative");
        }

        long start = System.currentTimeMillis();
        T thread = threadAccess.currentThread();
        ThreadImpl<T> impl = threadAccess.getImpl(thread);

        Node<T> node;
        Node<T> next;
        do {
            next = joiner.get();
            node = new Node<>(thread, next);
        } while (!joiner.compareAndSet(next, node));

        long remaining = timeout;
        while (isAlive() && remaining > 0) {
            impl.park(remaining, unit);
            if (impl.interrupted()) {
                throw new InterruptedException();
            }
            remaining = start - System.currentTimeMillis() + timeout;
        }

        node.thread.set(null);
    }

    @Override
    public boolean isInterrupted() {
        return interrupted;
    }

    @Override
    public boolean interrupted() {
        if (interrupted) {
            interrupted = false;
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void interrupt() {
        interrupted = true;
        unpark();
    }

    @Override
    public void yield() {
        action = YIELD;
        Continuation.suspend();
        if (action != NONE) {
            Thread.yield();
        }
    }

    private void execute() {
        threadAccess.setCurrentThread(thread);

        action = NONE;
        try {
            continuation.resume();
        } catch (Throwable e) {
            threadAccess.dispatchUncaughtException(thread, e);
        }

        if (continuation.isDone()) {
            state.set(TERMINATED);
            Node<T> node = joiner.getAndSet(null);
            while (node != null) {
                T thread = node.thread.getAndSet(null);
                if (thread != null) {
                    threadAccess.getImpl(thread).unpark();
                }
                node = node.next;
            }
        } else {
            switch (action) {
                case PARK:
                    state.set(WAITING);
                    if (unparked && state.compareAndSet(WAITING, RUNNABLE)) {
                        unparked = false;
                        executor.execute(executeTask);
                    }
                    break;
                case TIMED_PARK:
                    state.set(TIMED_WAITING);
                    if (unparked && state.compareAndSet(TIMED_WAITING, RUNNABLE)) {
                        cancelable.cancel(false);
                        cancelable = null;
                        unparked = false;
                        executor.execute(executeTask);
                    }
                    break;
                default:
                    executor.execute(executeTask);
            }
        }
    }

    private final class ExecuteTask implements Runnable {

        ExecuteTask() {
        }

        @Override
        public void run() {
            execute();
        }
    }

    private final class UnparkTask implements Runnable {

        UnparkTask() {
        }

        @Override
        public void run() {
            unpark();
        }
    }

    private static final class Node<T> {

        final AtomicReference<T> thread = new AtomicReference<>();
        final Node<T> next;

        Node(T thread, Node<T> next) {
            this.thread.set(thread);
            this.next = next;
        }
    }
}

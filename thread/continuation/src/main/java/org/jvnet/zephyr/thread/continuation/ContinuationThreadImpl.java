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
    private boolean yielded;
    private volatile boolean unsuspendable;
    private Thread javaThread;

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
            cancelable = null;
            if (unsuspendable) {
                state.set(WAITING);
                while (state.get() != RUNNABLE) {
                    LockSupport.park();
                }
            } else {
                Continuation.suspend();
            }
        }
    }

    @Override
    public void park(long timeout, TimeUnit unit) {
        if (unparked) {
            unparked = false;
        } else {
            cancelable = scheduler.schedule(unparkTask, timeout, unit);
            if (unsuspendable) {
                state.set(TIMED_WAITING);
                while (state.get() != RUNNABLE) {
                    LockSupport.park();
                }
            } else {
                Continuation.suspend();
            }
        }
    }

    @Override
    public void parkUntil(long deadline) {
        if (unparked) {
            unparked = false;
        } else {
            long delay = deadline - System.currentTimeMillis();
            if (delay > 0) {
                cancelable = scheduler.schedule(unparkTask, delay, TimeUnit.MILLISECONDS);
                if (unsuspendable) {
                    state.set(TIMED_WAITING);
                    while (state.get() != RUNNABLE) {
                        LockSupport.park();
                    }
                } else {
                    Continuation.suspend();
                }
            }
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
                    if (unsuspendable) {
                        LockSupport.unpark(javaThread);
                    } else {
                        executor.execute(executeTask);
                    }
                    return;
                }
            } else if (state == TIMED_WAITING) {
                if (this.state.compareAndSet(TIMED_WAITING, RUNNABLE)) {
                    unparked = false;
                    cancelable.cancel(false);
                    cancelable = null;
                    if (unsuspendable) {
                        LockSupport.unpark(javaThread);
                    } else {
                        executor.execute(executeTask);
                    }
                    return;
                }
            } else {
                return;
            }
        }
    }

    @Override
    public void sleep(long timeout, TimeUnit unit) throws InterruptedException {
        if (timeout < 0) {
            throw new IllegalArgumentException();
        }
        if (timeout == 0) {
            return;
        }
        long start = System.currentTimeMillis();
        long remaining = timeout;
        do {
            cancelable = scheduler.schedule(unparkTask, remaining, unit);
            if (unsuspendable) {
                state.set(TIMED_WAITING);
                while (state.get() != RUNNABLE) {
                    LockSupport.park();
                }
            } else {
                Continuation.suspend();
            }
            if (interrupted()) {
                throw new InterruptedException();
            }
            remaining = start - System.currentTimeMillis() + timeout;
        } while (remaining > 0);
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
        long base = System.currentTimeMillis();

        if (timeout < 0) {
            throw new IllegalArgumentException("timeout value is negative");
        }

        T thread = threadAccess.currentThread();
        ThreadImpl<T> impl = threadAccess.getImpl(thread);

        Node<T> node;
        Node<T> next;
        do {
            next = joiner.get();
            node = new Node<>(thread, next);
        } while (!joiner.compareAndSet(next, node));

        long now = 0;
        while (isAlive()) {
            long remaining = timeout - now;
            if (remaining <= 0) {
                break;
            }
            impl.park(remaining, unit);
            if (impl.interrupted()) {
                throw new InterruptedException();
            }
            now = System.currentTimeMillis() - base;
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
        if (unsuspendable) {
            Thread.yield();
        } else {
            yielded = true;
            Continuation.suspend();
        }
    }

    private void execute() {
        threadAccess.setCurrentThread(thread);

        boolean suspended;
        try {
            suspended = continuation.resume();
        } catch (Throwable e) {
            boolean unsuspendable = this.unsuspendable;
            this.unsuspendable = true;
            try {
                threadAccess.dispatchUncaughtException(thread, e);
            } finally {
                this.unsuspendable = unsuspendable;
            }
            suspended = false;
        }

        if (!suspended) {
            state.set(TERMINATED);
            Node<T> node = joiner.getAndSet(null);
            while (node != null) {
                T thread = node.thread.getAndSet(null);
                if (thread != null) {
                    threadAccess.getImpl(thread).unpark();
                }
                node = node.next;
            }
            return;
        }

        if (yielded) {
            yielded = false;
            executor.execute(executeTask);
            return;
        }

        if (cancelable == null) {
            state.set(WAITING);
        } else {
            state.set(TIMED_WAITING);
        }

        if (unparked) {
            while (true) {
                int state = this.state.get();
                if (state == WAITING) {
                    if (this.state.compareAndSet(WAITING, RUNNABLE)) {
                        unparked = false;
                        executor.execute(executeTask);
                        return;
                    }
                } else if (state == TIMED_WAITING) {
                    if (this.state.compareAndSet(TIMED_WAITING, RUNNABLE)) {
                        unparked = false;
                        cancelable.cancel(false);
                        cancelable = null;
                        executor.execute(executeTask);
                        return;
                    }
                } else {
                    return;
                }
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

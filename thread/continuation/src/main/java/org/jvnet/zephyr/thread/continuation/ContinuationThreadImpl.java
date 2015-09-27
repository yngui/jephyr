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
import org.jvnet.zephyr.continuation.UnsuspendableError;
import org.jvnet.zephyr.thread.ThreadAccess;
import org.jvnet.zephyr.thread.ThreadImpl;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ManagedBlocker;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

final class ContinuationThreadImpl<T extends Runnable> extends ThreadImpl<T> {

    private static final int PARK = 0;
    private static final int TIMED_PARK = 1;
    private static final int YIELD = 2;

    private static final boolean debug;
    private final AtomicInteger state = new AtomicInteger(NEW);
    private final ForkJoinTask<Void> executeTask = new ExecuteTask();
    private final Runnable unparkTask = new UnparkTask();
    private final ManagedBlocker blocker = new ParkBlocker();
    private final AtomicReference<Node<T>> joiner = new AtomicReference<>();
    private final T thread;
    private final ThreadAccess<T, ?> threadAccess;
    private final ForkJoinPool pool;
    private final ScheduledExecutorService scheduler;
    private final Continuation continuation;
    private volatile boolean interrupted;
    private volatile boolean unparked;
    private ScheduledFuture<?> cancelable;
    private int action;
    private volatile Thread javaThread;

    static {
        debug = Boolean.getBoolean(ContinuationThreadImpl.class.getName() + ".debug");
    }

    ContinuationThreadImpl(T thread, ThreadAccess<T, ?> threadAccess, ForkJoinPool pool,
            ScheduledExecutorService scheduler) {
        this.thread = thread;
        this.threadAccess = threadAccess;
        this.pool = pool;
        this.scheduler = scheduler;
        continuation = Continuation.create(thread);
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
        pool.execute(executeTask);
    }

    @Override
    public void park() {
        if (unparked) {
            unparked = false;
        } else {
            action = PARK;
            try {
                Continuation.suspend();
            } catch (UnsuspendableError e) {
                if (debug) {
                    System.err.println("Cannot suspend: " + e.getMessage());
                }
                javaThread = Thread.currentThread();
                state.set(WAITING);
                if (unparked && state.compareAndSet(WAITING, RUNNABLE)) {
                    unparked = false;
                    javaThread = null;
                } else {
                    try {
                        ForkJoinPool.managedBlock(blocker);
                    } catch (InterruptedException ignored) {
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
            try {
                Continuation.suspend();
            } catch (UnsuspendableError e) {
                if (debug) {
                    System.err.println("Cannot suspend: " + e.getMessage());
                }
                javaThread = Thread.currentThread();
                state.set(TIMED_WAITING);
                if (unparked && state.compareAndSet(TIMED_WAITING, RUNNABLE)) {
                    cancelable.cancel(false);
                    cancelable = null;
                    unparked = false;
                    javaThread = null;
                } else {
                    try {
                        ForkJoinPool.managedBlock(blocker);
                    } catch (InterruptedException ignored) {
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
            switch (state.get()) {
                case WAITING:
                    if (state.compareAndSet(WAITING, RUNNABLE)) {
                        unparked = false;
                        Thread javaThread = this.javaThread;
                        if (javaThread == null) {
                            pool.execute(executeTask);
                        } else {
                            this.javaThread = null;
                            LockSupport.unpark(javaThread);
                        }
                        return;
                    }
                    break;
                case TIMED_WAITING:
                    if (state.compareAndSet(TIMED_WAITING, RUNNABLE)) {
                        cancelable.cancel(false);
                        cancelable = null;
                        unparked = false;
                        Thread javaThread = this.javaThread;
                        if (javaThread == null) {
                            pool.execute(executeTask);
                        } else {
                            this.javaThread = null;
                            LockSupport.unpark(javaThread);
                        }
                        return;
                    }
                    break;
                default:
                    return;
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
        try {
            Continuation.suspend();
        } catch (UnsuspendableError e) {
            if (debug) {
                System.err.println("Cannot suspend: " + e.getMessage());
            }
            Thread.yield();
        }
    }

    private void execute() {
        threadAccess.setCurrentThread(thread);

        boolean suspended;
        try {
            suspended = continuation.resume();
        } catch (Throwable e) {
            threadAccess.dispatchUncaughtException(thread, e);
            suspended = false;
        }

        if (suspended) {
            switch (action) {
                case PARK:
                    state.set(WAITING);
                    if (unparked && state.compareAndSet(WAITING, RUNNABLE)) {
                        unparked = false;
                        pool.execute(executeTask);
                    }
                    break;
                case TIMED_PARK:
                    state.set(TIMED_WAITING);
                    if (unparked && state.compareAndSet(TIMED_WAITING, RUNNABLE)) {
                        cancelable.cancel(false);
                        cancelable = null;
                        unparked = false;
                        pool.execute(executeTask);
                    }
                    break;
                default:
                    pool.execute(executeTask);
            }
        } else {
            state.set(TERMINATED);
            Node<T> node = joiner.getAndSet(null);
            while (node != null) {
                T thread = node.thread.getAndSet(null);
                if (thread != null) {
                    threadAccess.getImpl(thread).unpark();
                }
                node = node.next;
            }
        }
    }

    private final class ExecuteTask extends ForkJoinTask<Void> {

        ExecuteTask() {
        }

        @Override
        public Void getRawResult() {
            return null;
        }

        @Override
        protected void setRawResult(Void value) {
        }

        @Override
        protected boolean exec() {
            execute();
            return false;
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

    private final class ParkBlocker implements ManagedBlocker {

        ParkBlocker() {
        }

        @Override
        public boolean block() {
            LockSupport.park();
            return false;
        }

        @Override
        public boolean isReleasable() {
            return javaThread == null;
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

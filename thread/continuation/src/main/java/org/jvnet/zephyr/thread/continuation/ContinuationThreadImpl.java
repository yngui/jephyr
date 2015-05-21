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
import org.jvnet.zephyr.jcl.java.lang.Thread;
import org.jvnet.zephyr.jcl.java.lang.Thread.State;
import org.jvnet.zephyr.jcl.java.lang.ThreadUtils;
import org.jvnet.zephyr.thread.ThreadImpl;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

final class ContinuationThreadImpl extends ThreadImpl implements Runnable {

    private static final int NEW = 0;
    private static final int RUNNABLE = 1;
    private static final int WAITING = 2;
    private static final int TIMED_WAITING = 3;
    private static final int TERMINATED = 4;

    private static final AtomicInteger nextNum = new AtomicInteger();

    private final Executor executor;
    private final ScheduledExecutorService scheduler;
    private final Continuation continuation;
    private final Runnable task;
    private final AtomicInteger state = new AtomicInteger(NEW);
    private final Runnable unparkTask = new UnparkTask();
    private final AtomicReference<Node> joiner = new AtomicReference<>();
    private volatile String name;
    private volatile int priority;
    private volatile boolean daemon;
    private volatile Object blocker;
    private volatile boolean interrupted;
    private volatile boolean unparked;
    private ScheduledFuture<?> cancelable;
    private boolean yielded;
    private volatile boolean managed = true;
    private java.lang.Thread javaThread;

    ContinuationThreadImpl(Thread thread, Executor executor, ScheduledExecutorService scheduler) {
        this(thread, executor, scheduler, "Thread-" + nextNum.getAndIncrement());
    }

    ContinuationThreadImpl(Thread thread, Executor executor, ScheduledExecutorService scheduler, String name) {
        super(thread);
        this.executor = executor;
        this.scheduler = scheduler;
        this.name = name;
        continuation = Continuation.create(thread);
        task = executor instanceof AdaptingExecutor ? ((AdaptingExecutor) executor).adapt(this) : this;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public void setPriority(int priority) {
        this.priority = priority;
    }

    @Override
    public boolean isDaemon() {
        return daemon;
    }

    @Override
    public void setDaemon(boolean daemon) {
        if (isAlive()) {
            throw new IllegalThreadStateException();
        }
        this.daemon = daemon;
    }

    @Override
    public State getState() {
        switch (state.get()) {
            case NEW:
                return State.NEW;
            case RUNNABLE:
                return State.RUNNABLE;
            case WAITING:
                return State.WAITING;
            case TIMED_WAITING:
                return State.TIMED_WAITING;
            case TERMINATED:
                return State.TERMINATED;
            default:
                throw new AssertionError();
        }
    }

    @Override
    public boolean isAlive() {
        int state = this.state.get();
        return state != NEW && state != TERMINATED;
    }

    @Override
    public Object getBlocker() {
        return blocker;
    }

    @Override
    public boolean isInterrupted() {
        return interrupted;
    }

    @Override
    public void start() {
        if (!state.compareAndSet(NEW, RUNNABLE)) {
            throw new IllegalStateException();
        }
        executor.execute(task);
    }

    @Override
    public void park() {
        if (unparked) {
            unparked = false;
        } else {
            cancelable = null;
            if (managed) {
                Continuation.suspend();
            } else {
                state.set(WAITING);
                while (state.get() != RUNNABLE) {
                    LockSupport.park();
                }
            }
        }
    }

    @Override
    public void park(Object blocker) {
        this.blocker = blocker;
        park();
        this.blocker = null;
    }

    @Override
    public void parkNanos(long nanos) {
        if (unparked) {
            unparked = false;
        } else {
            cancelable = scheduler.schedule(unparkTask, nanos, TimeUnit.NANOSECONDS);
            if (managed) {
                Continuation.suspend();
            } else {
                state.set(TIMED_WAITING);
                while (state.get() != RUNNABLE) {
                    LockSupport.park();
                }
            }
        }
    }

    @Override
    public void parkNanos(Object blocker, long nanos) {
        this.blocker = blocker;
        parkNanos(nanos);
        this.blocker = null;
    }

    @Override
    public void parkUntil(long deadline) {
        if (unparked) {
            unparked = false;
        } else {
            long delay = deadline - System.currentTimeMillis();
            if (delay > 0) {
                cancelable = scheduler.schedule(unparkTask, delay, TimeUnit.MILLISECONDS);
                if (managed) {
                    Continuation.suspend();
                } else {
                    state.set(TIMED_WAITING);
                    while (state.get() != RUNNABLE) {
                        LockSupport.park();
                    }
                }
            }
        }
    }

    @Override
    public void parkUntil(Object blocker, long deadline) {
        this.blocker = blocker;
        parkUntil(deadline);
        this.blocker = null;
    }

    @Override
    public void unpark() {
        unparked = true;
        while (true) {
            int state = this.state.get();
            if (state == WAITING) {
                if (this.state.compareAndSet(WAITING, RUNNABLE)) {
                    unparked = false;
                    if (managed) {
                        executor.execute(task);
                    } else {
                        LockSupport.unpark(javaThread);
                    }
                    return;
                }
            } else if (state == TIMED_WAITING) {
                if (this.state.compareAndSet(TIMED_WAITING, RUNNABLE)) {
                    unparked = false;
                    cancelable.cancel(false);
                    cancelable = null;
                    if (managed) {
                        executor.execute(task);
                    } else {
                        LockSupport.unpark(javaThread);
                    }
                    return;
                }
            } else {
                return;
            }
        }
    }

    @Override
    public void sleep(long millis) throws InterruptedException {
        if (millis < 0) {
            throw new IllegalArgumentException();
        }
        if (millis == 0) {
            return;
        }
        long start = System.currentTimeMillis();
        long delay = millis;
        do {
            cancelable = scheduler.schedule(unparkTask, delay, TimeUnit.MILLISECONDS);
            if (managed) {
                Continuation.suspend();
            } else {
                state.set(TIMED_WAITING);
                while (state.get() != RUNNABLE) {
                    LockSupport.park();
                }
            }
            if (interrupted()) {
                throw new InterruptedException();
            }
            delay = start - System.currentTimeMillis() + millis;
        } while (delay > 0);
    }

    @Override
    public void sleep(long millis, int nanos) throws InterruptedException {
        if (millis < 0) {
            throw new IllegalArgumentException("timeout value is negative");
        }
        if (nanos < 0 || nanos > 999999) {
            throw new IllegalArgumentException("nanosecond timeout value out of range");
        }
        if (nanos >= 500000 || nanos != 0 && millis == 0) {
            millis++;
        }
        sleep(millis);
    }

    @Override
    public void join() throws InterruptedException {
        join(0);
    }

    @Override
    public void join(long millis) throws InterruptedException {
        long base = System.currentTimeMillis();

        if (millis < 0) {
            throw new IllegalArgumentException("timeout value is negative");
        }

        Node node;
        Node next;
        do {
            next = joiner.get();
            node = new Node(Thread.currentThread(), next);
        } while (!joiner.compareAndSet(next, node));

        if (millis == 0) {
            while (isAlive()) {
                ThreadUtils.park();
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
            }
        } else {
            long now = 0;
            while (isAlive()) {
                long delay = millis - now;
                if (delay <= 0) {
                    break;
                }
                ThreadUtils.parkNanos(delay > Long.MAX_VALUE / 1000000 ? Long.MAX_VALUE : delay * 1000000);
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
                now = System.currentTimeMillis() - base;
            }
        }

        node.thread.set(null);
    }

    @Override
    public void join(long millis, int nanos) throws InterruptedException {
        if (millis < 0) {
            throw new IllegalArgumentException("timeout value is negative");
        }
        if (nanos < 0 || nanos > 999999) {
            throw new IllegalArgumentException("nanosecond timeout value out of range");
        }
        if (nanos >= 500000 || nanos != 0 && millis == 0) {
            millis++;
        }
        join(millis);
    }

    @Override
    public void interrupt() {
        interrupted = true;
        unpark();
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
    public void yield() {
        if (managed) {
            yielded = true;
            Continuation.suspend();
        } else {
            java.lang.Thread.yield();
        }
    }

    @Override
    public boolean managed() {
        boolean managed = this.managed;
        javaThread = java.lang.Thread.currentThread();
        this.managed = false;
        return managed;
    }

    @Override
    public void manage() {
        javaThread = null;
        managed = true;
    }

    @Override
    public String toString() {
        return "Thread[" + getThread().getName() + ',' + priority + ']';
    }

    @Override
    public void run() {
        setCurrentThread(getThread());

        boolean suspended;
        try {
            suspended = continuation.resume();
        } catch (Throwable e) {
            dispatchUncaughtException(getThread(), e);
            suspended = false;
        }

        if (!suspended) {
            state.set(TERMINATED);
            Node node = joiner.getAndSet(null);
            while (node != null) {
                Thread thread = node.thread.getAndSet(null);
                if (thread != null) {
                    ThreadUtils.unpark(thread);
                }
                node = node.next;
            }
            return;
        }

        if (yielded) {
            yielded = false;
            executor.execute(task);
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
                        executor.execute(task);
                        return;
                    }
                } else if (state == TIMED_WAITING) {
                    if (this.state.compareAndSet(TIMED_WAITING, RUNNABLE)) {
                        unparked = false;
                        cancelable.cancel(false);
                        cancelable = null;
                        executor.execute(task);
                        return;
                    }
                } else {
                    return;
                }
            }
        }
    }

    private static final class Node {

        final AtomicReference<Thread> thread = new AtomicReference<>();
        final Node next;

        Node(Thread thread, Node next) {
            this.thread.set(thread);
            this.next = next;
        }
    }

    private final class UnparkTask implements Runnable {

        @Override
        public void run() {
            unpark();
        }
    }
}

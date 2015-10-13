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

package org.jvnet.zephyr.thread;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static java.util.Objects.requireNonNull;

public final class JavaThreadImpl extends ThreadImpl {

    private final Thread javaThread;

    public JavaThreadImpl(Thread javaThread) {
        requireNonNull(javaThread);
        this.javaThread = javaThread;
    }

    @Override
    public int getState() {
        switch (javaThread.getState()) {
            case NEW:
                return NEW;
            case RUNNABLE:
                return RUNNABLE;
            case BLOCKED:
            case WAITING:
                return WAITING;
            case TIMED_WAITING:
                return TIMED_WAITING;
            case TERMINATED:
                return TERMINATED;
            default:
                throw new AssertionError();
        }
    }

    @Override
    public boolean isAlive() {
        return javaThread.isAlive();
    }

    @Override
    public void start() {
        javaThread.start();
    }

    @Override
    public void park() {
        LockSupport.park();
    }

    @Override
    public void park(long timeout, TimeUnit unit) {
        LockSupport.parkNanos(unit.toNanos(timeout));
    }

    @Override
    public void parkUntil(long deadline) {
        LockSupport.parkUntil(deadline);
    }

    @Override
    public void unpark() {
        LockSupport.unpark(javaThread);
    }

    @Override
    public void sleep(long timeout, TimeUnit unit) throws InterruptedException {
        long ms = unit.toMillis(timeout);
        Thread.sleep(ms, excessNanos(timeout, ms, unit));
    }

    @Override
    public void join() throws InterruptedException {
        javaThread.join();
    }

    @Override
    public void join(long timeout, TimeUnit unit) throws InterruptedException {
        long ms = unit.toMillis(timeout);
        javaThread.join(ms, excessNanos(timeout, ms, unit));
    }

    @Override
    public boolean isInterrupted() {
        return javaThread.isInterrupted();
    }

    @Override
    public boolean interrupted() {
        return Thread.interrupted();
    }

    @Override
    public void interrupt() {
        javaThread.interrupt();
    }

    @Override
    public void yield() {
        Thread.yield();
    }

    private static int excessNanos(long d, long m, TimeUnit unit) {
        switch (unit) {
            case NANOSECONDS:
                return (int) (d - m * 1000000L);
            case MICROSECONDS:
                return (int) (d * 1000L - m * 1000000L);
            default:
                return 0;
        }
    }
}

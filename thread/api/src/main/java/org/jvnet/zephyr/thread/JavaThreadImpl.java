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

import org.jvnet.zephyr.jcl.java.lang.Thread;
import org.jvnet.zephyr.jcl.java.lang.Thread.State;

import java.util.concurrent.locks.LockSupport;

final class JavaThreadImpl extends ThreadImpl {

    private final java.lang.Thread javaThread;

    JavaThreadImpl(Thread thread, java.lang.Thread javaThread) {
        super(thread);
        this.javaThread = javaThread;
    }

    @Override
    public String getName() {
        return javaThread.getName();
    }

    @Override
    public void setName(String name) {
        javaThread.setName(name);
    }

    @Override
    public int getPriority() {
        return javaThread.getPriority();
    }

    @Override
    public void setPriority(int priority) {
        javaThread.setPriority(priority);
    }

    @Override
    public boolean isDaemon() {
        return javaThread.isDaemon();
    }

    @Override
    public void setDaemon(boolean daemon) {
        javaThread.setDaemon(daemon);
    }

    @Override
    public State getState() {
        switch (javaThread.getState()) {
            case NEW:
                return State.NEW;
            case RUNNABLE:
                return State.RUNNABLE;
            case BLOCKED:
                return State.BLOCKED;
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
        return javaThread.isAlive();
    }

    @Override
    public Object getBlocker() {
        return LockSupport.getBlocker(javaThread);
    }

    @Override
    public boolean isInterrupted() {
        return javaThread.isInterrupted();
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
    public void park(Object blocker) {
        LockSupport.park(blocker);
    }

    @Override
    public void parkNanos(long nanos) {
        LockSupport.parkNanos(nanos);
    }

    @Override
    public void parkNanos(Object blocker, long nanos) {
        LockSupport.parkNanos(blocker, nanos);
    }

    @Override
    public void parkUntil(long deadline) {
        LockSupport.parkUntil(deadline);
    }

    @Override
    public void parkUntil(Object blocker, long deadline) {
        LockSupport.parkUntil(blocker, deadline);
    }

    @Override
    public void unpark() {
        LockSupport.unpark(javaThread);
    }

    @Override
    public void sleep(long millis) throws InterruptedException {
        java.lang.Thread.sleep(millis);
    }

    @Override
    public void sleep(long millis, int nanos) throws InterruptedException {
        java.lang.Thread.sleep(millis, nanos);
    }

    @Override
    public void join() throws InterruptedException {
        javaThread.join();
    }

    @Override
    public void join(long millis) throws InterruptedException {
        javaThread.join(millis);
    }

    @Override
    public void join(long millis, int nanos) throws InterruptedException {
        javaThread.join(millis, nanos);
    }

    @Override
    public void interrupt() {
        javaThread.interrupt();
    }

    @Override
    public boolean interrupted() {
        return java.lang.Thread.interrupted();
    }

    @Override
    public void yield() {
        java.lang.Thread.yield();
    }

    @Override
    public boolean managed() {
        return false;
    }

    @Override
    public void manage() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return javaThread.toString();
    }
}

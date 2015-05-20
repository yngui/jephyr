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

package org.jvnet.zephyr.continuation;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

final class ThreadContinuation extends Continuation {

    private static final long serialVersionUID = 1L;

    private final ContinuationThread thread;

    ThreadContinuation(Runnable target) {
        thread = new ContinuationThread(target);
        thread.setName(toString());
        thread.setDaemon(true);
        thread.start();
    }

    public static void suspend() {
        ContinuationThread thread = ContinuationThread.currentContinuation.get();
        if (thread == null) {
            throw new IllegalStateException();
        }
        thread.suspendContinuation();
    }

    @Override
    public boolean resume() {
        return thread.resumeContinuation();
    }

    private static final class ContinuationThread extends Thread {

        private static final int SUSPENDED = 0;
        private static final int RESUMED = 1;
        private static final int COMPLETE = 2;
        private static final int DONE = 3;

        static final ThreadLocal<ContinuationThread> currentContinuation = new ThreadLocal<>();

        private final Lock lock = new ReentrantLock();
        private final Condition resume = lock.newCondition();
        private final Condition suspend = lock.newCondition();
        private int state;
        private Throwable exception;

        ContinuationThread(Runnable target) {
            super(target);
        }

        @Override
        public void run() {
            currentContinuation.set(this);
            lock.lock();
            try {
                while (state == SUSPENDED) {
                    resume.awaitUninterruptibly();
                }
                super.run();
            } catch (Throwable e) {
                exception = e;
            } finally {
                state = COMPLETE;
                suspend.signal();
                lock.unlock();
            }
        }

        boolean resumeContinuation() {
            lock.lock();
            try {
                if (state == DONE) {
                    throw new IllegalStateException();
                }
                state = RESUMED;
                resume.signal();
                while (state == RESUMED) {
                    suspend.awaitUninterruptibly();
                }
                if (state == COMPLETE) {
                    state = DONE;
                    if (exception == null) {
                        return true;
                    }
                    throw ContinuationThread.<RuntimeException>throwException(exception);
                }
                return false;
            } finally {
                lock.unlock();
            }
        }

        @SuppressWarnings("unchecked")
        private static <E extends Throwable> E throwException(Throwable exception) throws E {
            throw (E) exception;
        }

        void suspendContinuation() {
            state = SUSPENDED;
            suspend.signal();
            while (state == SUSPENDED) {
                resume.awaitUninterruptibly();
            }
        }
    }
}

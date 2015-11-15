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

package org.jephyr.thread;

import java.util.concurrent.TimeUnit;

public abstract class ThreadImpl {

    public static final int NEW = 0;
    public static final int RUNNABLE = 1;
    public static final int WAITING = 2;
    public static final int TIMED_WAITING = 3;
    public static final int TERMINATED = 4;

    protected ThreadImpl() {
    }

    public static <T extends Runnable> ThreadImpl create(T thread, ThreadAccess<T> threadAccess,
            TerminationHandler terminationHandler) {
        return ThreadImplProvider.provider().createThreadImpl(thread, threadAccess, terminationHandler);
    }

    public abstract int getState();

    public abstract boolean isAlive();

    public abstract void start(boolean daemon);

    public abstract void park();

    public abstract void park(long timeout, TimeUnit unit);

    public abstract void parkUntil(long deadline);

    public abstract void unpark();

    public abstract void sleep(long timeout, TimeUnit unit) throws InterruptedException;

    public abstract void join() throws InterruptedException;

    public abstract void join(long timeout, TimeUnit unit) throws InterruptedException;

    public abstract boolean isInterrupted();

    public abstract boolean interrupted();

    public abstract void interrupt();

    public abstract void yield();
}

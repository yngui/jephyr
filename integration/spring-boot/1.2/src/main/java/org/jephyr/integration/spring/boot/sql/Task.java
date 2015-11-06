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

package org.jephyr.integration.spring.boot.sql;

import java.util.concurrent.Executor;
import java.util.concurrent.locks.LockSupport;

abstract class Task<R, E extends Throwable> implements Runnable {

    private final Thread thread = Thread.currentThread();
    private volatile boolean complete;
    private R result;
    private Throwable exception;

    @SuppressWarnings("unchecked")
    final R execute(Executor executor) throws E {
        executor.execute(this);
        while (!complete) {
            LockSupport.park(this);
        }
        if (exception == null) {
            return result;
        }
        if (exception instanceof Error) {
            throw (Error) exception;
        }
        if (exception instanceof RuntimeException) {
            throw (RuntimeException) exception;
        }
        throw (E) exception;
    }

    @Override
    public final void run() {
        try {
            result = doExecute();
        } catch (Throwable e) {
            exception = e;
        }
        complete = true;
        LockSupport.unpark(thread);
    }

    abstract R doExecute() throws E;
}

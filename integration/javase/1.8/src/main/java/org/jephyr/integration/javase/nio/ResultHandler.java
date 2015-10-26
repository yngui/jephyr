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

package org.jephyr.integration.javase.nio;

import jephyr.java.lang.Thread;
import jephyr.java.util.concurrent.locks.LockSupport;

import java.io.IOException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.CompletionHandler;

final class ResultHandler<V> implements CompletionHandler<V, Object> {

    private final Thread thread = Thread.currentThread();
    private final AutoCloseable closeable;
    private volatile boolean done;
    private V result;
    private Throwable exception;

    ResultHandler(AutoCloseable closeable) {
        this.closeable = closeable;
    }

    V result() throws IOException {
        checkInterrupted();
        while (!done) {
            LockSupport.park(this);
            checkInterrupted();
        }
        if (exception == null) {
            return result;
        }
        if (exception instanceof IOException) {
            throw (IOException) exception;
        }
        if (exception instanceof Error) {
            throw (Error) exception;
        }
        if (exception instanceof RuntimeException) {
            throw (RuntimeException) exception;
        }
        throw new RuntimeException(exception);
    }

    private void checkInterrupted() throws ClosedByInterruptException {
        if (Thread.interrupted()) {
            ClosedByInterruptException exception = new ClosedByInterruptException();
            try {
                closeable.close();
            } catch (Throwable e) {
                exception.addSuppressed(e);
            }
            throw exception;
        }
    }

    @Override
    public void completed(V result, Object attachment) {
        this.result = result;
        done = true;
        LockSupport.unpark(thread);
    }

    @Override
    public void failed(Throwable exc, Object attachment) {
        exception = exc;
        done = true;
        LockSupport.unpark(thread);
    }
}

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

package org.jephyr.common.util.concurrent;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

import static java.util.Objects.requireNonNull;

public abstract class RunnableFutureTask<V> implements RunnableFuture<V> {

    private final Sync<V> sync = new Sync<>();

    protected RunnableFutureTask() {
    }

    @Override
    public final boolean cancel(boolean mayInterruptIfRunning) {
        if (!sync.cancel(mayInterruptIfRunning)) {
            return false;
        }
        if (mayInterruptIfRunning) {
            interrupt();
        }
        return true;
    }

    @Override
    public final boolean isCancelled() {
        return sync.isCancelled();
    }

    @Override
    public final boolean isDone() {
        return sync.isDone();
    }

    @Override
    public final V get() throws InterruptedException, ExecutionException {
        return sync.get();
    }

    @Override
    public final V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return sync.get(timeout, unit);
    }

    protected final boolean set(V result) {
        return sync.set(result);
    }

    protected final boolean setException(Throwable exception) {
        requireNonNull(exception);
        return sync.setException(exception);
    }

    protected void interrupt() {
    }

    private static final class Sync<V> extends AbstractQueuedSynchronizer {

        private static final long serialVersionUID = 1L;

        private static final int RUNNING = 0;
        private static final int COMPLETING = 1;
        private static final int COMPLETED = 2;
        private static final int CANCELLED = 4;
        private static final int INTERRUPTED = 8;

        private V result;
        private Throwable exception;

        Sync() {
        }

        @Override
        protected int tryAcquireShared(int arg) {
            return isDone() ? 1 : -1;
        }

        @Override
        protected boolean tryReleaseShared(int arg) {
            setState(arg);
            return true;
        }

        boolean cancel(boolean mayInterruptIfRunning) {
            return complete(null, new CancellationException(), mayInterruptIfRunning ? INTERRUPTED : CANCELLED);
        }

        boolean isCancelled() {
            return (getState() & (CANCELLED | INTERRUPTED)) != 0;
        }

        boolean isDone() {
            return (getState() & (COMPLETED | CANCELLED | INTERRUPTED)) != 0;
        }

        V get() throws InterruptedException, ExecutionException {
            acquireSharedInterruptibly(-1);
            return getResult();
        }

        V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            if (!tryAcquireSharedNanos(-1, unit.toNanos(timeout))) {
                throw new TimeoutException();
            }
            return getResult();
        }

        boolean set(V result) {
            return complete(result, null, COMPLETED);
        }

        boolean setException(Throwable exception) {
            return complete(null, exception, COMPLETED);
        }

        private boolean complete(V result, Throwable exception, int state) {
            boolean doCompletion = compareAndSetState(RUNNING, COMPLETING);
            if (doCompletion) {
                this.result = result;
                this.exception = exception;
                releaseShared(state);
            } else if (getState() == COMPLETING) {
                acquireShared(-1);
            }
            return doCompletion;
        }

        private V getResult() throws ExecutionException {
            switch (getState()) {
                case COMPLETED:
                    if (exception != null) {
                        throw new ExecutionException(exception);
                    } else {
                        return result;
                    }
                case CANCELLED:
                case INTERRUPTED:
                    throw new CancellationException();
                default:
                    throw new AssertionError();
            }
        }
    }
}

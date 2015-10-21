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

package org.jephyr.continuation.easyflow;

import org.jephyr.continuation.Continuation;
import org.jephyr.continuation.ContinuationHolder;

public final class EasyFlowContinuation extends Continuation {

    private static final long serialVersionUID = 7643072361724561136L;

    private static final ContinuationThreadLocal currentContinuation = new ContinuationThreadLocal();

    private final ContinuationImpl impl;

    private EasyFlowContinuation(Runnable target) {
        impl = new ContinuationImpl(target);
    }

    public static EasyFlowContinuation create(Runnable target) {
        return new EasyFlowContinuation(target);
    }

    public static void suspend() {
        EasyFlowContinuation continuation = currentContinuation.get();
        if (continuation == null) {
            throw new IllegalStateException();
        }
        continuation.impl.suspend();
    }

    @Override
    public boolean resume() {
        EasyFlowContinuation continuation = currentContinuation.get();
        currentContinuation.set(this);
        try {
            return impl.resume();
        } finally {
            currentContinuation.set(continuation);
        }
    }

    static ContinuationImpl currentImpl() {
        EasyFlowContinuation continuation = currentContinuation.get();
        if (continuation == null) {
            return null;
        }
        return continuation.impl;
    }

    private static final class ContinuationThreadLocal {

        private static final ThreadLocal<EasyFlowContinuation> continuation = new ThreadLocal<>();

        ContinuationThreadLocal() {
        }

        EasyFlowContinuation get() {
            Thread thread = Thread.currentThread();
            if (thread instanceof ContinuationHolder) {
                return (EasyFlowContinuation) ((ContinuationHolder) thread).getContinuation();
            } else {
                return continuation.get();
            }
        }

        void set(EasyFlowContinuation continuation) {
            Thread thread = Thread.currentThread();
            if (thread instanceof ContinuationHolder) {
                ((ContinuationHolder) thread).setContinuation(continuation);
            } else {
                ContinuationThreadLocal.continuation.set(continuation);
            }
        }
    }
}

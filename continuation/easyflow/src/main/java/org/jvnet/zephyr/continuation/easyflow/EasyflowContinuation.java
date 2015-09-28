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

package org.jvnet.zephyr.continuation.easyflow;

import org.jvnet.zephyr.continuation.Continuation;
import org.jvnet.zephyr.continuation.UnsuspendableError;

public final class EasyFlowContinuation extends Continuation {

    private static final long serialVersionUID = -6013208741268527329L;

    private final org.jvnet.zephyr.easyflow.runtime.Continuation continuation;

    private EasyFlowContinuation(Runnable target) {
        continuation = org.jvnet.zephyr.easyflow.runtime.Continuation.create(target);
    }

    public static EasyFlowContinuation create(Runnable target) {
        return new EasyFlowContinuation(target);
    }

    public static void suspend() {
        try {
            org.jvnet.zephyr.easyflow.runtime.Continuation.suspend();
        } catch (org.jvnet.zephyr.easyflow.runtime.UnsuspendableError e) {
            throw new UnsuspendableError(e);
        }
    }

    @Override
    public boolean resume() {
        return continuation.resume();
    }
}

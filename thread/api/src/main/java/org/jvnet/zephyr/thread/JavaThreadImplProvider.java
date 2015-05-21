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

import java.lang.Thread.UncaughtExceptionHandler;

import static org.jvnet.zephyr.thread.ThreadImpl.dispatchUncaughtException;

final class JavaThreadImplProvider extends ThreadImplProvider {

    @Override
    public ThreadImpl createThreadImpl(Thread thread) {
        java.lang.Thread javaThread = new java.lang.Thread(thread);
        return createThreadImpl(thread, javaThread);
    }

    @Override
    public ThreadImpl createThreadImpl(Thread thread, String name) {
        java.lang.Thread javaThread = new java.lang.Thread(thread, name);
        return createThreadImpl(thread, javaThread);
    }

    private static ThreadImpl createThreadImpl(final Thread thread, java.lang.Thread javaThread) {
        javaThread.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {

            @Override
            public void uncaughtException(java.lang.Thread t, Throwable e) {
                dispatchUncaughtException(thread, e);
            }
        });
        return new JavaThreadImpl(thread, javaThread);
    }
}

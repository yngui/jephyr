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

package org.jvnet.zephyr.jcl.java.lang;

public final class ThreadUtils {

    private ThreadUtils() {
    }

    public static void unpark(Thread thread) {
        if (thread != null) {
            thread.impl.unpark();
        }
    }

    public static Object getBlocker(Thread thread) {
        return thread.impl.getBlocker();
    }

    public static void park() {
        Thread.currentThread().impl.park();
    }

    public static void park(Object blocker) {
        Thread.currentThread().impl.park(blocker);
    }

    public static void parkNanos(long nanos) {
        Thread.currentThread().impl.parkNanos(nanos);
    }

    public static void parkNanos(Object blocker, long nanos) {
        Thread.currentThread().impl.parkNanos(blocker, nanos);
    }

    public static void parkUntil(long deadline) {
        Thread.currentThread().impl.parkUntil(deadline);
    }

    public static void parkUntil(Object blocker, long deadline) {
        Thread.currentThread().impl.parkUntil(blocker, deadline);
    }

    public static boolean managed() {
        return Thread.currentThread().impl.managed();
    }

    public static void manage() {
        Thread.currentThread().impl.manage();
    }
}

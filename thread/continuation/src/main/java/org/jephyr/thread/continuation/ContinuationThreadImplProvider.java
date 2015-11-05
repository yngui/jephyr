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

package org.jephyr.thread.continuation;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import org.jephyr.thread.TerminationHandler;
import org.jephyr.thread.ThreadAccess;
import org.jephyr.thread.ThreadImpl;
import org.jephyr.thread.ThreadImplProvider;

import static java.util.Objects.requireNonNull;

public final class ContinuationThreadImplProvider extends ThreadImplProvider {

    private static final AtomicInteger providerNum = new AtomicInteger(1);
    private final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1, r -> {
        Thread thread = new Thread(r);
        thread.setName(ContinuationThreadImplProvider.class.getSimpleName() + '-' + providerNum.getAndIncrement() +
                "-scheduler");
        thread.setDaemon(true);
        return thread;
    });

    public ContinuationThreadImplProvider() {
        scheduler.prestartCoreThread();
    }

    @Override
    public <T extends Runnable> ThreadImpl createThreadImpl(T thread, ThreadAccess<T, ?> threadAccess,
            TerminationHandler terminationHandler) {
        requireNonNull(thread);
        requireNonNull(threadAccess);
        return new ContinuationThreadImpl<>(thread, threadAccess, ForkJoinPoolProvider.provider().getPool(), scheduler,
                terminationHandler);
    }
}

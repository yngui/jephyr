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

package org.jvnet.zephyr.thread.continuation;

import org.jvnet.zephyr.jcl.java.lang.Thread;
import org.jvnet.zephyr.thread.ThreadImpl;
import org.jvnet.zephyr.thread.ThreadImplProvider;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Objects.requireNonNull;

public final class ContinuationThreadImplProvider extends ThreadImplProvider {

    private static final String EXECUTOR = ContinuationThreadImplProvider.class.getName() + ".executor";

    private final Executor executor = loadExecutor();
    private final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1,
            new DaemonThreadFactory(ContinuationThreadImplProvider.class.getSimpleName() + "-scheduler-"));

    public ContinuationThreadImplProvider() {
        scheduler.prestartCoreThread();
    }

    @Override
    public ThreadImpl createThreadImpl(Thread thread) {
        return new ContinuationThreadImpl(thread, executor, scheduler);
    }

    @Override
    public ThreadImpl createThreadImpl(Thread thread, String name) {
        return new ContinuationThreadImpl(thread, executor, scheduler, requireNonNull(name));
    }

    private static Executor loadExecutor() {
        String name = System.getProperty(EXECUTOR);
        if (name == null) {
            return new DefaultForkJoinPoolExecutor();
        } else {
            try {
                return (Executor) ClassLoader.getSystemClassLoader().loadClass(name).getConstructor().newInstance();
            } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InstantiationException
                    | InvocationTargetException e) {
                throw new Error(e);
            }
        }
    }

    private static final class DaemonThreadFactory implements ThreadFactory {

        private final AtomicInteger threadNum = new AtomicInteger(1);
        private final String namePrefix;

        DaemonThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public java.lang.Thread newThread(Runnable r) {
            java.lang.Thread thread = new java.lang.Thread(r);
            thread.setName(namePrefix + threadNum.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}

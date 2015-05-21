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
import org.jvnet.zephyr.jcl.java.lang.Thread.State;
import org.jvnet.zephyr.jcl.java.lang.Thread.UncaughtExceptionHandler;
import org.jvnet.zephyr.jcl.java.lang.ThreadUtils;

import java.util.concurrent.atomic.AtomicLong;

import static java.util.Objects.requireNonNull;

public abstract class ThreadImpl {

    private static final AtomicLong nextId = new AtomicLong();
    private static final ThreadLocal<Thread> currentThread = new CurrentThread();
    private static volatile UncaughtExceptionHandler defaultUncaughtExceptionHandler;

    private final Thread thread;
    private final long id;
    private volatile UncaughtExceptionHandler uncaughtExceptionHandler;

    protected ThreadImpl(Thread thread) {
        this.thread = requireNonNull(thread);
        id = nextId.getAndIncrement();
    }

    public final long getId() {
        return id;
    }

    public static Thread getCurrentThread() {
        return currentThread.get();
    }

    protected static void setCurrentThread(Thread thread) {
        currentThread.set(thread);
    }

    public static UncaughtExceptionHandler getDefaultUncaughtExceptionHandler() {
        return defaultUncaughtExceptionHandler;
    }

    public static void setDefaultUncaughtExceptionHandler(UncaughtExceptionHandler defaultUncaughtExceptionHandler) {
        ThreadImpl.defaultUncaughtExceptionHandler = defaultUncaughtExceptionHandler;
    }

    protected final Thread getThread() {
        return thread;
    }

    public final UncaughtExceptionHandler getUncaughtExceptionHandler() {
        return uncaughtExceptionHandler == null ? defaultUncaughtExceptionHandler : uncaughtExceptionHandler;
    }

    public final void setUncaughtExceptionHandler(UncaughtExceptionHandler uncaughtExceptionHandler) {
        this.uncaughtExceptionHandler = uncaughtExceptionHandler;
    }

    protected static void dispatchUncaughtException(Thread thread, Throwable e) {
        boolean managed = ThreadUtils.managed();
        try {
            UncaughtExceptionHandler handler = thread.getUncaughtExceptionHandler();
            if (handler != null) {
                handler.uncaughtException(thread, e);
            } else {
                System.err.print("Exception in thread \"" + thread.getName() + "\" ");
                e.printStackTrace();
            }
        } catch (Throwable e2) {
            System.err
                    .println("Exception: " + e2.getClass() + " thrown from the UncaughtExceptionHandler in thread \"" +
                            thread.getName() + '"');
        } finally {
            if (managed) {
                ThreadUtils.manage();
            }
        }
    }

    public abstract String getName();

    public abstract void setName(String name);

    public abstract int getPriority();

    public abstract void setPriority(int priority);

    public abstract boolean isDaemon();

    public abstract void setDaemon(boolean daemon);

    public abstract State getState();

    public abstract boolean isAlive();

    public abstract Object getBlocker();

    public abstract boolean isInterrupted();

    public abstract void start();

    public abstract void park();

    public abstract void park(Object blocker);

    public abstract void parkNanos(long nanos);

    public abstract void parkNanos(Object blocker, long nanos);

    public abstract void parkUntil(long deadline);

    public abstract void parkUntil(Object blocker, long deadline);

    public abstract void unpark();

    public abstract void sleep(long millis) throws InterruptedException;

    public abstract void sleep(long millis, int nanos) throws InterruptedException;

    public abstract void join() throws InterruptedException;

    public abstract void join(long millis) throws InterruptedException;

    public abstract void join(long millis, int nanos) throws InterruptedException;

    public abstract void interrupt();

    public abstract boolean interrupted();

    public abstract void yield();

    public abstract boolean managed();

    public abstract void manage();

    private static final class CurrentThread extends ThreadLocal<Thread> {

        private static final ThreadImplProvider provider = new ThreadImplProvider() {

            @Override
            public ThreadImpl createThreadImpl(Thread thread) {
                return new JavaThreadImpl(thread, java.lang.Thread.currentThread());
            }

            @Override
            public ThreadImpl createThreadImpl(Thread thread, String name) {
                throw new UnsupportedOperationException();
            }
        };

        CurrentThread() {
        }

        @Override
        protected Thread initialValue() {
            return new Thread(provider, null);
        }
    }
}

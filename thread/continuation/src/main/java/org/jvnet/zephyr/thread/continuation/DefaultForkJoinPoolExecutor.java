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

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

import static java.util.Objects.requireNonNull;

public final class DefaultForkJoinPoolExecutor extends ForkJoinPool implements AdaptingExecutor {

    private static final String PARALLELISM = DefaultForkJoinPoolExecutor.class.getName() + ".parallelism";

    public DefaultForkJoinPoolExecutor() {
        super(loadParallelism(), defaultForkJoinWorkerThreadFactory, null, true);
    }

    private static int loadParallelism() {
        String s = System.getProperty(PARALLELISM);
        return s == null ? Runtime.getRuntime().availableProcessors() : Integer.parseInt(s);
    }

    @Override
    public void execute(Runnable task) {
        if (task instanceof ForkJoinTask) {
            if (ForkJoinTask.getPool() == this) {
                ((ForkJoinTask<?>) task).fork();
            } else {
                execute((ForkJoinTask<?>) task);
            }
        } else {
            execute((ForkJoinTask<?>) adapt(task));
        }
    }

    @Override
    public Runnable adapt(Runnable runnable) {
        return new AdaptedRunnable(runnable);
    }

    private static final class AdaptedRunnable extends ForkJoinTask<Void> implements Runnable {

        private static final long serialVersionUID = 1L;

        private final Runnable runnable;

        AdaptedRunnable(Runnable runnable) {
            this.runnable = requireNonNull(runnable);
        }

        @Override
        public Void getRawResult() {
            return null;
        }

        @Override
        public void setRawResult(Void value) {
        }

        @Override
        public boolean exec() {
            run();
            return false;
        }

        @Override
        public void run() {
            runnable.run();
        }
    }
}

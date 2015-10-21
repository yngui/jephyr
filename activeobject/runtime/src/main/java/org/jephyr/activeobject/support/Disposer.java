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

package org.jephyr.activeobject.support;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;

public final class Disposer {

    private static final Disposer defaultDisposer = new Disposer("Default Disposer");
    private final ReferenceQueue<Object> referenceQueue = new ReferenceQueue<>();
    private final Map<Object, Disposable> disposables = new ConcurrentHashMap<>();
    private final DisposerThread thread;

    public Disposer(String name) {
        thread = new DisposerThread(referenceQueue, disposables);
        thread.setName(name);
        thread.setDaemon(true);
        thread.start();
    }

    public static Disposer defaultDisposer() {
        return defaultDisposer;
    }

    public void register(Object obj, Disposable disposable) {
        requireNonNull(obj);
        requireNonNull(disposable);
        disposables.put(new PhantomReference<>(obj, referenceQueue), disposable);
    }

    @Override
    protected void finalize() throws Throwable {
        thread.stopped = true;
        thread.interrupt();
    }

    private static final class DisposerThread extends Thread {

        private final ReferenceQueue<?> referenceQueue;
        private final Map<?, ? extends Disposable> disposables;
        volatile boolean stopped;

        DisposerThread(ReferenceQueue<?> referenceQueue, Map<?, ? extends Disposable> disposables) {
            this.referenceQueue = referenceQueue;
            this.disposables = disposables;
        }

        @Override
        public void run() {
            Reference<?> reference;
            while (!stopped) {
                try {
                    reference = referenceQueue.remove();
                } catch (InterruptedException ignored) {
                    continue;
                }
                dispose(reference);
            }
            while ((reference = referenceQueue.poll()) != null) {
                dispose(reference);
            }
        }

        private void dispose(Reference<?> reference) {
            Disposable disposable = disposables.remove(reference);
            try {
                disposable.dispose();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }
}

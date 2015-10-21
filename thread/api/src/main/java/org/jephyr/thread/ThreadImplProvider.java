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

package org.jephyr.thread;

import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

public abstract class ThreadImplProvider {

    protected ThreadImplProvider() {
    }

    public static ThreadImplProvider provider() {
        return Holder.provider;
    }

    public abstract <T extends Runnable> ThreadImpl createThreadImpl(T thread, ThreadAccess<T, ?> threadAccess);

    private static final class Holder {

        static final ThreadImplProvider provider = load();

        private static ThreadImplProvider load() {
            String className = System.getProperty(ThreadImplProvider.class.getName());
            if (className == null) {
                ServiceLoader<ThreadImplProvider> loader = ServiceLoader.load(ThreadImplProvider.class);
                Iterator<ThreadImplProvider> iterator = loader.iterator();
                if (iterator.hasNext()) {
                    return iterator.next();
                } else {
                    return new JavaThreadImplProvider();
                }
            }
            try {
                return (ThreadImplProvider) Class.forName(className).getConstructor().newInstance();
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException |
                    InvocationTargetException | NoSuchMethodException e) {
                throw new ServiceConfigurationError(null, e);
            }
        }
    }
}

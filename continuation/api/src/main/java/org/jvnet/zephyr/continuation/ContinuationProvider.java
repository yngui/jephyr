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

package org.jvnet.zephyr.continuation;

import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

public abstract class ContinuationProvider {

    protected ContinuationProvider() {
    }

    public static ContinuationProvider provider() {
        return Holder.provider;
    }

    public abstract Continuation createContinuation(Runnable target);

    public abstract void suspendContinuation();

    private static final class Holder {

        static final ContinuationProvider provider = load();

        private static ContinuationProvider load() {
            String className = System.getProperty(ContinuationProvider.class.getName());
            if (className == null) {
                ServiceLoader<ContinuationProvider> loader = ServiceLoader.load(ContinuationProvider.class);
                Iterator<ContinuationProvider> iterator = loader.iterator();
                if (iterator.hasNext()) {
                    return iterator.next();
                } else {
                    throw new ServiceConfigurationError(ContinuationProvider.class.getSimpleName() + " not found");
                }
            }

            Class<?> cls;
            try {
                cls = Class.forName(className);
            } catch (ClassNotFoundException e) {
                throw new ServiceConfigurationError(null, e);
            }

            try {
                return (ContinuationProvider) cls.getConstructor().newInstance();
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                    NoSuchMethodException e) {
                throw new ServiceConfigurationError(null, e);
            }
        }
    }
}

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

import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.concurrent.ForkJoinPool;

public abstract class ForkJoinPoolProvider {

    protected ForkJoinPoolProvider() {
    }

    public static ForkJoinPoolProvider provider() {
        return Holder.provider;
    }

    public abstract ForkJoinPool getPool();

    private static final class Holder {

        static final ForkJoinPoolProvider provider = load();

        private static ForkJoinPoolProvider load() {
            String className = System.getProperty(ForkJoinPoolProvider.class.getName());
            if (className == null) {
                ServiceLoader<ForkJoinPoolProvider> loader = ServiceLoader.load(ForkJoinPoolProvider.class);
                Iterator<ForkJoinPoolProvider> iterator = loader.iterator();
                if (iterator.hasNext()) {
                    return iterator.next();
                } else {
                    return new DefaultForkJoinPoolProvider();
                }
            }
            try {
                return (ForkJoinPoolProvider) Class.forName(className).getConstructor().newInstance();
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException |
                    InvocationTargetException | NoSuchMethodException e) {
                throw new ServiceConfigurationError(null, e);
            }
        }
    }
}

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

package org.jephyr.parameters;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;

public final class ParameterNamesCache {

    private static final Map<Key, String[]> map = new ConcurrentHashMap<>();

    private ParameterNamesCache() {
    }

    public static String[] getParameterNames(ClassLoader loader, String owner, String name, String desc) {
        String[] names = map.get(new Key(loader, owner, name, desc));
        if (names == null) {
            return null;
        }
        return names.clone();
    }

    public static void addParameterNames(ClassLoader loader, String owner, String name, String desc, String[] names) {
        map.put(new Key(loader, owner, name, desc), names.clone());
    }

    private static final class Key {

        private final ClassLoader classLoader;
        private final String owner;
        private final String name;
        private final String desc;

        Key(ClassLoader classLoader, String owner, String name, String desc) {
            this.classLoader = classLoader;
            this.owner = requireNonNull(owner);
            this.name = requireNonNull(name);
            this.desc = requireNonNull(desc);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof Key)) {
                return false;
            }
            Key other = (Key) obj;
            return (classLoader == null ? other.classLoader == null : classLoader.equals(other.classLoader)) &&
                    owner.equals(other.owner) && name.equals(other.name) && desc.equals(other.desc);
        }

        @Override
        public int hashCode() {
            int result = classLoader != null ? classLoader.hashCode() : 0;
            result = 31 * result + owner.hashCode();
            result = 31 * result + name.hashCode();
            result = 31 * result + desc.hashCode();
            return result;
        }
    }
}

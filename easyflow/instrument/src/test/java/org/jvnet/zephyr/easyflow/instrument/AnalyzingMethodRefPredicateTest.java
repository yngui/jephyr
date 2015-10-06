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

package org.jvnet.zephyr.easyflow.instrument;

import org.apache.commons.io.IOUtils;
import org.jvnet.zephyr.common.util.function.Predicate;
import org.objectweb.asm.Type;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public final class AnalyzingMethodRefPredicateTest {

    private static final Predicate<MethodRef> TRUE_PREDICATE = new Predicate<MethodRef>() {
        @Override
        public boolean test(MethodRef t) {
            return true;
        }
    };

    @Test
    public void testApplyParentPredicate() throws Exception {
        class C {

            void m() {
                m2();
            }

            void m2() {
            }
        }
        Predicate<MethodRef> predicate = new AnalyzingMethodRefPredicate(getBytes(C.class), new Predicate<MethodRef>() {
            @Override
            public boolean test(MethodRef t) {
                return !t.getName().equals("m");
            }
        });
        assertFalse(predicate.test(getMethodRef(C.class.getDeclaredMethod("m"))));
    }

    @Test
    public void testApplyMethodWithoutCalls() throws Exception {
        class C {

            void m() {
            }
        }
        Predicate<MethodRef> predicate = new AnalyzingMethodRefPredicate(getBytes(C.class), TRUE_PREDICATE);
        assertFalse(predicate.test(getMethodRef(C.class.getDeclaredMethod("m"))));
    }

    @Test
    public void testApplyMethodWithCall() throws Exception {
        class C {

            void m() {
                m2();
            }

            void m2() {
            }
        }
        Predicate<MethodRef> predicate = new AnalyzingMethodRefPredicate(getBytes(C.class), TRUE_PREDICATE);
        assertTrue(predicate.test(getMethodRef(C.class.getDeclaredMethod("m"))));
    }

    @Test
    public void testApplyMethodWithCallFinalClass() throws Exception {
        final class C {

            void m() {
                m2();
            }

            void m2() {
            }
        }
        Predicate<MethodRef> predicate = new AnalyzingMethodRefPredicate(getBytes(C.class), TRUE_PREDICATE);
        assertFalse(predicate.test(getMethodRef(C.class.getDeclaredMethod("m"))));
    }

    @Test
    public void testApplyMethodWithCallPrivate() throws Exception {
        class C {

            void m() {
                m2();
            }

            private void m2() {
            }
        }
        Predicate<MethodRef> predicate = new AnalyzingMethodRefPredicate(getBytes(C.class), TRUE_PREDICATE);
        assertFalse(predicate.test(getMethodRef(C.class.getDeclaredMethod("m"))));
    }

    static class Static {

        void m() {
            m2();
        }

        static void m2() {
        }
    }

    @Test
    public void testApplyMethodWithCallStatic() throws Exception {
        Predicate<MethodRef> predicate = new AnalyzingMethodRefPredicate(getBytes(Static.class), TRUE_PREDICATE);
        assertFalse(predicate.test(getMethodRef(Static.class.getDeclaredMethod("m"))));
    }

    @Test
    public void testApplyMethodWithCallFinal() throws Exception {
        class C {

            void m() {
                m2();
            }

            final void m2() {
            }
        }
        Predicate<MethodRef> predicate = new AnalyzingMethodRefPredicate(getBytes(C.class), TRUE_PREDICATE);
        assertFalse(predicate.test(getMethodRef(C.class.getDeclaredMethod("m"))));
    }

    @Test
    public void testApplyMethodWithCallRecursion() throws Exception {
        class C {

            void m1() {
                m2();
            }

            private void m2() {
                m1();
            }
        }
        Predicate<MethodRef> predicate = new AnalyzingMethodRefPredicate(getBytes(C.class), TRUE_PREDICATE);
        assertTrue(predicate.test(getMethodRef(C.class.getDeclaredMethod("m1"))));
        assertTrue(predicate.test(getMethodRef(C.class.getDeclaredMethod("m2"))));
    }

    @Test
    public void testApplyMethodWithCallRecursionPrivate() throws Exception {
        class C {

            private void m1() {
                m2();
            }

            private void m2() {
                m1();
            }
        }
        Predicate<MethodRef> predicate = new AnalyzingMethodRefPredicate(getBytes(C.class), TRUE_PREDICATE);
        assertFalse(predicate.test(getMethodRef(C.class.getDeclaredMethod("m1"))));
        assertFalse(predicate.test(getMethodRef(C.class.getDeclaredMethod("m2"))));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testApplyNonExistentMethod() throws Exception {
        Predicate<MethodRef> predicate = new AnalyzingMethodRefPredicate(getBytes(getClass()), TRUE_PREDICATE);
        predicate.test(new MethodRef("nonExistentMethod", "()V"));
    }

    private static byte[] getBytes(Class<?> cls) throws IOException {
        try (InputStream in = AnalyzingMethodRefPredicateTest.class.getClassLoader()
                .getResourceAsStream(Type.getInternalName(cls) + ".class")) {
            return IOUtils.toByteArray(in);
        }
    }

    private static MethodRef getMethodRef(Method method) {
        return new MethodRef(method.getName(), Type.getMethodDescriptor(method));
    }
}

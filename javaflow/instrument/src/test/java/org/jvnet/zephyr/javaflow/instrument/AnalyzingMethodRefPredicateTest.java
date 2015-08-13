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

package org.jvnet.zephyr.javaflow.instrument;

import org.jvnet.zephyr.common.util.Predicate;
import org.objectweb.asm.Type;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.lang.reflect.Method;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public final class AnalyzingMethodRefPredicateTest {

    private AnalyzingMethodRefPredicate predicate;

    @BeforeMethod
    public void setUp() throws Exception {
        predicate = new AnalyzingMethodRefPredicate(new Predicate<MethodRef>() {
            @Override
            public boolean test(MethodRef t) {
                return true;
            }
        }, getClass().getClassLoader());
    }

    @Test
    public void testApplyParentPredicate() throws Exception {
        class C {

            void m() {
                m2();
            }

            void m2() {
            }
        }
        Predicate<MethodRef> predicate = new AnalyzingMethodRefPredicate(new Predicate<MethodRef>() {
            @Override
            public boolean test(MethodRef t) {
                return !t.getName().equals("m");
            }
        }, getClass().getClassLoader());
        assertFalse(predicate.test(createMethodRef(C.class.getDeclaredMethod("m"))));
    }

    @Test
    public void testApplyMethodWithoutCalls() throws Exception {
        class C {

            void m() {
            }
        }
        assertFalse(predicate.test(createMethodRef(C.class.getDeclaredMethod("m"))));
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
        assertTrue(predicate.test(createMethodRef(C.class.getDeclaredMethod("m"))));
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
        assertFalse(predicate.test(createMethodRef(C.class.getDeclaredMethod("m"))));
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
        assertFalse(predicate.test(createMethodRef(C.class.getDeclaredMethod("m"))));
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
        assertFalse(predicate.test(createMethodRef(Static.class.getDeclaredMethod("m"))));
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
        assertFalse(predicate.test(createMethodRef(C.class.getDeclaredMethod("m"))));
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
        assertTrue(predicate.test(createMethodRef(C.class.getDeclaredMethod("m1"))));
        assertTrue(predicate.test(createMethodRef(C.class.getDeclaredMethod("m2"))));
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
        assertFalse(predicate.test(createMethodRef(C.class.getDeclaredMethod("m1"))));
        assertFalse(predicate.test(createMethodRef(C.class.getDeclaredMethod("m2"))));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testApplyNonExistentClass() throws Exception {
        predicate.test(new MethodRef(Type.getInternalName(getClass()) + "$NonExistentClass", "<clinit>", "()V"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testApplyNonExistentMethod() throws Exception {
        predicate.test(new MethodRef(Type.getInternalName(getClass()), "nonExistentMethod", "()V"));
    }

    private static MethodRef createMethodRef(Method method) {
        return new MethodRef(Type.getInternalName(method.getDeclaringClass()), method.getName(),
                Type.getMethodDescriptor(method));
    }
}

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
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.MethodNode;

import static java.util.Objects.requireNonNull;
import static org.objectweb.asm.Opcodes.ACC_ABSTRACT;
import static org.objectweb.asm.Opcodes.ACC_ANNOTATION;
import static org.objectweb.asm.Opcodes.ACC_NATIVE;
import static org.objectweb.asm.Opcodes.ASM5;

public final class ContinuationClassAdapter extends ClassVisitor {

    private static final String CONTINUABLE = "org/jvnet/zephyr/javaflow/runtime/Continuable";

    private final Predicate<MethodRef> predicate;
    private String name;

    public ContinuationClassAdapter(ClassVisitor cv, Predicate<MethodRef> predicate) {
        super(ASM5, cv);
        this.predicate = requireNonNull(predicate);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.name = name;

        if ((access & ACC_ANNOTATION) != 0) {
            cv.visit(version, access, name, signature, superName, interfaces);
            return;
        }

        for (String name1 : interfaces) {
            if (name1.equals(CONTINUABLE)) {
                throw new RuntimeException(name + " has already been instrumented");
            }
        }

        int n = interfaces.length;
        String[] interfaces1 = new String[n + 1];
        System.arraycopy(interfaces, 0, interfaces1, 0, n);
        interfaces1[n] = CONTINUABLE;

        cv.visit(version, access, name, signature, superName, interfaces1);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
        if ((access & (ACC_NATIVE | ACC_ABSTRACT)) != 0 || name.charAt(0) == '<' ||
                !predicate.test(new MethodRef(name, desc))) {
            return mv;
        }
        return new MethodAdapter(this.name, access, name, desc, signature, exceptions, mv);
    }

    private static final class MethodAdapter extends MethodNode {

        private static final NewRelocator newRelocator = new NewRelocator();
        private static final ContinuationAdder continuationAdder = new ContinuationAdder();

        private final String owner;
        private final MethodVisitor mv;

        MethodAdapter(String owner, int access, String name, String desc, String signature, String[] exceptions,
                MethodVisitor mv) {
            super(ASM5, access, name, desc, signature, exceptions);
            this.owner = owner;
            this.mv = mv;
        }

        @Override
        public void visitEnd() {
            newRelocator.process(owner, this);
            continuationAdder.process(owner, this);
            accept(mv);
        }
    }
}

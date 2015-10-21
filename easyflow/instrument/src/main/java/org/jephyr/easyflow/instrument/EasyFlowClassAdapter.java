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

package org.jephyr.easyflow.instrument;

import org.jephyr.common.util.function.Predicate;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import static java.util.Objects.requireNonNull;
import static org.objectweb.asm.Opcodes.ACC_ABSTRACT;
import static org.objectweb.asm.Opcodes.ACC_NATIVE;
import static org.objectweb.asm.Opcodes.ACC_SYNCHRONIZED;
import static org.objectweb.asm.Opcodes.ASM5;
import static org.objectweb.asm.Opcodes.V1_6;

public final class EasyFlowClassAdapter extends ClassVisitor {

    private final Predicate<MethodRef> methodRefPredicate;
    private String name;
    private boolean instrument;

    public EasyFlowClassAdapter(Predicate<MethodRef> methodRefPredicate, ClassVisitor cv) {
        super(ASM5, cv);
        this.methodRefPredicate = requireNonNull(methodRefPredicate);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.name = name;
        instrument = (version & 0xFF) >= V1_6;
        super.visit(version, access, name, signature, superName, interfaces);
        super.visitAnnotation("Lorg/jephyr/easyflow/instrument/Instrumented;", false);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        if (desc.equals("Lorg/jephyr/easyflow/instrument/Instrumented;")) {
            instrument = false;
            return null;
        }
        return super.visitAnnotation(desc, visible);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
        if (instrument && (access & (ACC_SYNCHRONIZED | ACC_NATIVE | ACC_ABSTRACT)) == 0 && name.charAt(0) != '<' &&
                methodRefPredicate.test(new MethodRef(name, desc))) {
            return NewRelocatorMethodAdapter.create(this.name, access, name, desc, signature, exceptions,
                    ContinuationMethodAdapter.create(this.name, access, name, desc, signature, exceptions, mv));
        }
        return mv;
    }
}

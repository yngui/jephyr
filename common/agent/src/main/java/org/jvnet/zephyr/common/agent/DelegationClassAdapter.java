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

package org.jvnet.zephyr.common.agent;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.TypePath;

public class DelegationClassAdapter extends ClassVisitor {

    public DelegationClassAdapter(int api) {
        super(api);
    }

    public DelegationClassAdapter(int api, ClassVisitor cv) {
        super(api, cv);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        ClassVisitor cv1 = delegate();
        if (cv1 != null) {
            cv1.visit(version, access, name, signature, superName, interfaces);
        }
    }

    @Override
    public void visitSource(String source, String debug) {
        ClassVisitor cv1 = delegate();
        if (cv1 != null) {
            cv1.visitSource(source, debug);
        }
    }

    @Override
    public void visitOuterClass(String owner, String name, String desc) {
        ClassVisitor cv1 = delegate();
        if (cv1 != null) {
            cv1.visitOuterClass(owner, name, desc);
        }
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        ClassVisitor cv1 = delegate();
        if (cv1 != null) {
            return cv1.visitAnnotation(desc, visible);
        } else {
            return null;
        }
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
        ClassVisitor cv1 = delegate();
        if (cv1 != null) {
            return cv1.visitTypeAnnotation(typeRef, typePath, desc, visible);
        } else {
            return null;
        }
    }

    @Override
    public void visitAttribute(Attribute attr) {
        ClassVisitor cv1 = delegate();
        if (cv1 != null) {
            cv1.visitAttribute(attr);
        }
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        ClassVisitor cv1 = delegate();
        if (cv1 != null) {
            cv1.visitInnerClass(name, outerName, innerName, access);
        }
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        ClassVisitor cv1 = delegate();
        if (cv1 != null) {
            return cv1.visitField(access, name, desc, signature, value);
        } else {
            return null;
        }
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        ClassVisitor cv1 = delegate();
        if (cv1 != null) {
            return cv1.visitMethod(access, name, desc, signature, exceptions);
        } else {
            return null;
        }
    }

    @Override
    public void visitEnd() {
        ClassVisitor cv1 = delegate();
        if (cv1 != null) {
            cv1.visitEnd();
        }
    }

    protected ClassVisitor delegate() {
        return cv;
    }
}

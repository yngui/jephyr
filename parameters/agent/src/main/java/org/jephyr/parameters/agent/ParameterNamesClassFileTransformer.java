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

package org.jephyr.parameters.agent;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

import org.jephyr.parameters.ParameterNamesCache;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ASM5;

final class ParameterNamesClassFileTransformer implements ClassFileTransformer {

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        try {
            ClassReader classReader = new ClassReader(classfileBuffer);
            classReader.accept(new ClassAdapter(loader), 0);
        } catch (Throwable e) {
            System.err.println("Failed to process class " + className);
            e.printStackTrace(System.err);
        }
        return null;
    }

    private static final class ClassAdapter extends ClassVisitor {

        private final ClassLoader loader;
        private String name;

        ClassAdapter(ClassLoader loader) {
            super(ASM5);
            this.loader = loader;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {
            this.name = name;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            return new MethodAdapter(loader, this.name, access, name, desc);
        }
    }

    private static final class MethodAdapter extends MethodVisitor {

        private static final String[] EMPTY_NAMES = new String[0];

        private final ClassLoader loader;
        private final String owner;
        private final int access;
        private final String name;
        private final String desc;
        private final Type[] types;
        private int[] indices;
        private String[] names;

        MethodAdapter(ClassLoader loader, String owner, int access, String name, String desc) {
            super(ASM5);
            this.loader = loader;
            this.owner = owner;
            this.access = access;
            this.name = name;
            this.desc = desc;
            types = Type.getArgumentTypes(desc);
            if (types.length == 0) {
                names = EMPTY_NAMES;
            }
        }

        @Override
        public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
            int n = types.length;
            if (n == 0) {
                return;
            }
            if (indices == null) {
                indices = new int[n];
                int index1 = (access & ACC_STATIC) == 0 ? 1 : 0;
                for (int i = 0; i < n; i++) {
                    indices[i] = index1;
                    index1 += types[i].getSize();
                }
                names = new String[n];
            }
            for (int i = 0; i < n; i++) {
                if (indices[i] == index) {
                    names[i] = name;
                    return;
                }
            }
        }

        @Override
        public void visitEnd() {
            if (names != null) {
                ParameterNamesCache.addParameterNames(loader, owner, name, desc, names);
            }
        }
    }
}

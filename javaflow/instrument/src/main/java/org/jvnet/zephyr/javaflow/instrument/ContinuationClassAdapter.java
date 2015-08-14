/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package org.jvnet.zephyr.javaflow.instrument;

import org.jvnet.zephyr.common.util.Predicate;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import static java.util.Objects.requireNonNull;
import static org.objectweb.asm.Opcodes.ACC_ABSTRACT;
import static org.objectweb.asm.Opcodes.ACC_ANNOTATION;
import static org.objectweb.asm.Opcodes.ACC_NATIVE;
import static org.objectweb.asm.Opcodes.ASM5;

public final class ContinuationClassAdapter extends ClassVisitor {

    private static final String CONTINUABLE = "org/jvnet/zephyr/javaflow/runtime/Continuable";

    private final Predicate<MethodRef> predicate;
    private String className;

    public ContinuationClassAdapter(ClassVisitor cv, Predicate<MethodRef> predicate) {
        super(ASM5, cv);
        this.predicate = requireNonNull(predicate);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        className = name;

        if ((access & ACC_ANNOTATION) != 0) {
            cv.visit(version, access, name, signature, superName, interfaces);
            return;
        }

        String[] newInterfaces = new String[interfaces.length + 1];
        for (int i = interfaces.length - 1; i >= 0; i--) {
            if (interfaces[i].equals(CONTINUABLE)) {
                throw new RuntimeException(className + " has already been instrumented");
            }
            newInterfaces[i] = interfaces[i];
        }

        newInterfaces[newInterfaces.length - 1] = CONTINUABLE;
        cv.visit(version, access, name, signature, superName, newInterfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
        if ((access & (ACC_NATIVE | ACC_ABSTRACT)) != 0 || name.charAt(0) == '<' ||
                !predicate.test(new MethodRef(name, desc))) {
            return mv;
        }
        return new NewRelocator(access, name, desc, signature, exceptions, className,
                new ContinuationMethodAdapter(className, access, name, desc, signature, exceptions, mv));
    }
}

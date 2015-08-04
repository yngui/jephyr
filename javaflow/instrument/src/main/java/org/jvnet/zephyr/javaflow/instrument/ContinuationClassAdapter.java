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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jvnet.zephyr.javaflow.runtime.Continuable;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import static org.objectweb.asm.Opcodes.ACC_ANNOTATION;
import static org.objectweb.asm.Opcodes.ASM5;

public final class ContinuationClassAdapter extends ClassVisitor {

    private static final Log log = LogFactory.getLog(ContinuationClassAdapter.class);

    private final Predicate<MethodRef> predicate;
    private String className;

    public ContinuationClassAdapter(ClassVisitor cv, Predicate<MethodRef> predicate) {
        super(ASM5, cv);
        this.predicate = predicate;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        className = name;

        if ((access & ACC_ANNOTATION) != 0) {
            cv.visit(version, access, name, signature, superName, interfaces);
            return;
        }

        // Check that it doesn't implement Continuable (already been instrumented)
        String[] newInterfaces = new String[interfaces.length + 1];
        for (int i = interfaces.length - 1; i >= 0; i--) {
            if (interfaces[i].equals(Type.getInternalName(Continuable.class))) {
                throw new RuntimeException(className + " has already been instrumented");
            }

            newInterfaces[i] = interfaces[i];
        }

        // Add the Continuable interface so that the class is marked and wont be instrumented again by mistake
        newInterfaces[newInterfaces.length - 1] = Type.getInternalName(Continuable.class);

        cv.visit(version, access, name, signature, superName, newInterfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
        // TODO skip native and abstract methods?
        if (name.charAt(0) == '<' || !predicate.apply(new MethodRef(className, name, desc))) {
            return mv;
        }
        if (log.isDebugEnabled()) {
            log.debug("Instrumenting " + className + '.' + name + desc);
        }
        return new ContinuationMethodAnalyzer(className, mv, access, name, desc, signature, exceptions);
    }
}

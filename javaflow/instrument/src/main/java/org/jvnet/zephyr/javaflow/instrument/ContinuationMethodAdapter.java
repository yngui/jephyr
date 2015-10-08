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

import org.jvnet.zephyr.javaflow.runtime.StackRecorder;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

import java.util.List;

import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ASM5;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.BIPUSH;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.DCONST_0;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.DUP2_X2;
import static org.objectweb.asm.Opcodes.FCONST_0;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.IFEQ;
import static org.objectweb.asm.Opcodes.IFNULL;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.ISTORE;
import static org.objectweb.asm.Opcodes.LCONST_0;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.POP2;
import static org.objectweb.asm.Opcodes.SIPUSH;
import static org.objectweb.asm.Opcodes.SWAP;

final class ContinuationMethodAdapter extends MethodVisitor {

    private static final String STACK_RECORDER = Type.getInternalName(StackRecorder.class);
    private static final String POP_METHOD = "pop";
    private static final String PUSH_METHOD = "push";

    private final ContinuationMethodAnalyzer canalyzer;
    private final Analyzer<BasicValue> analyzer;
    private final Label startLabel = new Label();
    private final List<Label> labels;
    private final List<MethodInsnNode> nodes;
    private final int stackRecorderVar;
    private final boolean instance;
    private final String methodDesc;

    private int currentIndex;
    private Frame<BasicValue> currentFrame;

    ContinuationMethodAdapter(ContinuationMethodAnalyzer a) {
        super(ASM5, a.mv);
        canalyzer = a;
        analyzer = a.analyzer;
        labels = a.labels;
        nodes = a.nodes;
        stackRecorderVar = a.stackRecorderVar;
        instance = (a.access & ACC_STATIC) == 0;
        methodDesc = a.desc;
    }

    @Override
    public void visitCode() {
        mv.visitCode();

        int fsize = labels.size();
        Label[] restoreLabels = new Label[fsize];
        for (int i = restoreLabels.length - 1; i >= 0; i--) {
            restoreLabels[i] = new Label();
        }

        // verify if restoring
        Label l0 = new Label();

        // PC: StackRecorder stackRecorder = StackRecorder.get();
        mv.visitMethodInsn(INVOKESTATIC, STACK_RECORDER, "get", "()L" + STACK_RECORDER + ';', false);
        mv.visitInsn(DUP);
        mv.visitVarInsn(ASTORE, stackRecorderVar);
        mv.visitLabel(startLabel);

        // PC: if (stackRecorder != null && !stackRecorder.isRestoring) {  
        mv.visitJumpInsn(IFNULL, l0);
        mv.visitVarInsn(ALOAD, stackRecorderVar);
        mv.visitFieldInsn(GETFIELD, STACK_RECORDER, "isRestoring", "Z");
        mv.visitJumpInsn(IFEQ, l0);

        mv.visitVarInsn(ALOAD, stackRecorderVar);
        // PC:    stackRecorder.popInt();
        mv.visitMethodInsn(INVOKEVIRTUAL, STACK_RECORDER, POP_METHOD + "Int", "()I", false);
        mv.visitTableSwitchInsn(0, fsize - 1, l0, restoreLabels);

        // switch cases
        for (int i = 0; i < fsize; i++) {
            Label frameLabel = labels.get(i);
            mv.visitLabel(restoreLabels[i]);

            MethodInsnNode mnode = nodes.get(i);
            Frame<BasicValue> frame = analyzer.getFrames()[canalyzer.getIndex(mnode)];

            // for each local variable store the value in locals popping it from the stack!
            // locals
            int lsize = frame.getLocals();
            for (int j = lsize - 1; j >= 0; j--) {
                BasicValue value = frame.getLocal(j);
                if (isNull(value)) {
                    mv.visitInsn(ACONST_NULL);
                    mv.visitVarInsn(ASTORE, j);
                } else if (value == BasicValue.UNINITIALIZED_VALUE) {
                    // TODO ??
                } else if (value == BasicValue.RETURNADDRESS_VALUE) {
                    // TODO ??
                } else {
                    mv.visitVarInsn(ALOAD, stackRecorderVar);
                    Type type = value.getType();
                    if (value.isReference()) {
                        mv.visitMethodInsn(INVOKEVIRTUAL, STACK_RECORDER, POP_METHOD + "Object", "()Ljava/lang/Object;",
                                false);
                        Type t = value.getType();
                        String desc = t.getDescriptor();
                        if (desc.charAt(0) == '[') {
                            mv.visitTypeInsn(CHECKCAST, desc);
                        } else {
                            mv.visitTypeInsn(CHECKCAST, t.getInternalName());
                        }
                        mv.visitVarInsn(ASTORE, j);
                    } else {
                        mv.visitMethodInsn(INVOKEVIRTUAL, STACK_RECORDER, getPopMethod(type),
                                "()" + type.getDescriptor(), false);
                        mv.visitVarInsn(type.getOpcode(ISTORE), j);
                    }
                }
            }

            // stack
            int argSize = Type.getArgumentTypes(mnode.desc).length;
            int ownerSize = mnode.getOpcode() == INVOKESTATIC ? 0 : 1;  // TODO
            int initSize = mnode.name.charAt(0) == '<' ? 2 : 0;
            int ssize = frame.getStackSize();
            for (int j = 0; j < ssize - argSize - ownerSize - initSize; j++) {
                BasicValue value = frame.getStack(j);
                if (isNull(value)) {
                    mv.visitInsn(ACONST_NULL);
                } else if (value == BasicValue.UNINITIALIZED_VALUE) {
                    // TODO ??
                } else if (value == BasicValue.RETURNADDRESS_VALUE) {
                    // TODO ??
                } else if (value.isReference()) {
                    mv.visitVarInsn(ALOAD, stackRecorderVar);
                    mv.visitMethodInsn(INVOKEVIRTUAL, STACK_RECORDER, POP_METHOD + "Object", "()Ljava/lang/Object;",
                            false);
                    mv.visitTypeInsn(CHECKCAST, value.getType().getInternalName());
                } else {
                    Type type = value.getType();
                    mv.visitVarInsn(ALOAD, stackRecorderVar);
                    mv.visitMethodInsn(INVOKEVIRTUAL, STACK_RECORDER, getPopMethod(type), "()" + type.getDescriptor(),
                            false);
                }
            }

            if (mnode.getOpcode() != INVOKESTATIC) {
                // Load the object whose method we are calling  
                BasicValue value = frame.getStack(ssize - argSize - 1);
                if (isNull(value)) {
                    // If user code causes NPE, then we keep this behavior: load null to get NPE at runtime
                    mv.visitInsn(ACONST_NULL);
                } else {
                    mv.visitVarInsn(ALOAD, stackRecorderVar);
                    mv.visitMethodInsn(INVOKEVIRTUAL, STACK_RECORDER, POP_METHOD + "Reference", "()Ljava/lang/Object;",
                            false);
                    mv.visitTypeInsn(CHECKCAST, value.getType().getInternalName());
                }
            }

            // Create null types for the parameters of the method invocation
            for (Type paramType : Type.getArgumentTypes(mnode.desc)) {
                pushDefault(paramType);
            }

            // continue to the next method
            mv.visitJumpInsn(GOTO, frameLabel);
        }

        // PC: }
        // end of start block
        mv.visitLabel(l0);
    }

    @Override
    public void visitLabel(Label label) {
        if (currentIndex < labels.size() && label == labels.get(currentIndex)) {
            int i = canalyzer.getIndex(nodes.get(currentIndex));
            currentFrame = analyzer.getFrames()[i];
        }
        mv.visitLabel(label);
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
        mv.visitMethodInsn(opcode, owner, name, desc, itf);

        if (currentFrame != null) {
            Label fl = new Label();

            mv.visitVarInsn(ALOAD, stackRecorderVar);
            mv.visitJumpInsn(IFNULL, fl);
            mv.visitVarInsn(ALOAD, stackRecorderVar);
            mv.visitFieldInsn(GETFIELD, STACK_RECORDER, "isCapturing", "Z");
            mv.visitJumpInsn(IFEQ, fl);

            // save stack
            Type returnType = Type.getReturnType(desc);
            boolean hasReturn = returnType != Type.VOID_TYPE;
            if (hasReturn) {
                mv.visitInsn(returnType.getSize() == 1 ? POP : POP2);
            }

            Type[] params = Type.getArgumentTypes(desc);
            int argSize = params.length;
            int ownerSize = opcode == INVOKESTATIC ? 0 : 1;  // TODO
            int ssize = currentFrame.getStackSize() - argSize - ownerSize;
            for (int i = ssize - 1; i >= 0; i--) {
                BasicValue value = currentFrame.getStack(i);
                if (isNull(value)) {
                    mv.visitInsn(POP);
                } else if (value == BasicValue.UNINITIALIZED_VALUE) {
                    // TODO ??
                } else if (value.isReference()) {
                    mv.visitVarInsn(ALOAD, stackRecorderVar);
                    mv.visitInsn(SWAP);
                    mv.visitMethodInsn(INVOKEVIRTUAL, STACK_RECORDER, PUSH_METHOD + "Object", "(Ljava/lang/Object;)V",
                            false);
                } else {
                    Type type = value.getType();
                    if (type.getSize() > 1) {
                        mv.visitInsn(ACONST_NULL); // dummy stack entry
                        mv.visitVarInsn(ALOAD, stackRecorderVar);
                        mv.visitInsn(DUP2_X2);  // swap2 for long/double
                        mv.visitInsn(POP2);
                        mv.visitMethodInsn(INVOKEVIRTUAL, STACK_RECORDER, getPushMethod(type),
                                '(' + type.getDescriptor() + ")V", false);
                        mv.visitInsn(POP);  // remove dummy stack entry
                    } else {
                        mv.visitVarInsn(ALOAD, stackRecorderVar);
                        mv.visitInsn(SWAP);
                        mv.visitMethodInsn(INVOKEVIRTUAL, STACK_RECORDER, getPushMethod(type),
                                '(' + type.getDescriptor() + ")V", false);
                    }
                }
            }

            if (instance) {
                mv.visitVarInsn(ALOAD, stackRecorderVar);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitMethodInsn(INVOKEVIRTUAL, STACK_RECORDER, PUSH_METHOD + "Reference", "(Ljava/lang/Object;)V",
                        false);
            }

            // save locals
            int fsize = currentFrame.getLocals();
            for (int j = 0; j < fsize; j++) {
                BasicValue value = currentFrame.getLocal(j);
                if (isNull(value)) {
                    // no need to save null
                } else if (value == BasicValue.UNINITIALIZED_VALUE) {
                    // no need to save uninitialized objects
                } else if (value.isReference()) {
                    mv.visitVarInsn(ALOAD, stackRecorderVar);
                    mv.visitVarInsn(ALOAD, j);
                    mv.visitMethodInsn(INVOKEVIRTUAL, STACK_RECORDER, PUSH_METHOD + "Object", "(Ljava/lang/Object;)V",
                            false);
                } else {
                    mv.visitVarInsn(ALOAD, stackRecorderVar);
                    Type type = value.getType();
                    mv.visitVarInsn(type.getOpcode(ILOAD), j);
                    mv.visitMethodInsn(INVOKEVIRTUAL, STACK_RECORDER, getPushMethod(type),
                            '(' + type.getDescriptor() + ")V", false);
                }
            }

            mv.visitVarInsn(ALOAD, stackRecorderVar);
            if (currentIndex >= 128) {
                // if > 127 then it's a SIPUSH, not a BIPUSH...
                mv.visitIntInsn(SIPUSH, currentIndex);
            } else {
                // TODO optimize to iconst_0...
                mv.visitIntInsn(BIPUSH, currentIndex);
            }
            mv.visitMethodInsn(INVOKEVIRTUAL, STACK_RECORDER, "pushInt", "(I)V", false);

            Type methodReturnType = Type.getReturnType(methodDesc);
            pushDefault(methodReturnType);
            mv.visitInsn(methodReturnType.getOpcode(IRETURN));
            mv.visitLabel(fl);

            currentIndex++;
            currentFrame = null;
        }
    }

    @Override
    public void visitMaxs(int maxStack, int maxLocals) {
        Label endLabel = new Label();
        mv.visitLabel(endLabel);

        mv.visitLocalVariable("__stackRecorder", 'L' + STACK_RECORDER + ';', null, startLabel, endLabel,
                stackRecorderVar);

        mv.visitMaxs(0, 0);
    }

    private static boolean isNull(BasicValue value) {
        if (value == null) {
            return true;
        }
        if (!value.isReference()) {
            return false;
        }
        Type type = value.getType();
        return type.getDescriptor().equals("Lnull;");
    }

    private void pushDefault(Type type) {
        switch (type.getSort()) {
            case Type.VOID:
                break;
            case Type.DOUBLE:
                mv.visitInsn(DCONST_0);
                break;
            case Type.LONG:
                mv.visitInsn(LCONST_0);
                break;
            case Type.FLOAT:
                mv.visitInsn(FCONST_0);
                break;
            case Type.OBJECT:
            case Type.ARRAY:
                mv.visitInsn(ACONST_NULL);
                break;
            default:
                mv.visitInsn(ICONST_0);
                break;
        }
    }

    private static String getPopMethod(Type type) {
        return POP_METHOD + getSuffix(type.getSort());
    }

    private static String getPushMethod(Type type) {
        return PUSH_METHOD + getSuffix(type.getSort());
    }

    private static String getSuffix(int sort) {
        switch (sort) {
            case Type.VOID:
            case Type.ARRAY:
            case Type.OBJECT:
                return "Object";
            case Type.BOOLEAN:
            case Type.CHAR:
            case Type.BYTE:
            case Type.SHORT:
            case Type.INT:
                return "Int";
            case Type.FLOAT:
                return "Float";
            case Type.LONG:
                return "Long";
            case Type.DOUBLE:
                return "Double";
            default:
                throw new IllegalArgumentException();
        }
    }
}

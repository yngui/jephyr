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
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SimpleVerifier;

import java.util.ArrayList;
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
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.ISTORE;
import static org.objectweb.asm.Opcodes.LCONST_0;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.POP2;
import static org.objectweb.asm.Opcodes.SIPUSH;
import static org.objectweb.asm.Opcodes.SWAP;

final class ContinuationMethodAdapter extends MethodNode {

    private final List<Label> labels = new ArrayList<>();
    private final List<MethodInsnNode> nodes = new ArrayList<>();
    private final String className;
    private final MethodVisitor mv;
    private Analyzer<BasicValue> analyzer;

    ContinuationMethodAdapter(int access, String name, String desc, String signature, String[] exceptions,
            String className, MethodVisitor mv) {
        super(ASM5, access, name, desc, signature, exceptions);
        this.className = className;
        this.mv = mv;
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
        MethodInsnNode node = new MethodInsnNode(opcode, owner, name, desc, itf);
        if (opcode == INVOKEINTERFACE || opcode == INVOKESPECIAL && !name.equals("<init>") || opcode == INVOKESTATIC ||
                opcode == INVOKEVIRTUAL) {
            Label label = new Label();
            visitLabel(label);
            labels.add(label);
            nodes.add(node);
        }
        instructions.add(node);
    }

    @Override
    protected LabelNode getLabelNode(Label l) {
        Object info = l.info;
        if (info instanceof LabelNode) {
            return (LabelNode) info;
        } else {
            LabelNode labelNode = new LabelNode(l);
            l.info = labelNode;
            return labelNode;
        }
    }

    @Override
    public void visitEnd() {
        if (labels.isEmpty()) {
            accept(mv);
            return;
        }

        analyzer = new Analyzer<>(new SimpleVerifier() {
            @Override
            protected Class<?> getClass(Type t) {
                try {
                    if (t.getSort() == Type.ARRAY) {
                        return Class.forName(t.getDescriptor().replace('/', '.'), true,
                                Thread.currentThread().getContextClassLoader());
                    }
                    return Class.forName(t.getClassName(), true, Thread.currentThread().getContextClassLoader());
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        try {
            analyzer.analyze(className, this);
        } catch (AnalyzerException e) {
            throw new RuntimeException(e);
        }

        accept(new MethodAdapter(access, desc, instructions, labels, nodes, analyzer.getFrames(), maxLocals, mv));
    }

    private static final class MethodAdapter extends MethodVisitor {

        private static final String STACK_RECORDER = Type.getInternalName(StackRecorder.class);
        private static final String POP_METHOD = "pop";
        private static final String PUSH_METHOD = "push";

        private final Label startLabel = new Label();
        private final int access;
        private final String desc;
        private final InsnList instructions;
        private final List<Label> labels;
        private final List<MethodInsnNode> nodes;
        private final Frame<BasicValue>[] frames;
        private final int maxLocals;
        private int currentIndex;
        private Frame<BasicValue> currentFrame;

        MethodAdapter(int access, String desc, InsnList instructions, List<Label> labels, List<MethodInsnNode> nodes,
                Frame<BasicValue>[] frames, int maxLocals, MethodVisitor mv) {
            super(ASM5, mv);
            this.access = access;
            this.desc = desc;
            this.instructions = instructions;
            this.labels = labels;
            this.nodes = nodes;
            this.frames = frames;
            this.maxLocals = maxLocals;
        }

        @Override
        public void visitCode() {
            mv.visitCode();

            int n = labels.size();
            Label[] restoreLabels = new Label[n];
            for (int i = restoreLabels.length - 1; i >= 0; i--) {
                restoreLabels[i] = new Label();
            }

            // verify if restoring
            Label label = new Label();

            // PC: StackRecorder stackRecorder = StackRecorder.get();
            mv.visitMethodInsn(INVOKESTATIC, STACK_RECORDER, "get", "()L" + STACK_RECORDER + ';', false);
            mv.visitInsn(DUP);
            mv.visitVarInsn(ASTORE, maxLocals);
            mv.visitLabel(startLabel);

            // PC: if (stackRecorder != null && !stackRecorder.isRestoring) {
            mv.visitJumpInsn(IFNULL, label);
            mv.visitVarInsn(ALOAD, maxLocals);
            mv.visitFieldInsn(GETFIELD, STACK_RECORDER, "isRestoring", "Z");
            mv.visitJumpInsn(IFEQ, label);

            mv.visitVarInsn(ALOAD, maxLocals);
            // PC: stackRecorder.popInt();
            mv.visitMethodInsn(INVOKEVIRTUAL, STACK_RECORDER, POP_METHOD + "Int", "()I", false);
            mv.visitTableSwitchInsn(0, n - 1, label, restoreLabels);

            // switch cases
            for (int i = 0; i < n; i++) {
                Label frameLabel = labels.get(i);
                mv.visitLabel(restoreLabels[i]);

                MethodInsnNode node = nodes.get(i);
                Frame<BasicValue> frame = frames[instructions.indexOf(node)];

                // for each local variable store the value in locals popping it from the stack!
                // locals
                int n1 = frame.getLocals();
                for (int j = n1 - 1; j >= 0; j--) {
                    BasicValue value = frame.getLocal(j);
                    if (isNull(value)) {
                        mv.visitInsn(ACONST_NULL);
                        mv.visitVarInsn(ASTORE, j);
                    } else if (value == BasicValue.UNINITIALIZED_VALUE) {
                        // TODO ??
                    } else if (value == BasicValue.RETURNADDRESS_VALUE) {
                        // TODO ??
                    } else {
                        mv.visitVarInsn(ALOAD, maxLocals);
                        Type type = value.getType();
                        if (value.isReference()) {
                            mv.visitMethodInsn(INVOKEVIRTUAL, STACK_RECORDER, POP_METHOD + "Object",
                                    "()Ljava/lang/Object;", false);
                            Type type1 = value.getType();
                            String desc = type1.getDescriptor();
                            if (desc.charAt(0) == '[') {
                                mv.visitTypeInsn(CHECKCAST, desc);
                            } else {
                                mv.visitTypeInsn(CHECKCAST, type1.getInternalName());
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
                int argSize = Type.getArgumentTypes(node.desc).length;
                int ownerSize = node.getOpcode() == INVOKESTATIC ? 0 : 1; // TODO
                int initSize = node.name.charAt(0) == '<' ? 2 : 0;
                int stackSize = frame.getStackSize();
                for (int j = 0; j < stackSize - argSize - ownerSize - initSize; j++) {
                    BasicValue value = frame.getStack(j);
                    if (isNull(value)) {
                        mv.visitInsn(ACONST_NULL);
                    } else if (value == BasicValue.UNINITIALIZED_VALUE) {
                        // TODO ??
                    } else if (value == BasicValue.RETURNADDRESS_VALUE) {
                        // TODO ??
                    } else if (value.isReference()) {
                        mv.visitVarInsn(ALOAD, maxLocals);
                        mv.visitMethodInsn(INVOKEVIRTUAL, STACK_RECORDER, POP_METHOD + "Object", "()Ljava/lang/Object;",
                                false);
                        mv.visitTypeInsn(CHECKCAST, value.getType().getInternalName());
                    } else {
                        Type type = value.getType();
                        mv.visitVarInsn(ALOAD, maxLocals);
                        mv.visitMethodInsn(INVOKEVIRTUAL, STACK_RECORDER, getPopMethod(type),
                                "()" + type.getDescriptor(), false);
                    }
                }

                if (node.getOpcode() != INVOKESTATIC) {
                    // Load the object whose method we are calling
                    BasicValue value = frame.getStack(stackSize - argSize - 1);
                    if (isNull(value)) {
                        // If user code causes NPE, then we keep this behavior: load null to get NPE at runtime
                        mv.visitInsn(ACONST_NULL);
                    } else {
                        mv.visitVarInsn(ALOAD, maxLocals);
                        mv.visitMethodInsn(INVOKEVIRTUAL, STACK_RECORDER, POP_METHOD + "Reference",
                                "()Ljava/lang/Object;", false);
                        mv.visitTypeInsn(CHECKCAST, value.getType().getInternalName());
                    }
                }

                // Create null types for the parameters of the method invocation
                for (Type paramType : Type.getArgumentTypes(node.desc)) {
                    pushDefault(paramType);
                }

                // continue to the next method
                mv.visitJumpInsn(GOTO, frameLabel);
            }

            // PC: }
            // end of start block
            mv.visitLabel(label);
        }

        @Override
        public void visitLabel(Label label) {
            if (currentIndex < labels.size() && label == labels.get(currentIndex)) {
                int index = instructions.indexOf(nodes.get(currentIndex));
                currentFrame = frames[index];
            }
            mv.visitLabel(label);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            mv.visitMethodInsn(opcode, owner, name, desc, itf);

            if (currentFrame != null) {
                Label label = new Label();

                mv.visitVarInsn(ALOAD, maxLocals);
                mv.visitJumpInsn(IFNULL, label);
                mv.visitVarInsn(ALOAD, maxLocals);
                mv.visitFieldInsn(GETFIELD, STACK_RECORDER, "isCapturing", "Z");
                mv.visitJumpInsn(IFEQ, label);

                // save stack
                Type returnType = Type.getReturnType(desc);
                boolean hasReturn = returnType != Type.VOID_TYPE;
                if (hasReturn) {
                    mv.visitInsn(returnType.getSize() == 1 ? POP : POP2);
                }

                Type[] types = Type.getArgumentTypes(desc);
                int argSize = types.length;
                int ownerSize = opcode == INVOKESTATIC ? 0 : 1; // TODO
                int stackSize = currentFrame.getStackSize() - argSize - ownerSize;
                for (int i = stackSize - 1; i >= 0; i--) {
                    BasicValue value = currentFrame.getStack(i);
                    if (isNull(value)) {
                        mv.visitInsn(POP);
                    } else if (value == BasicValue.UNINITIALIZED_VALUE) {
                        // TODO ??
                    } else if (value.isReference()) {
                        mv.visitVarInsn(ALOAD, maxLocals);
                        mv.visitInsn(SWAP);
                        mv.visitMethodInsn(INVOKEVIRTUAL, STACK_RECORDER, PUSH_METHOD + "Object",
                                "(Ljava/lang/Object;)V", false);
                    } else {
                        Type type = value.getType();
                        if (type.getSize() > 1) {
                            mv.visitInsn(ACONST_NULL); // dummy stack entry
                            mv.visitVarInsn(ALOAD, maxLocals);
                            mv.visitInsn(DUP2_X2); // swap2 for long/double
                            mv.visitInsn(POP2);
                            mv.visitMethodInsn(INVOKEVIRTUAL, STACK_RECORDER, getPushMethod(type),
                                    '(' + type.getDescriptor() + ")V", false);
                            mv.visitInsn(POP); // remove dummy stack entry
                        } else {
                            mv.visitVarInsn(ALOAD, maxLocals);
                            mv.visitInsn(SWAP);
                            mv.visitMethodInsn(INVOKEVIRTUAL, STACK_RECORDER, getPushMethod(type),
                                    '(' + type.getDescriptor() + ")V", false);
                        }
                    }
                }

                if ((access & ACC_STATIC) == 0) {
                    mv.visitVarInsn(ALOAD, maxLocals);
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitMethodInsn(INVOKEVIRTUAL, STACK_RECORDER, PUSH_METHOD + "Reference",
                            "(Ljava/lang/Object;)V", false);
                }

                // save locals
                int n = currentFrame.getLocals();
                for (int j = 0; j < n; j++) {
                    BasicValue value = currentFrame.getLocal(j);
                    if (isNull(value)) {
                        // no need to save null
                    } else if (value == BasicValue.UNINITIALIZED_VALUE) {
                        // no need to save uninitialized objects
                    } else if (value.isReference()) {
                        mv.visitVarInsn(ALOAD, maxLocals);
                        mv.visitVarInsn(ALOAD, j);
                        mv.visitMethodInsn(INVOKEVIRTUAL, STACK_RECORDER, PUSH_METHOD + "Object",
                                "(Ljava/lang/Object;)V", false);
                    } else {
                        mv.visitVarInsn(ALOAD, maxLocals);
                        Type type = value.getType();
                        mv.visitVarInsn(type.getOpcode(ILOAD), j);
                        mv.visitMethodInsn(INVOKEVIRTUAL, STACK_RECORDER, getPushMethod(type),
                                '(' + type.getDescriptor() + ")V", false);
                    }
                }

                mv.visitVarInsn(ALOAD, maxLocals);
                if (currentIndex >= 128) {
                    // if > 127 then it's a SIPUSH, not a BIPUSH...
                    mv.visitIntInsn(SIPUSH, currentIndex);
                } else {
                    // TODO optimize to iconst_0...
                    mv.visitIntInsn(BIPUSH, currentIndex);
                }
                mv.visitMethodInsn(INVOKEVIRTUAL, STACK_RECORDER, "pushInt", "(I)V", false);

                Type methodReturnType = Type.getReturnType(this.desc);
                pushDefault(methodReturnType);
                mv.visitInsn(methodReturnType.getOpcode(IRETURN));
                mv.visitLabel(label);

                currentIndex++;
                currentFrame = null;
            }
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            Label endLabel = new Label();
            mv.visitLabel(endLabel);
            mv.visitLocalVariable("__stackRecorder", 'L' + STACK_RECORDER + ';', null, startLabel, endLabel,
                    this.maxLocals);
            mv.visitMaxs(0, 0);
        }

        private static boolean isNull(BasicValue value) {
            if (value == null) {
                return true;
            }
            if (!value.isReference()) {
                return false;
            }
            return value.getType().getDescriptor().equals("Lnull;");
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
}

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

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AnalyzerAdapter;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Collection;
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
import static org.objectweb.asm.Opcodes.NULL;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.POP2;
import static org.objectweb.asm.Opcodes.SIPUSH;
import static org.objectweb.asm.Opcodes.SWAP;
import static org.objectweb.asm.Opcodes.TOP;
import static org.objectweb.asm.Opcodes.UNINITIALIZED_THIS;

final class ContinuationMethodAdapter extends AnalyzerAdapter {

    ContinuationMethodAdapter(String className, int access, String name, String desc, String signature,
            String[] exceptions, MethodVisitor mv) {
        super(ASM5, className, access, name, desc, null);
        this.mv = new MyMethodNode(access, name, desc, signature, exceptions, mv);
    }

    private final class MyMethodNode extends MethodNode {

        private final List<Label> labels = new ArrayList<>();
        private final List<MethodInsnNode> nodes = new ArrayList<>();
        private final List<Frame> frames = new ArrayList<>();
        private final MethodVisitor mv;

        MyMethodNode(int access, String name, String desc, String signature, String[] exceptions, MethodVisitor mv) {
            super(ASM5, access, name, desc, signature, exceptions);
            this.mv = mv;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            MethodInsnNode node = new MethodInsnNode(opcode, owner, name, desc, itf);
            if (opcode == INVOKEINTERFACE || opcode == INVOKESPECIAL && !name.equals("<init>") ||
                    opcode == INVOKESTATIC ||
                    opcode == INVOKEVIRTUAL) {
                Label label = new Label();
                visitLabel(label);
                labels.add(label);
                nodes.add(node);
                frames.add(new Frame(locals, stack));
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

            accept(new MethodAdapter(access, desc, labels, nodes, frames, maxLocals, mv));
        }
    }

    private static final class MethodAdapter extends MethodVisitor {

        private static final String STACK_RECORDER = "org/jvnet/zephyr/javaflow/runtime/StackRecorder";
        private static final int INT = 1;
        private static final int FLOAT = 2;
        private static final int DOUBLE = 3;
        private static final int LONG = 4;

        private final Label startLabel = new Label();
        private final int access;
        private final String desc;
        private final List<Label> labels;
        private final List<MethodInsnNode> nodes;
        private final List<Frame> frames;
        private final int maxLocals;
        private int currentIndex;
        private Frame currentFrame;

        MethodAdapter(int access, String desc, List<Label> labels, List<MethodInsnNode> nodes, List<Frame> frames,
                int maxLocals, MethodVisitor mv) {
            super(ASM5, mv);
            this.access = access;
            this.desc = desc;
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
            mv.visitMethodInsn(INVOKEVIRTUAL, STACK_RECORDER, "popInt", "()I", false);
            mv.visitTableSwitchInsn(0, n - 1, label, restoreLabels);

            // switch cases
            for (int i = 0; i < n; i++) {
                Label frameLabel = labels.get(i);
                mv.visitLabel(restoreLabels[i]);

                MethodInsnNode node = nodes.get(i);
                Frame frame = frames.get(i);

                // for each local variable store the value in locals popping it from the stack!
                // locals
                for (int j = 0, n1 = frame.locals.size(); j < n1; j++) {
                    Object obj = frame.locals.get(j);
                    if (obj == NULL) {
                        mv.visitInsn(ACONST_NULL);
                        mv.visitVarInsn(ASTORE, j);
                    } else if (obj instanceof String) {
                        mv.visitVarInsn(ALOAD, maxLocals);
                        mv.visitMethodInsn(INVOKEVIRTUAL, STACK_RECORDER, "popObject", "()Ljava/lang/Object;", false);
                        mv.visitTypeInsn(CHECKCAST, (String) obj);
                        mv.visitVarInsn(ASTORE, j);
                    } else if (obj instanceof Integer && obj != TOP && obj != UNINITIALIZED_THIS) {
                        mv.visitVarInsn(ALOAD, maxLocals);
                        int opcode = (Integer) obj;
                        Type type = getType(opcode);
                        mv.visitMethodInsn(INVOKEVIRTUAL, STACK_RECORDER, getPopMethod(opcode),
                                "()" + type.getDescriptor(), false);
                        mv.visitVarInsn(type.getOpcode(ISTORE), j);
                    }
                }

                // stack
                int argSize = (Type.getArgumentsAndReturnSizes(node.desc) >> 2) - 1;
                int ownerSize = node.getOpcode() == INVOKESTATIC ? 0 : 1; // TODO
                int stackSize = frame.stack.size();
                for (int j = 0, n1 = stackSize - argSize - ownerSize; j < n1; j++) {
                    Object obj = frame.stack.get(j);
                    if (obj == NULL) {
                        mv.visitInsn(ACONST_NULL);
                    } else if (obj instanceof String) {
                        mv.visitVarInsn(ALOAD, maxLocals);
                        mv.visitMethodInsn(INVOKEVIRTUAL, STACK_RECORDER, "popObject", "()Ljava/lang/Object;", false);
                        mv.visitTypeInsn(CHECKCAST, (String) obj);
                    } else if (obj instanceof Integer && obj != TOP && obj != UNINITIALIZED_THIS) {
                        int opcode = (Integer) obj;
                        Type type = getType(opcode);
                        mv.visitVarInsn(ALOAD, maxLocals);
                        mv.visitMethodInsn(INVOKEVIRTUAL, STACK_RECORDER, getPopMethod(opcode),
                                "()" + type.getDescriptor(), false);
                    }
                }

                if (node.getOpcode() != INVOKESTATIC) {
                    // Load the object whose method we are calling
                    Object obj = frame.stack.get(stackSize - argSize - 1);
                    if (obj == NULL) {
                        // If user code causes NPE, then we keep this behavior: load null to get NPE at runtime
                        mv.visitInsn(ACONST_NULL);
                    } else {
                        mv.visitVarInsn(ALOAD, maxLocals);
                        mv.visitMethodInsn(INVOKEVIRTUAL, STACK_RECORDER, "popReference", "()Ljava/lang/Object;",
                                false);
                        mv.visitTypeInsn(CHECKCAST, (String) obj);
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
                currentFrame = frames.get(currentIndex);
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

                int argSize = (Type.getArgumentsAndReturnSizes(desc) >> 2) - 1;
                int ownerSize = opcode == INVOKESTATIC ? 0 : 1; // TODO
                for (int i = currentFrame.stack.size() - argSize - ownerSize - 1; i >= 0; i--) {
                    Object obj = currentFrame.stack.get(i);
                    if (obj == NULL) {
                        mv.visitInsn(POP);
                    } else if (obj instanceof String) {
                        mv.visitVarInsn(ALOAD, maxLocals);
                        mv.visitInsn(SWAP);
                        mv.visitMethodInsn(INVOKEVIRTUAL, STACK_RECORDER, "pushObject", "(Ljava/lang/Object;)V", false);
                    } else if (obj instanceof Integer && obj != TOP && obj != UNINITIALIZED_THIS) {
                        Integer opcode1 = (Integer) obj;
                        Type type = getType(opcode1);
                        if (type.getSize() > 1) {
                            mv.visitInsn(ACONST_NULL); // dummy stack entry
                            mv.visitVarInsn(ALOAD, maxLocals);
                            mv.visitInsn(DUP2_X2); // swap2 for long/double
                            mv.visitInsn(POP2);
                            mv.visitMethodInsn(INVOKEVIRTUAL, STACK_RECORDER, getPushMethod(opcode1),
                                    '(' + type.getDescriptor() + ")V", false);
                            mv.visitInsn(POP); // remove dummy stack entry
                        } else {
                            mv.visitVarInsn(ALOAD, maxLocals);
                            mv.visitInsn(SWAP);
                            mv.visitMethodInsn(INVOKEVIRTUAL, STACK_RECORDER, getPushMethod(opcode1),
                                    '(' + type.getDescriptor() + ")V", false);
                        }
                    }
                }

                if ((access & ACC_STATIC) == 0) {
                    mv.visitVarInsn(ALOAD, maxLocals);
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitMethodInsn(INVOKEVIRTUAL, STACK_RECORDER, "pushReference", "(Ljava/lang/Object;)V", false);
                }

                // save locals
                for (int j = currentFrame.locals.size() - 1; j >= 0; j--) {
                    Object obj = currentFrame.locals.get(j);
                    if (obj instanceof String) {
                        mv.visitVarInsn(ALOAD, maxLocals);
                        mv.visitVarInsn(ALOAD, j);
                        mv.visitMethodInsn(INVOKEVIRTUAL, STACK_RECORDER, "pushObject", "(Ljava/lang/Object;)V", false);
                    } else if (obj instanceof Integer && obj != TOP && obj != NULL && obj != UNINITIALIZED_THIS) {
                        mv.visitVarInsn(ALOAD, maxLocals);
                        int opcode1 = (Integer) obj;
                        Type type = getType(opcode1);
                        mv.visitVarInsn(type.getOpcode(ILOAD), j);
                        mv.visitMethodInsn(INVOKEVIRTUAL, STACK_RECORDER, getPushMethod(opcode1),
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

        private static Type getType(int opcode) {
            switch (opcode) {
                case INT:
                    return Type.INT_TYPE;
                case FLOAT:
                    return Type.FLOAT_TYPE;
                case DOUBLE:
                    return Type.DOUBLE_TYPE;
                case LONG:
                    return Type.LONG_TYPE;
                default:
                    throw new IllegalArgumentException();
            }
        }

        private static String getPopMethod(int opcode) {
            switch (opcode) {
                case INT:
                    return "popInt";
                case FLOAT:
                    return "popFloat";
                case DOUBLE:
                    return "popDouble";
                case LONG:
                    return "popLong";
                default:
                    throw new IllegalArgumentException();
            }
        }

        private static String getPushMethod(int opcode) {
            switch (opcode) {
                case INT:
                    return "pushInt";
                case FLOAT:
                    return "pushFloat";
                case DOUBLE:
                    return "pushDouble";
                case LONG:
                    return "pushLong";
                default:
                    throw new IllegalArgumentException();
            }
        }
    }

    private static final class Frame {

        final List<Object> locals;
        final List<Object> stack;

        Frame(Collection<Object> locals, Collection<Object> stack) {
            this.locals = new ArrayList<>(locals);
            this.stack = new ArrayList<>(stack);
        }
    }
}

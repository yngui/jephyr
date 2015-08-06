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
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

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
import static org.objectweb.asm.Opcodes.ICONST_1;
import static org.objectweb.asm.Opcodes.ICONST_2;
import static org.objectweb.asm.Opcodes.ICONST_3;
import static org.objectweb.asm.Opcodes.ICONST_4;
import static org.objectweb.asm.Opcodes.ICONST_5;
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

final class ContinuationMethodAdapter extends MethodNode {

    private static final String STACK_RECORDER = "org/jvnet/zephyr/javaflow/runtime/StackRecorder";
    private static final int INT = 1;
    private static final int FLOAT = 2;
    private static final int DOUBLE = 3;
    private static final int LONG = 4;

    private final String owner;
    private final MethodVisitor mv;

    ContinuationMethodAdapter(String owner, int access, String name, String desc, String signature, String[] exceptions,
            MethodVisitor mv) {
        super(ASM5, access, name, desc, signature, exceptions);
        this.owner = owner;
        this.mv = mv;
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
        List<Node> nodes = analyze();

        if (nodes.isEmpty()) {
            accept(mv);
            return;
        }

        for (Node node : nodes) {
            instructions.insertBefore(node.insn, getLabelNode(node.label));
        }

        capturing(nodes);
        restoring(nodes);

        maxLocals = 0;
        maxStack = 0;

        accept(mv);
    }

    private List<Node> analyze() {
        final List<Node> nodes = new ArrayList<>();

        final class Adapter extends AnalyzerAdapter {

            AbstractInsnNode insn;

            Adapter() {
                super(ASM5, owner, access, name, desc, null);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                if (opcode == INVOKEINTERFACE || opcode == INVOKESPECIAL && name.charAt(0) != '<' ||
                        opcode == INVOKESTATIC || opcode == INVOKEVIRTUAL) {
                    nodes.add(new Node(new Label(), (MethodInsnNode) insn, locals.toArray(), stack.toArray()));
                }
                super.visitMethodInsn(opcode, owner, name, desc, itf);
            }
        }

        Adapter adapter = new Adapter();

        for (AbstractInsnNode insn = instructions.getFirst(); insn != null; insn = insn.getNext()) {
            adapter.insn = insn;
            insn.accept(adapter);
        }

        return nodes;
    }

    private void capturing(List<Node> nodes) {
        for (int i = 0, n = nodes.size(); i < n; i++) {
            Node node = nodes.get(i);
            InsnList list = new InsnList();
            Label label = new Label();

            list.add(new VarInsnNode(ALOAD, maxLocals));
            list.add(new JumpInsnNode(IFNULL, getLabelNode(label)));
            list.add(new VarInsnNode(ALOAD, maxLocals));
            list.add(new FieldInsnNode(GETFIELD, STACK_RECORDER, "isCapturing", "Z"));
            list.add(new JumpInsnNode(IFEQ, getLabelNode(label)));

            // save stack
            MethodInsnNode insn = node.insn;
            String desc = insn.desc;
            Type returnType = Type.getReturnType(desc);
            boolean hasReturn = returnType != Type.VOID_TYPE;
            if (hasReturn) {
                list.add(new InsnNode(returnType.getSize() == 1 ? POP : POP2));
            }

            int argSize = (Type.getArgumentsAndReturnSizes(desc) >> 2) - 1;
            int ownerSize = insn.getOpcode() == INVOKESTATIC ? 0 : 1; // TODO
            for (int j = node.stack.length - argSize - ownerSize - 1; j >= 0; j--) {
                Object obj = node.stack[j];
                if (obj == NULL) {
                    list.add(new InsnNode(POP));
                } else if (obj instanceof String) {
                    list.add(new VarInsnNode(ALOAD, maxLocals));
                    list.add(new InsnNode(SWAP));
                    list.add(new MethodInsnNode(INVOKEVIRTUAL, STACK_RECORDER, "pushObject", "(Ljava/lang/Object;)V",
                            false));
                } else if (obj instanceof Integer && obj != TOP && obj != UNINITIALIZED_THIS) {
                    Integer opcode1 = (Integer) obj;
                    Type type = getType(opcode1);
                    if (type.getSize() > 1) {
                        list.add(new InsnNode(ACONST_NULL)); // dummy stack entry
                        list.add(new VarInsnNode(ALOAD, maxLocals));
                        list.add(new InsnNode(DUP2_X2)); // swap2 for long/double
                        list.add(new InsnNode(POP2));
                        list.add(new MethodInsnNode(INVOKEVIRTUAL, STACK_RECORDER, getPushMethod(opcode1),
                                '(' + type.getDescriptor() + ")V", false));
                        list.add(new InsnNode(POP)); // remove dummy stack entry
                    } else {
                        list.add(new VarInsnNode(ALOAD, maxLocals));
                        list.add(new InsnNode(SWAP));
                        list.add(new MethodInsnNode(INVOKEVIRTUAL, STACK_RECORDER, getPushMethod(opcode1),
                                '(' + type.getDescriptor() + ")V", false));
                    }
                }
            }

            if ((access & ACC_STATIC) == 0) {
                list.add(new VarInsnNode(ALOAD, maxLocals));
                list.add(new VarInsnNode(ALOAD, 0));
                list.add(new MethodInsnNode(INVOKEVIRTUAL, STACK_RECORDER, "pushReference", "(Ljava/lang/Object;)V",
                        false));
            }

            // save locals
            for (int j = node.locals.length - 1; j >= 0; j--) {
                Object obj = node.locals[j];
                if (obj instanceof String) {
                    list.add(new VarInsnNode(ALOAD, maxLocals));
                    list.add(new VarInsnNode(ALOAD, j));
                    list.add(new MethodInsnNode(INVOKEVIRTUAL, STACK_RECORDER, "pushObject", "(Ljava/lang/Object;)V",
                            false));
                } else if (obj instanceof Integer && obj != TOP && obj != NULL && obj != UNINITIALIZED_THIS) {
                    list.add(new VarInsnNode(ALOAD, maxLocals));
                    int opcode1 = (Integer) obj;
                    Type type = getType(opcode1);
                    list.add(new VarInsnNode(type.getOpcode(ILOAD), j));
                    list.add(new MethodInsnNode(INVOKEVIRTUAL, STACK_RECORDER, getPushMethod(opcode1),
                            '(' + type.getDescriptor() + ")V", false));
                }
            }

            list.add(new VarInsnNode(ALOAD, maxLocals));

            switch (i) {
                case 0:
                    list.add(new InsnNode(ICONST_0));
                    break;
                case 1:
                    list.add(new InsnNode(ICONST_1));
                    break;
                case 2:
                    list.add(new InsnNode(ICONST_2));
                    break;
                case 3:
                    list.add(new InsnNode(ICONST_3));
                    break;
                case 4:
                    list.add(new InsnNode(ICONST_4));
                    break;
                case 5:
                    list.add(new InsnNode(ICONST_5));
                    break;
                default:
                    if (i <= Byte.MAX_VALUE) {
                        list.add(new IntInsnNode(BIPUSH, i));
                    } else {
                        list.add(new IntInsnNode(SIPUSH, i));
                    }
            }

            list.add(new MethodInsnNode(INVOKEVIRTUAL, STACK_RECORDER, "pushInt", "(I)V", false));

            Type methodReturnType = Type.getReturnType(this.desc);
            pushDefault(list, methodReturnType.getSort());
            list.add(new InsnNode(methodReturnType.getOpcode(IRETURN)));
            list.add(getLabelNode(label));

            instructions.insert(insn, list);
        }
    }

    private void restoring(List<Node> nodes) {
        InsnList list = new InsnList();
        Label startLabel = new Label();

        int n = nodes.size();
        Label[] restoreLabels = new Label[n];
        for (int i = 0; i < n; i++) {
            restoreLabels[i] = new Label();
        }

        // verify if restoring
        Label label = new Label();

        // PC: StackRecorder stackRecorder = StackRecorder.get();
        list.add(new MethodInsnNode(INVOKESTATIC, STACK_RECORDER, "get", "()L" + STACK_RECORDER + ';', false));
        list.add(new InsnNode(DUP));
        list.add(new VarInsnNode(ASTORE, maxLocals));
        list.add(getLabelNode(startLabel));

        // PC: if (stackRecorder != null && !stackRecorder.isRestoring) {
        list.add(new JumpInsnNode(IFNULL, getLabelNode(label)));
        list.add(new VarInsnNode(ALOAD, maxLocals));
        list.add(new FieldInsnNode(GETFIELD, STACK_RECORDER, "isRestoring", "Z"));
        list.add(new JumpInsnNode(IFEQ, getLabelNode(label)));

        list.add(new VarInsnNode(ALOAD, maxLocals));
        // PC: stackRecorder.popInt();
        list.add(new MethodInsnNode(INVOKEVIRTUAL, STACK_RECORDER, "popInt", "()I", false));
        list.add(new TableSwitchInsnNode(0, n - 1, getLabelNode(label), getLabelNodes(restoreLabels)));

        // switch cases
        for (int i = 0; i < n; i++) {
            list.add(getLabelNode(restoreLabels[i]));

            Node node = nodes.get(i);

            // for each local variable store the value in locals popping it from the stack!
            // locals
            for (int j = 0, n1 = node.locals.length; j < n1; j++) {
                Object obj = node.locals[j];
                if (obj == NULL) {
                    list.add(new InsnNode(ACONST_NULL));
                    list.add(new VarInsnNode(ASTORE, j));
                } else if (obj instanceof String) {
                    list.add(new VarInsnNode(ALOAD, maxLocals));
                    list.add(new MethodInsnNode(INVOKEVIRTUAL, STACK_RECORDER, "popObject", "()Ljava/lang/Object;",
                            false));
                    list.add(new TypeInsnNode(CHECKCAST, (String) obj));
                    list.add(new VarInsnNode(ASTORE, j));
                } else if (obj instanceof Integer && obj != TOP && obj != UNINITIALIZED_THIS) {
                    list.add(new VarInsnNode(ALOAD, maxLocals));
                    int opcode = (Integer) obj;
                    Type type = getType(opcode);
                    list.add(new MethodInsnNode(INVOKEVIRTUAL, STACK_RECORDER, getPopMethod(opcode),
                            "()" + type.getDescriptor(), false));
                    list.add(new VarInsnNode(type.getOpcode(ISTORE), j));
                }
            }

            MethodInsnNode insn = node.insn;

            // stack
            int argSize = (Type.getArgumentsAndReturnSizes(insn.desc) >> 2) - 1;
            int ownerSize = insn.getOpcode() == INVOKESTATIC ? 0 : 1; // TODO
            int stackSize = node.stack.length;

            for (int j = 0, n1 = stackSize - argSize - ownerSize; j < n1; j++) {
                Object obj = node.stack[j];
                if (obj == NULL) {
                    list.add(new InsnNode(ACONST_NULL));
                } else if (obj instanceof String) {
                    list.add(new VarInsnNode(ALOAD, maxLocals));
                    list.add(new MethodInsnNode(INVOKEVIRTUAL, STACK_RECORDER, "popObject", "()Ljava/lang/Object;",
                            false));
                    list.add(new TypeInsnNode(CHECKCAST, (String) obj));
                } else if (obj instanceof Integer && obj != TOP && obj != UNINITIALIZED_THIS) {
                    int opcode = (Integer) obj;
                    Type type = getType(opcode);
                    list.add(new VarInsnNode(ALOAD, maxLocals));
                    list.add(new MethodInsnNode(INVOKEVIRTUAL, STACK_RECORDER, getPopMethod(opcode),
                            "()" + type.getDescriptor(), false));
                }
            }

            if (insn.getOpcode() != INVOKESTATIC) {
                // Load the object whose method we are calling
                Object obj = node.stack[stackSize - argSize - 1];
                if (obj == NULL) {
                    // If user code causes NPE, then we keep this behavior: load null to get NPE at runtime
                    list.add(new InsnNode(ACONST_NULL));
                } else {
                    list.add(new VarInsnNode(ALOAD, maxLocals));
                    list.add(new MethodInsnNode(INVOKEVIRTUAL, STACK_RECORDER, "popReference", "()Ljava/lang/Object;",
                            false));
                    list.add(new TypeInsnNode(CHECKCAST, (String) obj));
                }
            }

            // Create null types for the parameters of the method invocation
            for (Type paramType : Type.getArgumentTypes(insn.desc)) {
                pushDefault(list, paramType.getSort());
            }

            // continue to the next method
            list.add(new JumpInsnNode(GOTO, getLabelNode(node.label)));
        }

        // PC: }
        // end of start block
        list.add(getLabelNode(label));

        instructions.insert(list);

        Label endLabel = new Label();
        instructions.add(getLabelNode(endLabel));
        localVariables.add(new LocalVariableNode("__stackRecorder", 'L' + STACK_RECORDER + ';', null,
                getLabelNode(startLabel), getLabelNode(endLabel), maxLocals));
    }

    private LabelNode[] getLabelNodes(Label[] labels) {
        int n = labels.length;
        LabelNode[] nodes = new LabelNode[n];
        for (int i = 0; i < n; ++i) {
            nodes[i] = getLabelNode(labels[i]);
        }
        return nodes;
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

    private void pushDefault(InsnList list, int sort) {
        switch (sort) {
            case Type.VOID:
                break;
            case Type.FLOAT:
                list.add(new InsnNode(FCONST_0));
                break;
            case Type.LONG:
                list.add(new InsnNode(LCONST_0));
                break;
            case Type.DOUBLE:
                list.add(new InsnNode(DCONST_0));
                break;
            case Type.ARRAY:
            case Type.OBJECT:
                list.add(new InsnNode(ACONST_NULL));
                break;
            default:
                list.add(new InsnNode(ICONST_0));
                break;
        }
    }

    private static final class Node {

        final Label label;
        final MethodInsnNode insn;
        final Object[] locals;
        final Object[] stack;

        Node(Label label, MethodInsnNode insn, Object[] locals, Object[] stack) {
            this.label = label;
            this.insn = insn;
            this.locals = locals;
            this.stack = stack;
        }
    }
}

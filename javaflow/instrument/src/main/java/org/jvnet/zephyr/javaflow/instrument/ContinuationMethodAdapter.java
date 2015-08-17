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
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AnalyzerAdapter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

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
import static org.objectweb.asm.Opcodes.DUP_X2;
import static org.objectweb.asm.Opcodes.FCONST_0;
import static org.objectweb.asm.Opcodes.F_NEW;
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

final class ContinuationMethodAdapter extends MethodNode {

    private static final String STACK_RECORDER = "org/jvnet/zephyr/javaflow/runtime/StackRecorder";
    private static final int INT = 1;
    private static final int FLOAT = 2;
    private static final int DOUBLE = 3;
    private static final int LONG = 4;
    private static final Object[] EMPTY_STACK = {};

    private final String owner;
    private final MethodVisitor mv;

    ContinuationMethodAdapter(String owner, int access, String name, String desc, String signature, String[] exceptions,
            MethodVisitor mv) {
        super(ASM5, access, name, desc, signature, exceptions);
        this.owner = owner;
        this.mv = mv;
    }

    @Override
    public void visitEnd() {
        Frame[] frames = analyze();

        int n = frames.length;
        if (n == 0) {
            accept(mv);
            return;
        }

        for (AbstractInsnNode node = instructions.getFirst(); node != null; node = node.getNext()) {
            if (node instanceof FrameNode) {
                FrameNode frameNode1 = (FrameNode) node;
                FrameNode frameNode2 = newFrameNode(frameNode1.local.toArray(), frameNode1.stack.toArray());
                instructions.set(node, frameNode2);
                node = frameNode2;
            }
        }

        LabelNode[] labelNodes = new LabelNode[n];
        for (int i = 0; i < n; i++) {
            labelNodes[i] = newLabelNode();
        }

        InsnList list = new InsnList();

        int n1 = frames.length;
        LabelNode[] restoreLabelNodes = new LabelNode[n1];
        for (int i = 0; i < n1; i++) {
            restoreLabelNodes[i] = newLabelNode();
        }

        // verify if restoring
        LabelNode labelNode = newLabelNode();

        // PC: StackRecorder stackRecorder = StackRecorder.get();
        list.add(new MethodInsnNode(INVOKESTATIC, STACK_RECORDER, "getStackRecorder", "()L" + STACK_RECORDER + ';',
                false));
        list.add(new VarInsnNode(ASTORE, maxLocals));

        LabelNode startLabelNode = newLabelNode();
        list.add(startLabelNode);

        // PC: if (stackRecorder != null && !stackRecorder.isSuspended()) {
        list.add(new VarInsnNode(ALOAD, maxLocals));
        list.add(new JumpInsnNode(IFNULL, labelNode));
        list.add(new VarInsnNode(ALOAD, maxLocals));
        list.add(new MethodInsnNode(INVOKEVIRTUAL, STACK_RECORDER, "isSuspended", "()Z", false));
        list.add(new JumpInsnNode(IFEQ, labelNode));

        list.add(new VarInsnNode(ALOAD, maxLocals));
        // PC: stackRecorder.popInt();
        list.add(new MethodInsnNode(INVOKEVIRTUAL, STACK_RECORDER, "popInt", "()I", false));
        list.add(new TableSwitchInsnNode(0, n1 - 1, labelNode, restoreLabelNodes));

        if (maxStack < 1) {
            maxStack = 1;
        }

        int rem = maxLocals;
        Collection<Object> col = new ArrayList<>();
        if ((access & ACC_STATIC) == 0) {
            col.add(owner);
            rem -= 1;
        }
        Type[] types = Type.getArgumentTypes(desc);
        for (Type type : types) {
            switch (type.getSort()) {
                case Type.FLOAT:
                    col.add(Opcodes.FLOAT);
                    break;
                case Type.LONG:
                    col.add(Opcodes.LONG);
                    break;
                case Type.DOUBLE:
                    col.add(Opcodes.DOUBLE);
                    break;
                case Type.ARRAY:
                    col.add(type.getDescriptor());
                    break;
                case Type.OBJECT:
                    col.add(type.getInternalName());
                    break;
                default:
                    col.add(Opcodes.INTEGER);
                    break;
            }
            rem -= type.getSize();
        }
        for (int i = 0; i < rem; i++) {
            col.add(TOP);
        }
        col.add(STACK_RECORDER);
        Object[] locals = col.toArray();
        int n2 = locals.length;

        // switch cases
        for (int i = 0; i < n1; i++) {
            Frame frame = frames[i];

            list.add(restoreLabelNodes[i]);
            list.add(new FrameNode(F_NEW, n2, locals, 0, EMPTY_STACK));

            // for each local variable store the value in locals popping it from the stack!
            // locals
            Object[] beforeLocals = frame.beforeLocals;

            for (int j = 0, n3 = beforeLocals.length; j < n3; j++) {
                Object obj = beforeLocals[j];
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

            int newMaxStack = 0;

            // stack
            MethodInsnNode node = frame.node;
            int argSize = (Type.getArgumentsAndReturnSizes(node.desc) >> 2) - 1;
            boolean invokeStatic = node.getOpcode() == INVOKESTATIC;
            int ownerSize = invokeStatic ? 0 : 1; // TODO
            Object[] beforeStack = frame.beforeStack;
            int n3 = beforeStack.length - argSize - ownerSize;

            for (int j = 0; j < n3; j++) {
                Object obj = beforeStack[j];
                if (obj == NULL) {
                    list.add(new InsnNode(ACONST_NULL));
                    newMaxStack += 1;
                } else if (obj instanceof String) {
                    list.add(new VarInsnNode(ALOAD, maxLocals));
                    list.add(new MethodInsnNode(INVOKEVIRTUAL, STACK_RECORDER, "popObject", "()Ljava/lang/Object;",
                            false));
                    list.add(new TypeInsnNode(CHECKCAST, (String) obj));
                    newMaxStack += 1;
                } else if (obj instanceof Integer && obj != TOP && obj != UNINITIALIZED_THIS) {
                    int opcode = (Integer) obj;
                    Type type = getType(opcode);
                    list.add(new VarInsnNode(ALOAD, maxLocals));
                    list.add(new MethodInsnNode(INVOKEVIRTUAL, STACK_RECORDER, getPopMethod(opcode),
                            "()" + type.getDescriptor(), false));
                    newMaxStack += type.getSize();
                }
            }

            if (!invokeStatic) {
                // Load the object whose method we are calling
                Object obj = beforeStack[n3];
                if (obj == NULL) {
                    // If user code causes NPE, then we keep this behavior: load null to get NPE at runtime
                    list.add(new InsnNode(ACONST_NULL));
                } else {
                    list.add(new VarInsnNode(ALOAD, maxLocals));
                    list.add(new MethodInsnNode(INVOKEVIRTUAL, STACK_RECORDER, "popObject", "()Ljava/lang/Object;",
                            false));
                    list.add(new TypeInsnNode(CHECKCAST, (String) obj));
                }
                newMaxStack += 1;
            }

            // Create null types for the parameters of the method invocation
            for (Type type : Type.getArgumentTypes(node.desc)) {
                addPushDefault(list, type.getSort());
                newMaxStack += type.getSize();
            }

            if (maxStack < newMaxStack) {
                maxStack = newMaxStack;
            }

            // continue to the next method
            list.add(new JumpInsnNode(GOTO, labelNodes[i]));
        }

        // PC: }
        // end of start block
        list.add(labelNode);
        list.add(new FrameNode(F_NEW, n2, locals, 0, EMPTY_STACK));

        instructions.insert(list);

        for (int i = 0; i < n; i++) {
            Frame frame = frames[i];
            InsnList list1 = new InsnList();
            addCapturing(list1, frame, i);
            MethodInsnNode node = frame.node;
            instructions.insertBefore(node, labelNodes[i]);
            instructions.insertBefore(node, newFrameNode(getValues(frame.beforeLocals), getValues(frame.beforeStack)));
            instructions.insert(node, list1);
        }

        LabelNode endLabelNode = newLabelNode();
        instructions.add(endLabelNode);

        localVariables.add(new LocalVariableNode("__stackRecorder", 'L' + STACK_RECORDER + ';', null, startLabelNode,
                endLabelNode, maxLocals));

        FrameNode prevFrameNode = null;
        for (AbstractInsnNode node = instructions.getFirst(); node != null; node = node.getNext()) {
            if (node instanceof FrameNode) {
                FrameNode frameNode = (FrameNode) node;
                if (prevFrameNode != null) {
                    instructions.remove(prevFrameNode);
                }
                prevFrameNode = frameNode;
            } else if (node.getOpcode() != -1) {
                prevFrameNode = null;
            }
        }

        maxLocals += 1;

        accept(mv);
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

    private Frame[] analyze() {
        final List<Frame> frames = new ArrayList<>();

        final class Analyzer extends AnalyzerAdapter {

            AbstractInsnNode node;

            Analyzer() {
                super(ASM5, owner, access, name, desc, null);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                if (opcode == INVOKEINTERFACE || opcode == INVOKESPECIAL && name.charAt(0) != '<' ||
                        opcode == INVOKESTATIC || opcode == INVOKEVIRTUAL) {
                    Object[] beforeLocals = locals.toArray();
                    Object[] beforeStack = stack.toArray();
                    super.visitMethodInsn(opcode, owner, name, desc, itf);
                    frames.add(new Frame((MethodInsnNode) node, beforeLocals, beforeStack, locals.toArray(),
                            stack.toArray()));
                } else {
                    super.visitMethodInsn(opcode, owner, name, desc, itf);
                }
            }
        }

        Analyzer analyzer = new Analyzer();

        for (AbstractInsnNode node = instructions.getFirst(); node != null; node = node.getNext()) {
            analyzer.node = node;
            node.accept(analyzer);
        }

        return frames.toArray(new Frame[frames.size()]);
    }

    private static Object[] getValues(Object[] values) {
        Collection<Object> col = new ArrayList<>(values.length);
        int i = 0;
        int n = values.length;
        while (i < n) {
            Object value = values[i];
            col.add(value);
            i += value == Opcodes.LONG || value == Opcodes.DOUBLE ? 2 : 1;
        }
        return col.toArray();
    }

    private void addCapturing(InsnList list, Frame frame, int index) {
        LabelNode labelNode = newLabelNode();

        int newMaxStack = frame.afterStack.length;

        list.add(new VarInsnNode(ALOAD, maxLocals));
        list.add(new JumpInsnNode(IFNULL, labelNode));
        list.add(new VarInsnNode(ALOAD, maxLocals));
        list.add(new MethodInsnNode(INVOKEVIRTUAL, STACK_RECORDER, "isSuspending", "()Z", false));
        list.add(new JumpInsnNode(IFEQ, labelNode));

        if (maxStack < newMaxStack + 1) {
            maxStack = newMaxStack + 1;
        }

        // save stack
        MethodInsnNode methodInsnNode = frame.node;
        int sizes = Type.getArgumentsAndReturnSizes(methodInsnNode.desc);
        int returnSize = sizes & 0x03;

        if (returnSize > 0) {
            list.add(new InsnNode(returnSize == 1 ? POP : POP2));
            newMaxStack -= returnSize;
        }

        int argSize = (sizes >> 2) - 1;
        int ownerSize = methodInsnNode.getOpcode() == INVOKESTATIC ? 0 : 1; // TODO
        Object[] beforeStack = frame.beforeStack;

        for (int i = beforeStack.length - argSize - ownerSize - 1; i >= 0; i--) {
            Object obj = beforeStack[i];
            if (obj == NULL) {
                list.add(new InsnNode(POP));
                newMaxStack -= 1;
            } else if (obj instanceof String) {
                list.add(new VarInsnNode(ALOAD, maxLocals));
                list.add(new InsnNode(SWAP));
                list.add(new MethodInsnNode(INVOKEVIRTUAL, STACK_RECORDER, "pushObject", "(Ljava/lang/Object;)V",
                        false));
                if (maxStack < newMaxStack + 1) {
                    maxStack = newMaxStack + 1;
                }
                newMaxStack -= 1;
            } else if (obj instanceof Integer && obj != TOP && obj != UNINITIALIZED_THIS) {
                Integer opcode = (Integer) obj;
                Type type = getType(opcode);
                if (type.getSize() > 1) {
                    list.add(new VarInsnNode(ALOAD, maxLocals));
                    list.add(new InsnNode(DUP_X2));
                    list.add(new InsnNode(POP));
                    list.add(new MethodInsnNode(INVOKEVIRTUAL, STACK_RECORDER, getPushMethod(opcode),
                            '(' + type.getDescriptor() + ")V", false));
                    if (maxStack < newMaxStack + 2) {
                        maxStack = newMaxStack + 2;
                    }
                    newMaxStack -= 2;
                } else {
                    list.add(new VarInsnNode(ALOAD, maxLocals));
                    list.add(new InsnNode(SWAP));
                    list.add(new MethodInsnNode(INVOKEVIRTUAL, STACK_RECORDER, getPushMethod(opcode),
                            '(' + type.getDescriptor() + ")V", false));
                    if (maxStack < newMaxStack + 1) {
                        maxStack = newMaxStack + 1;
                    }
                    newMaxStack -= 1;
                }
            }
        }

        // save locals
        int maxStackDelta = 2;

        Object[] beforeLocals = frame.beforeLocals;

        for (int i = beforeLocals.length - 1; i >= 0; i--) {
            Object obj = beforeLocals[i];
            if (obj instanceof String) {
                list.add(new VarInsnNode(ALOAD, maxLocals));
                list.add(new VarInsnNode(ALOAD, i));
                list.add(new MethodInsnNode(INVOKEVIRTUAL, STACK_RECORDER, "pushObject", "(Ljava/lang/Object;)V",
                        false));
            } else if (obj instanceof Integer && obj != TOP && obj != NULL && obj != UNINITIALIZED_THIS) {
                list.add(new VarInsnNode(ALOAD, maxLocals));
                int opcode = (Integer) obj;
                Type type = getType(opcode);
                list.add(new VarInsnNode(type.getOpcode(ILOAD), i));
                list.add(new MethodInsnNode(INVOKEVIRTUAL, STACK_RECORDER, getPushMethod(opcode),
                        '(' + type.getDescriptor() + ")V", false));
                if (type.getSize() > 1) {
                    maxStackDelta = 3;
                }
            }
        }

        list.add(new VarInsnNode(ALOAD, maxLocals));

        if (index <= 5) {
            list.add(new InsnNode(ICONST_0 + index));
        } else if (index <= Byte.MAX_VALUE) {
            list.add(new IntInsnNode(BIPUSH, index));
        } else if (index <= Short.MAX_VALUE) {
            list.add(new IntInsnNode(SIPUSH, index));
        } else {
            list.add(new LdcInsnNode(index));
        }

        list.add(new MethodInsnNode(INVOKEVIRTUAL, STACK_RECORDER, "pushInt", "(I)V", false));

        if ((access & ACC_STATIC) == 0) {
            list.add(new VarInsnNode(ALOAD, maxLocals));
            list.add(new VarInsnNode(ALOAD, 0));
            list.add(new MethodInsnNode(INVOKEVIRTUAL, STACK_RECORDER, "pushObject", "(Ljava/lang/Object;)V", false));
        }

        Type returnType = Type.getReturnType(desc);
        addPushDefault(list, returnType.getSort());
        list.add(new InsnNode(returnType.getOpcode(IRETURN)));

        list.add(labelNode);
        list.add(newFrameNode(getValues(frame.afterLocals), getValues(frame.afterStack)));

        if (maxStack < newMaxStack + maxStackDelta) {
            maxStack = newMaxStack + maxStackDelta;
        }
    }

    private static LabelNode newLabelNode() {
        Label label = new Label();
        LabelNode labelNode = new LabelNode(label);
        label.info = labelNode;
        return labelNode;
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

    private static void addPushDefault(InsnList list, int sort) {
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

    private FrameNode newFrameNode(Object[] locals, Object[] stack) {
        int n = maxLocals;
        for (Object local : locals) {
            n -= local == Opcodes.LONG || local == Opcodes.DOUBLE ? 2 : 1;
        }
        int n1 = locals.length;
        int n2 = n1 + n + 1;
        Object[] locals1 = new Object[n2];
        System.arraycopy(locals, 0, locals1, 0, n1);
        for (int i = 0; i < n; i++) {
            locals1[i + n1] = TOP;
        }
        locals1[n2 - 1] = STACK_RECORDER;
        return new FrameNode(F_NEW, n2, locals1, stack.length, stack);
    }

    private static final class Frame {

        final MethodInsnNode node;
        final Object[] beforeLocals;
        final Object[] beforeStack;
        final Object[] afterLocals;
        final Object[] afterStack;

        Frame(MethodInsnNode node, Object[] beforeLocals, Object[] beforeStack, Object[] afterLocals,
                Object[] afterStack) {
            this.node = node;
            this.beforeLocals = beforeLocals;
            this.beforeStack = beforeStack;
            this.afterLocals = afterLocals;
            this.afterStack = afterStack;
        }
    }
}

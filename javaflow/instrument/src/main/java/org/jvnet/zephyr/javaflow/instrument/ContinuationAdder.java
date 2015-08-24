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

package org.jvnet.zephyr.javaflow.instrument;

import org.objectweb.asm.Label;
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
import static org.objectweb.asm.Opcodes.DOUBLE;
import static org.objectweb.asm.Opcodes.DUP_X2;
import static org.objectweb.asm.Opcodes.FCONST_0;
import static org.objectweb.asm.Opcodes.FLOAT;
import static org.objectweb.asm.Opcodes.F_NEW;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.IFEQ;
import static org.objectweb.asm.Opcodes.IFNULL;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INTEGER;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.ISTORE;
import static org.objectweb.asm.Opcodes.LCONST_0;
import static org.objectweb.asm.Opcodes.LONG;
import static org.objectweb.asm.Opcodes.NULL;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.POP2;
import static org.objectweb.asm.Opcodes.SIPUSH;
import static org.objectweb.asm.Opcodes.SWAP;
import static org.objectweb.asm.Opcodes.TOP;
import static org.objectweb.asm.Opcodes.UNINITIALIZED_THIS;

final class ContinuationAdder {

    private static final String STACK_RECORDER = "org/jvnet/zephyr/javaflow/runtime/StackRecorder";
    private static final Object[] EMPTY_STACK = {};

    void process(String owner, MethodNode methodNode) {
        Analyzer analyzer = new Analyzer();
        analyzer.analyze(owner, methodNode);

        Collection<Entry> entries = analyzer.entries;
        if (entries.isEmpty()) {
            return;
        }

        InsnList instructions = methodNode.instructions;
        int maxLocals = methodNode.maxLocals;

        for (AbstractInsnNode node = instructions.getFirst(); node != null; node = node.getNext()) {
            if (node instanceof FrameNode) {
                FrameNode frameNode = (FrameNode) node;
                List<Object> local = new ArrayList<>(frameNode.local);
                appendValue(local, STACK_RECORDER, maxLocals);
                frameNode.local = local;
            }
        }

        LabelNode defaultLabelNode = newLabelNode();

        instructions.insert(defaultLabelNode);

        instructions.insertBefore(defaultLabelNode,
                new MethodInsnNode(INVOKESTATIC, STACK_RECORDER, "stackRecorder", "()L" + STACK_RECORDER + ';', false));
        instructions.insertBefore(defaultLabelNode, new VarInsnNode(ASTORE, maxLocals));
        instructions.insertBefore(defaultLabelNode, new VarInsnNode(ALOAD, maxLocals));
        instructions.insertBefore(defaultLabelNode, new JumpInsnNode(IFNULL, defaultLabelNode));
        instructions.insertBefore(defaultLabelNode, new VarInsnNode(ALOAD, maxLocals));
        instructions.insertBefore(defaultLabelNode,
                new MethodInsnNode(INVOKEVIRTUAL, STACK_RECORDER, "isSuspended", "()Z", false));
        instructions.insertBefore(defaultLabelNode, new JumpInsnNode(IFEQ, defaultLabelNode));
        instructions.insertBefore(defaultLabelNode, new VarInsnNode(ALOAD, maxLocals));
        instructions.insertBefore(defaultLabelNode,
                new MethodInsnNode(INVOKEVIRTUAL, STACK_RECORDER, "popInt", "()I", false));

        TableSwitchInsnNode tableSwitchInsnNode = new TableSwitchInsnNode(0, entries.size() - 1, defaultLabelNode);

        instructions.insertBefore(defaultLabelNode, tableSwitchInsnNode);

        updateMaxStack(methodNode, 1);

        Collection<Object> values = convertValues(analyzer.initialLocals);
        appendValue(values, STACK_RECORDER, maxLocals);
        Object[] initialLocals = values.toArray();

        Collection<LabelNode> restoreLabelNodes = tableSwitchInsnNode.labels;
        int index = 0;
        Type returnType = Type.getReturnType(methodNode.desc);

        for (Entry entry : entries) {
            LabelNode restoreLabelNode = newLabelNode();
            restoreLabelNodes.add(restoreLabelNode);

            instructions.insertBefore(defaultLabelNode, restoreLabelNode);
            instructions.insertBefore(defaultLabelNode,
                    new FrameNode(F_NEW, initialLocals.length, initialLocals, 0, EMPTY_STACK));

            Object[] locals1 = entry.locals1;

            for (int i = 0, n = locals1.length; i < n; i++) {
                Object obj = locals1[i];
                if (obj == NULL) {
                    instructions.insertBefore(defaultLabelNode, new InsnNode(ACONST_NULL));
                    instructions.insertBefore(defaultLabelNode, new VarInsnNode(ASTORE, i));
                    updateMaxStack(methodNode, 1);
                } else if (obj instanceof String) {
                    instructions.insertBefore(defaultLabelNode, new VarInsnNode(ALOAD, maxLocals));
                    instructions.insertBefore(defaultLabelNode,
                            new MethodInsnNode(INVOKEVIRTUAL, STACK_RECORDER, "popObject", "()Ljava/lang/Object;",
                                    false));
                    instructions.insertBefore(defaultLabelNode, new TypeInsnNode(CHECKCAST, (String) obj));
                    instructions.insertBefore(defaultLabelNode, new VarInsnNode(ASTORE, i));
                    updateMaxStack(methodNode, 1);
                } else {
                    Type type = getType(obj);
                    if (type != null) {
                        int sort = type.getSort();
                        instructions.insertBefore(defaultLabelNode, new VarInsnNode(ALOAD, maxLocals));
                        instructions.insertBefore(defaultLabelNode,
                                new MethodInsnNode(INVOKEVIRTUAL, STACK_RECORDER, getPopName(sort), getPopDesc(sort),
                                        false));
                        instructions.insertBefore(defaultLabelNode, new VarInsnNode(type.getOpcode(ISTORE), i));
                        updateMaxStack(methodNode, type.getSize());
                    }
                }
            }

            MethodInsnNode node = entry.node;
            Object[] stack2 = entry.stack2;
            int returnSize = Type.getArgumentsAndReturnSizes(node.desc) & 0x03;

            for (int i = 0, n = stack2.length - returnSize; i < n; i++) {
                Object obj = stack2[i];
                if (obj == NULL) {
                    instructions.insertBefore(defaultLabelNode, new InsnNode(ACONST_NULL));
                } else if (obj == UNINITIALIZED_THIS || obj instanceof String) {
                    instructions.insertBefore(defaultLabelNode, new VarInsnNode(ALOAD, maxLocals));
                    instructions.insertBefore(defaultLabelNode,
                            new MethodInsnNode(INVOKEVIRTUAL, STACK_RECORDER, "popObject", "()Ljava/lang/Object;",
                                    false));
                    instructions.insertBefore(defaultLabelNode, new TypeInsnNode(CHECKCAST, (String) obj));
                } else {
                    Type type = getType(obj);
                    if (type != null) {
                        int sort = type.getSort();
                        instructions.insertBefore(defaultLabelNode, new VarInsnNode(ALOAD, maxLocals));
                        instructions.insertBefore(defaultLabelNode,
                                new MethodInsnNode(INVOKEVIRTUAL, STACK_RECORDER, getPopName(sort), getPopDesc(sort),
                                        false));
                    }
                }
            }

            if (node.getOpcode() != INVOKESTATIC) {
                Object obj = entry.stack1[stack2.length - returnSize];
                if (obj == NULL) {
                    instructions.insertBefore(defaultLabelNode, new InsnNode(ACONST_NULL));
                } else {
                    instructions.insertBefore(defaultLabelNode, new VarInsnNode(ALOAD, maxLocals));
                    instructions.insertBefore(defaultLabelNode,
                            new MethodInsnNode(INVOKEVIRTUAL, STACK_RECORDER, "popObject", "()Ljava/lang/Object;",
                                    false));
                    instructions.insertBefore(defaultLabelNode, new TypeInsnNode(CHECKCAST, (String) obj));
                }
            }

            for (Type type : Type.getArgumentTypes(node.desc)) {
                instructions.insertBefore(defaultLabelNode, getDefaultPushNode(type.getSort()));
            }

            LabelNode labelNode1 = newLabelNode();

            instructions.insertBefore(defaultLabelNode, new JumpInsnNode(GOTO, labelNode1));

            instructions.insertBefore(node, labelNode1);

            for (AbstractInsnNode previous = labelNode1.getPrevious(); previous.getOpcode() == -1;
                    previous = previous.getPrevious()) {
                if (previous instanceof FrameNode) {
                    instructions.remove(previous);
                    break;
                }
            }

            instructions.insert(labelNode1, newFrameNode(locals1, entry.stack1, maxLocals));

            LabelNode labelNode2 = newLabelNode();

            instructions.insert(node, labelNode2);

            instructions.insertBefore(labelNode2, new VarInsnNode(ALOAD, maxLocals));
            instructions.insertBefore(labelNode2, new JumpInsnNode(IFNULL, labelNode2));
            instructions.insertBefore(labelNode2, new VarInsnNode(ALOAD, maxLocals));
            instructions.insertBefore(labelNode2,
                    new MethodInsnNode(INVOKEVIRTUAL, STACK_RECORDER, "isSuspending", "()Z", false));
            instructions.insertBefore(labelNode2, new JumpInsnNode(IFEQ, labelNode2));

            int stackSize = stack2.length;
            updateMaxStack(methodNode, stackSize + 1);

            if (returnSize == 1) {
                instructions.insertBefore(labelNode2, new InsnNode(POP));
                stackSize -= 1;
            } else if (returnSize == 2) {
                instructions.insertBefore(labelNode2, new InsnNode(POP2));
                stackSize -= 2;
            }

            for (int i = stack2.length - returnSize - 1; i >= 0; i--) {
                Object obj = stack2[i];
                if (obj == NULL) {
                    instructions.insertBefore(labelNode2, new InsnNode(POP));
                    stackSize -= 1;
                } else if (obj == UNINITIALIZED_THIS || obj instanceof String) {
                    instructions.insertBefore(labelNode2, new VarInsnNode(ALOAD, maxLocals));
                    instructions.insertBefore(labelNode2, new InsnNode(SWAP));
                    instructions.insertBefore(labelNode2,
                            new MethodInsnNode(INVOKEVIRTUAL, STACK_RECORDER, "pushObject", "(Ljava/lang/Object;)V",
                                    false));
                    updateMaxStack(methodNode, stackSize + 1);
                    stackSize -= 1;
                } else {
                    Type type = getType(obj);
                    if (type != null) {
                        int sort = type.getSort();
                        if (type.getSize() == 1) {
                            instructions.insertBefore(labelNode2, new VarInsnNode(ALOAD, maxLocals));
                            instructions.insertBefore(labelNode2, new InsnNode(SWAP));
                            instructions.insertBefore(labelNode2,
                                    new MethodInsnNode(INVOKEVIRTUAL, STACK_RECORDER, getPushName(sort),
                                            getPushDesc(sort), false));
                            updateMaxStack(methodNode, stackSize + 1);
                            stackSize -= 1;
                        } else {
                            instructions.insertBefore(labelNode2, new VarInsnNode(ALOAD, maxLocals));
                            instructions.insertBefore(labelNode2, new InsnNode(DUP_X2));
                            instructions.insertBefore(labelNode2, new InsnNode(POP));
                            instructions.insertBefore(labelNode2,
                                    new MethodInsnNode(INVOKEVIRTUAL, STACK_RECORDER, getPushName(sort),
                                            getPushDesc(sort), false));
                            updateMaxStack(methodNode, stackSize + 2);
                            stackSize -= 2;
                        }
                    }
                }
            }

            for (int i = locals1.length - 1; i >= 0; i--) {
                Object obj = locals1[i];
                if (obj instanceof String) {
                    instructions.insertBefore(labelNode2, new VarInsnNode(ALOAD, maxLocals));
                    instructions.insertBefore(labelNode2, new VarInsnNode(ALOAD, i));
                    instructions.insertBefore(labelNode2,
                            new MethodInsnNode(INVOKEVIRTUAL, STACK_RECORDER, "pushObject", "(Ljava/lang/Object;)V",
                                    false));
                    updateMaxStack(methodNode, stackSize + 2);
                } else {
                    Type type = getType(obj);
                    if (type != null) {
                        int sort = type.getSort();
                        instructions.insertBefore(labelNode2, new VarInsnNode(ALOAD, maxLocals));
                        instructions.insertBefore(labelNode2, new VarInsnNode(type.getOpcode(ILOAD), i));
                        instructions.insertBefore(labelNode2,
                                new MethodInsnNode(INVOKEVIRTUAL, STACK_RECORDER, getPushName(sort), getPushDesc(sort),
                                        false));
                        updateMaxStack(methodNode, stackSize + type.getSize() + 1);
                    }
                }
            }

            instructions.insertBefore(labelNode2, new VarInsnNode(ALOAD, maxLocals));

            if (index <= 5) {
                instructions.insertBefore(labelNode2, new InsnNode(ICONST_0 + index));
            } else if (index <= Byte.MAX_VALUE) {
                instructions.insertBefore(labelNode2, new IntInsnNode(BIPUSH, index));
            } else if (index <= Short.MAX_VALUE) {
                instructions.insertBefore(labelNode2, new IntInsnNode(SIPUSH, index));
            } else {
                instructions.insertBefore(labelNode2, new LdcInsnNode(index));
            }

            instructions.insertBefore(labelNode2,
                    new MethodInsnNode(INVOKEVIRTUAL, STACK_RECORDER, "pushInt", "(I)V", false));

            updateMaxStack(methodNode, stackSize + 2);

            if ((methodNode.access & ACC_STATIC) == 0) {
                instructions.insertBefore(labelNode2, new VarInsnNode(ALOAD, maxLocals));
                instructions.insertBefore(labelNode2, new VarInsnNode(ALOAD, 0));
                instructions.insertBefore(labelNode2,
                        new MethodInsnNode(INVOKEVIRTUAL, STACK_RECORDER, "pushObject", "(Ljava/lang/Object;)V",
                                false));
                updateMaxStack(methodNode, stackSize + 2);
            }

            int size = returnType.getSize();
            if (size > 0) {
                instructions.insertBefore(labelNode2, getDefaultPushNode(returnType.getSort()));
            }

            instructions.insertBefore(labelNode2, new InsnNode(returnType.getOpcode(IRETURN)));

            updateMaxStack(methodNode, stackSize + size);

            if (!isNextFrameNode(labelNode2)) {
                instructions.insert(labelNode2, newFrameNode(entry.locals2, stack2, maxLocals));
            }

            index++;
        }

        if (!isNextFrameNode(defaultLabelNode)) {
            instructions.insert(defaultLabelNode,
                    new FrameNode(F_NEW, initialLocals.length, initialLocals, 0, EMPTY_STACK));
        }

        methodNode.maxLocals += 1;
    }

    private static LabelNode newLabelNode() {
        Label label = new Label();
        LabelNode labelNode = new LabelNode(label);
        label.info = labelNode;
        return labelNode;
    }

    private static void updateMaxStack(MethodNode methodNode, int stackSize) {
        if (methodNode.maxStack < stackSize) {
            methodNode.maxStack = stackSize;
        }
    }

    private static Type getType(Object obj) {
        if (obj == INTEGER) {
            return Type.INT_TYPE;
        } else if (obj == FLOAT) {
            return Type.FLOAT_TYPE;
        } else if (obj == LONG) {
            return Type.LONG_TYPE;
        } else if (obj == DOUBLE) {
            return Type.DOUBLE_TYPE;
        } else {
            return null;
        }
    }

    private static String getPopName(int sort) {
        switch (sort) {
            case Type.INT:
                return "popInt";
            case Type.FLOAT:
                return "popFloat";
            case Type.LONG:
                return "popLong";
            default:
                return "popDouble";
        }
    }

    private static String getPopDesc(int sort) {
        switch (sort) {
            case Type.INT:
                return "()I";
            case Type.FLOAT:
                return "()F";
            case Type.LONG:
                return "()J";
            default:
                return "()D";
        }
    }

    private static AbstractInsnNode getDefaultPushNode(int sort) {
        switch (sort) {
            case Type.FLOAT:
                return new InsnNode(FCONST_0);
            case Type.LONG:
                return new InsnNode(LCONST_0);
            case Type.DOUBLE:
                return new InsnNode(DCONST_0);
            case Type.ARRAY:
            case Type.OBJECT:
                return new InsnNode(ACONST_NULL);
            default:
                return new InsnNode(ICONST_0);
        }
    }

    private static FrameNode newFrameNode(Object[] locals, Object[] stack, int maxLocals) {
        Collection<Object> locals1 = convertValues(locals);
        appendValue(locals1, STACK_RECORDER, maxLocals);
        Collection<Object> stack1 = convertValues(stack);
        return new FrameNode(F_NEW, locals1.size(), locals1.toArray(), stack1.size(), stack1.toArray());
    }

    private static Collection<Object> convertValues(Object[] values) {
        Collection<Object> col = new ArrayList<>(values.length);
        int i = 0;
        int n = values.length;
        while (i < n) {
            Object value = values[i];
            col.add(value);
            i += getSize(value);
        }
        return col;
    }

    private static void appendValue(Collection<Object> values, String value, int maxSize) {
        int n = 0;
        for (Object value1 : values) {
            n += getSize(value1);
        }
        for (int i = n; i < maxSize; i++) {
            values.add(TOP);
        }
        values.add(value);
    }

    private static int getSize(Object value) {
        return value == LONG || value == DOUBLE ? 2 : 1;
    }

    private static String getPushName(int sort) {
        switch (sort) {
            case Type.INT:
                return "pushInt";
            case Type.FLOAT:
                return "pushFloat";
            case Type.LONG:
                return "pushLong";
            default:
                return "pushDouble";
        }
    }

    private static String getPushDesc(int sort) {
        switch (sort) {
            case Type.INT:
                return "(I)V";
            case Type.FLOAT:
                return "(F)V";
            case Type.LONG:
                return "(J)V";
            default:
                return "(D)V";
        }
    }

    private static boolean isNextFrameNode(AbstractInsnNode node) {
        for (AbstractInsnNode next = node.getNext(); next.getOpcode() == -1; next = next.getNext()) {
            if (next instanceof FrameNode) {
                return true;
            }
        }
        return false;
    }

    private static final class Analyzer {

        Collection<Entry> entries;
        Object[] initialLocals;

        Analyzer() {
        }

        void analyze(final String owner, final MethodNode methodNode) {
            entries = new ArrayList<>();

            final class ClassAdapter extends AnalyzerAdapter {

                AbstractInsnNode node;

                ClassAdapter() {
                    super(ASM5, owner, methodNode.access, methodNode.name, methodNode.desc, null);
                    initialLocals = locals.toArray();
                }

                @Override
                public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                    if (name.charAt(0) == '<') {
                        super.visitMethodInsn(opcode, owner, name, desc, itf);
                    } else {
                        Object[] locals1 = locals.toArray();
                        Object[] stack1 = stack.toArray();
                        super.visitMethodInsn(opcode, owner, name, desc, itf);
                        entries.add(
                                new Entry((MethodInsnNode) node, locals1, stack1, locals.toArray(), stack.toArray()));
                    }
                }
            }

            ClassAdapter classAdapter = new ClassAdapter();

            for (AbstractInsnNode node = methodNode.instructions.getFirst(); node != null; node = node.getNext()) {
                classAdapter.node = node;
                node.accept(classAdapter);
            }
        }
    }

    private static final class Entry {

        final MethodInsnNode node;
        final Object[] locals1;
        final Object[] stack1;
        final Object[] locals2;
        final Object[] stack2;

        Entry(MethodInsnNode node, Object[] locals1, Object[] stack1, Object[] locals2, Object[] stack2) {
            this.node = node;
            this.locals1 = locals1;
            this.stack1 = stack1;
            this.locals2 = locals2;
            this.stack2 = stack2;
        }
    }
}

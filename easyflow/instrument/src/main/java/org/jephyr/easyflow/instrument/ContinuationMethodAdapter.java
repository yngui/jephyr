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

package org.jephyr.easyflow.instrument;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AnalyzerAdapter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.ATHROW;
import static org.objectweb.asm.Opcodes.BIPUSH;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.DCONST_0;
import static org.objectweb.asm.Opcodes.DLOAD;
import static org.objectweb.asm.Opcodes.DOUBLE;
import static org.objectweb.asm.Opcodes.DRETURN;
import static org.objectweb.asm.Opcodes.DSTORE;
import static org.objectweb.asm.Opcodes.DUP_X2;
import static org.objectweb.asm.Opcodes.FCONST_0;
import static org.objectweb.asm.Opcodes.FLOAD;
import static org.objectweb.asm.Opcodes.FLOAT;
import static org.objectweb.asm.Opcodes.FRETURN;
import static org.objectweb.asm.Opcodes.FSTORE;
import static org.objectweb.asm.Opcodes.F_NEW;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.IFEQ;
import static org.objectweb.asm.Opcodes.IFNULL;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INTEGER;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.ISTORE;
import static org.objectweb.asm.Opcodes.LCONST_0;
import static org.objectweb.asm.Opcodes.LLOAD;
import static org.objectweb.asm.Opcodes.LONG;
import static org.objectweb.asm.Opcodes.LRETURN;
import static org.objectweb.asm.Opcodes.LSTORE;
import static org.objectweb.asm.Opcodes.MONITORENTER;
import static org.objectweb.asm.Opcodes.MONITOREXIT;
import static org.objectweb.asm.Opcodes.NULL;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.POP2;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.SIPUSH;
import static org.objectweb.asm.Opcodes.SWAP;
import static org.objectweb.asm.Opcodes.TOP;

final class ContinuationMethodAdapter extends AnalyzingMethodNode {

    private static final Object[] EMPTY_OBJECTS = new Object[0];

    private final String owner;
    private final MethodVisitor mv;

    private ContinuationMethodAdapter(String owner, int access, String name, String desc, String signature,
            String[] exceptions, MethodVisitor mv) {
        super(access, name, desc, signature, exceptions);
        this.owner = owner;
        this.mv = mv;
    }

    static MethodVisitor create(String owner, int access, String name, String desc, String signature,
            String[] exceptions, MethodVisitor mv) {
        ContinuationMethodAdapter adapter =
                new ContinuationMethodAdapter(owner, access, name, desc, signature, exceptions, mv);
        AnalyzerAdapter analyzerAdapter = new AnalyzerAdapter(owner, access, name, desc, adapter);
        adapter.adapter = analyzerAdapter;
        return analyzerAdapter;
    }

    @Override
    public void visitEnd() {
        List<MethodInsnNode> nodes = findNodes();

        if (nodes.isEmpty()) {
            accept(mv);
            return;
        }

        int implVarIndex = maxLocals;
        maxLocals += 1;

        Object[] initialLocals = appendValue(ensureSize(frames.get(instructions.getFirst()).locals, implVarIndex),
                "org/jephyr/continuation/easyflow/ContinuationImpl");

        updateFrames(implVarIndex);
        addMonitorHooks(implVarIndex);

        LabelNode labelNode = newLabelNode();

        instructions.insert(labelNode);

        if (!isNextFrameNode(labelNode)) {
            instructions.insert(labelNode, newFrameNode(initialLocals, EMPTY_OBJECTS));
        }

        addInvocationEndedHook(implVarIndex, labelNode);

        instructions.insertBefore(labelNode,
                new MethodInsnNode(INVOKESTATIC, "org/jephyr/continuation/easyflow/ContinuationImpl", "currentImpl",
                        "()Lorg/jephyr/continuation/easyflow/ContinuationImpl;", false));
        instructions.insertBefore(labelNode, new VarInsnNode(ASTORE, implVarIndex));

        instructions.insertBefore(labelNode, new VarInsnNode(ALOAD, implVarIndex));
        instructions.insertBefore(labelNode, new JumpInsnNode(IFNULL, labelNode));

        instructions.insertBefore(labelNode, new VarInsnNode(ALOAD, implVarIndex));
        instructions.insertBefore(labelNode,
                new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl", "isSuspended",
                        "()Z", false));

        LabelNode labelNode1 = newLabelNode();

        instructions.insertBefore(labelNode, new JumpInsnNode(IFEQ, labelNode1));

        LabelNode defaultLabelNode = newLabelNode();
        int size = nodes.size();
        int length = size - 1;
        LabelNode[] labelNodes = new LabelNode[length];

        for (int i = 0; i < length; i++) {
            labelNodes[i] = newLabelNode();
        }

        if (length > 0) {
            instructions.insertBefore(labelNode, new VarInsnNode(ALOAD, implVarIndex));
            instructions.insertBefore(labelNode,
                    new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl", "popInt",
                            "()I", false));
            instructions.insertBefore(labelNode, new TableSwitchInsnNode(0, length - 1, defaultLabelNode, labelNodes));
        } else {
            instructions.insertBefore(labelNode, new JumpInsnNode(GOTO, defaultLabelNode));
        }

        updateMaxStack(1);

        Type returnType = Type.getReturnType(desc);

        for (int i = 0; i < size; i++) {
            MethodInsnNode node = nodes.get(i);
            Frame frame = frames.get(node);
            Object[] locals = frame.locals;
            Object[] stack = frame.stack;

            // resume

            LabelNode labelNode2 = i < length ? labelNodes[i] : defaultLabelNode;
            instructions.insertBefore(labelNode, labelNode2);
            instructions.insert(labelNode2, newFrameNode(initialLocals, EMPTY_OBJECTS));

            int intCount = length > 0 ? 1 : 0;
            int floatCount = 0;
            int longCount = 0;
            int doubleCount = 0;
            int objectCount = 0;

            for (int j = 0, n = locals.length; j < n; j++) {
                Object value = locals[j];
                if (value == INTEGER) {
                    instructions.insertBefore(labelNode, new VarInsnNode(ALOAD, implVarIndex));
                    instructions.insertBefore(labelNode,
                            new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl",
                                    "popInt", "()I", false));
                    instructions.insertBefore(labelNode, new VarInsnNode(ISTORE, j));
                    updateMaxStack(1);
                    intCount++;
                } else if (value == FLOAT) {
                    instructions.insertBefore(labelNode, new VarInsnNode(ALOAD, implVarIndex));
                    instructions.insertBefore(labelNode,
                            new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl",
                                    "popFloat", "()F", false));
                    instructions.insertBefore(labelNode, new VarInsnNode(FSTORE, j));
                    updateMaxStack(1);
                    floatCount++;
                } else if (value == LONG) {
                    instructions.insertBefore(labelNode, new VarInsnNode(ALOAD, implVarIndex));
                    instructions.insertBefore(labelNode,
                            new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl",
                                    "popLong", "()J", false));
                    instructions.insertBefore(labelNode, new VarInsnNode(LSTORE, j));
                    updateMaxStack(2);
                    longCount++;
                } else if (value == DOUBLE) {
                    instructions.insertBefore(labelNode, new VarInsnNode(ALOAD, implVarIndex));
                    instructions.insertBefore(labelNode,
                            new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl",
                                    "popDouble", "()D", false));
                    instructions.insertBefore(labelNode, new VarInsnNode(DSTORE, j));
                    updateMaxStack(2);
                    doubleCount++;
                } else if (value == NULL) {
                    instructions.insertBefore(labelNode, new InsnNode(ACONST_NULL));
                    instructions.insertBefore(labelNode, new VarInsnNode(ASTORE, j));
                    updateMaxStack(1);
                } else if (value instanceof String) {
                    instructions.insertBefore(labelNode, new VarInsnNode(ALOAD, implVarIndex));
                    instructions.insertBefore(labelNode,
                            new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl",
                                    "popObject", "()Ljava/lang/Object;", false));
                    instructions.insertBefore(labelNode, new TypeInsnNode(CHECKCAST, (String) value));
                    instructions.insertBefore(labelNode, new VarInsnNode(ASTORE, j));
                    updateMaxStack(1);
                    objectCount++;
                } else if (value != TOP) {
                    throw new IllegalStateException();
                }
            }

            int sizes = Type.getArgumentsAndReturnSizes(node.desc);
            int argSize = sizes >> 2;
            boolean invokeStatic = node.getOpcode() == INVOKESTATIC;
            if (invokeStatic) {
                argSize -= 1;
            }

            int stackSize = 0;

            for (int j = 0, n = stack.length - argSize; j < n; j++) {
                Object value = stack[j];
                if (value == INTEGER) {
                    instructions.insertBefore(labelNode, new VarInsnNode(ALOAD, implVarIndex));
                    instructions.insertBefore(labelNode,
                            new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl",
                                    "popInt", "()I", false));
                    stackSize += 1;
                    intCount++;
                } else if (value == FLOAT) {
                    instructions.insertBefore(labelNode, new VarInsnNode(ALOAD, implVarIndex));
                    instructions.insertBefore(labelNode,
                            new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl",
                                    "popFloat", "()F", false));
                    stackSize += 1;
                    floatCount++;
                } else if (value == LONG) {
                    instructions.insertBefore(labelNode, new VarInsnNode(ALOAD, implVarIndex));
                    instructions.insertBefore(labelNode,
                            new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl",
                                    "popLong", "()J", false));
                    stackSize += 2;
                    longCount++;
                } else if (value == DOUBLE) {
                    instructions.insertBefore(labelNode, new VarInsnNode(ALOAD, implVarIndex));
                    instructions.insertBefore(labelNode,
                            new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl",
                                    "popDouble", "()D", false));
                    stackSize += 2;
                    doubleCount++;
                } else if (value == NULL) {
                    instructions.insertBefore(labelNode, new InsnNode(ACONST_NULL));
                    stackSize += 1;
                } else if (value instanceof String) {
                    instructions.insertBefore(labelNode, new VarInsnNode(ALOAD, implVarIndex));
                    instructions.insertBefore(labelNode,
                            new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl",
                                    "popObject", "()Ljava/lang/Object;", false));
                    instructions.insertBefore(labelNode, new TypeInsnNode(CHECKCAST, (String) value));
                    stackSize += 1;
                    objectCount++;
                } else if (value != TOP) {
                    throw new IllegalStateException();
                }
            }

            updateMaxStack(stackSize);

            int targetVarIndex;
            int objVarIndex;
            int argsVarIndex;

            if (invokeStatic) {
                targetVarIndex = -1;
                objVarIndex = -1;
                argsVarIndex = -1;

                for (Type type : Type.getArgumentTypes(node.desc)) {
                    instructions.insertBefore(labelNode, newPushDefaultNode(type));
                    stackSize += type.getSize();
                }
            } else if (node.owner.equals("java/lang/reflect/Method") && node.name.equals("invoke") &&
                    node.desc.equals("(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;")) {
                int varIndex = implVarIndex + 1;

                if (stack[stack.length - 3] == NULL) {
                    targetVarIndex = -1;
                } else {
                    targetVarIndex = varIndex;
                    varIndex++;
                }

                if (stack[stack.length - 2] == NULL) {
                    objVarIndex = -1;
                } else {
                    objVarIndex = varIndex;
                    varIndex++;
                }

                if (stack[stack.length - 1] == NULL) {
                    argsVarIndex = -1;
                } else {
                    argsVarIndex = varIndex;
                    varIndex++;
                }

                if (maxLocals < varIndex) {
                    maxLocals = varIndex;
                }

                if (targetVarIndex == -1) {
                    instructions.insertBefore(labelNode, new InsnNode(ACONST_NULL));
                } else {
                    instructions.insertBefore(labelNode, new VarInsnNode(ALOAD, implVarIndex));
                    instructions.insertBefore(labelNode,
                            new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl",
                                    "popObject", "()Ljava/lang/Object;", false));
                    instructions.insertBefore(labelNode, new TypeInsnNode(CHECKCAST, "java/lang/reflect/Method"));
                    instructions.insertBefore(labelNode, new VarInsnNode(ASTORE, targetVarIndex));
                    instructions.insertBefore(labelNode, new VarInsnNode(ALOAD, targetVarIndex));
                    objectCount++;
                }

                if (objVarIndex == -1) {
                    instructions.insertBefore(labelNode, new InsnNode(ACONST_NULL));
                } else {
                    instructions.insertBefore(labelNode, new VarInsnNode(ALOAD, implVarIndex));
                    instructions.insertBefore(labelNode,
                            new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl",
                                    "popObject", "()Ljava/lang/Object;", false));
                    instructions.insertBefore(labelNode, new TypeInsnNode(CHECKCAST, (String) stack[stack.length - 2]));
                    instructions.insertBefore(labelNode, new VarInsnNode(ASTORE, objVarIndex));
                    instructions.insertBefore(labelNode, new VarInsnNode(ALOAD, objVarIndex));
                    objectCount++;
                }

                if (targetVarIndex == -1) {
                    instructions.insertBefore(labelNode, new InsnNode(ACONST_NULL));
                } else {
                    instructions.insertBefore(labelNode, new VarInsnNode(ALOAD, targetVarIndex));
                }

                instructions.insertBefore(labelNode,
                        new MethodInsnNode(INVOKEVIRTUAL, "java/lang/reflect/Method", "getParameterTypes",
                                "()[Ljava/lang/Class;", false));

                instructions.insertBefore(labelNode,
                        new MethodInsnNode(INVOKESTATIC, "org/jephyr/continuation/easyflow/ContinuationImpl",
                                "getDefaultArguments", "([Ljava/lang/Class;)[Ljava/lang/Object;", false));
                stackSize += 3;
            } else {
                targetVarIndex = -1;
                objVarIndex = -1;
                argsVarIndex = -1;

                instructions.insertBefore(labelNode, new VarInsnNode(ALOAD, implVarIndex));
                instructions.insertBefore(labelNode,
                        new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl",
                                "popObject", "()Ljava/lang/Object;", false));
                instructions.insertBefore(labelNode, new VarInsnNode(ASTORE, implVarIndex + 1));
                instructions.insertBefore(labelNode, new VarInsnNode(ALOAD, implVarIndex + 1));
                instructions
                        .insertBefore(labelNode, new TypeInsnNode(CHECKCAST, (String) stack[stack.length - argSize]));
                stackSize += 1;

                for (Type type : Type.getArgumentTypes(node.desc)) {
                    instructions.insertBefore(labelNode, newPushDefaultNode(type));
                    stackSize += type.getSize();
                }

                objectCount++;
            }

            updateMaxStack(stackSize);

            LabelNode labelNode3 = newLabelNode();

            instructions.insertBefore(labelNode, new JumpInsnNode(GOTO, labelNode3));

            // invocation starting

            if (invokeStatic) {
                instructions.insertBefore(node, new VarInsnNode(ALOAD, implVarIndex));
                instructions.insertBefore(node, new JumpInsnNode(IFNULL, labelNode3));

                instructions.insertBefore(node, new VarInsnNode(ALOAD, implVarIndex));
                instructions.insertBefore(node, new LdcInsnNode(Type.getType('L' + node.owner + ';')));
                instructions.insertBefore(node, new LdcInsnNode(node.name));
                instructions.insertBefore(node, new LdcInsnNode(node.desc));
                instructions.insertBefore(node,
                        new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl",
                                "staticInvocationStarting", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;)V",
                                false));

                instructions.insertBefore(node, labelNode3);
                instructions.insert(labelNode3, newFrameNode(appendValue(ensureSize(locals, implVarIndex),
                        "org/jephyr/continuation/easyflow/ContinuationImpl"), stack));
            } else if (node.owner.equals("java/lang/reflect/Method") && node.name.equals("invoke") &&
                    node.desc.equals("(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;")) {
                instructions.insertBefore(node, new VarInsnNode(ALOAD, implVarIndex));

                LabelNode labelNode4 = newLabelNode();

                instructions.insertBefore(node, new JumpInsnNode(IFNULL, labelNode4));

                instructions.insertBefore(node,
                        argsVarIndex == -1 ? new InsnNode(POP) : new VarInsnNode(ASTORE, argsVarIndex));
                instructions.insertBefore(node,
                        objVarIndex == -1 ? new InsnNode(POP) : new VarInsnNode(ASTORE, objVarIndex));
                instructions.insertBefore(node,
                        targetVarIndex == -1 ? new InsnNode(POP) : new VarInsnNode(ASTORE, targetVarIndex));

                instructions.insertBefore(node, new VarInsnNode(ALOAD, implVarIndex));
                instructions.insertBefore(node,
                        targetVarIndex == -1 ? new InsnNode(ACONST_NULL) : new VarInsnNode(ALOAD, targetVarIndex));
                instructions.insertBefore(node,
                        objVarIndex == -1 ? new InsnNode(ACONST_NULL) : new VarInsnNode(ALOAD, objVarIndex));
                instructions.insertBefore(node,
                        new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl",
                                "reflectiveInvocationStarting", "(Ljava/lang/reflect/Method;Ljava/lang/Object;)V",
                                false));

                instructions.insertBefore(node,
                        targetVarIndex == -1 ? new InsnNode(ACONST_NULL) : new VarInsnNode(ALOAD, targetVarIndex));
                instructions.insertBefore(node,
                        objVarIndex == -1 ? new InsnNode(ACONST_NULL) : new VarInsnNode(ALOAD, objVarIndex));
                instructions.insertBefore(node,
                        argsVarIndex == -1 ? new InsnNode(ACONST_NULL) : new VarInsnNode(ALOAD, argsVarIndex));

                instructions.insertBefore(node, new JumpInsnNode(GOTO, labelNode3));

                instructions.insertBefore(node, labelNode4);
                instructions.insert(labelNode4, newFrameNode(appendValue(ensureSize(locals, implVarIndex),
                        "org/jephyr/continuation/easyflow/ContinuationImpl"), stack));

                if (targetVarIndex != -1) {
                    instructions.insertBefore(node, new InsnNode(ACONST_NULL));
                    instructions.insertBefore(node, new VarInsnNode(ASTORE, targetVarIndex));
                }

                if (objVarIndex != -1) {
                    instructions.insertBefore(node, new InsnNode(ACONST_NULL));
                    instructions.insertBefore(node, new VarInsnNode(ASTORE, objVarIndex));
                }

                instructions.insertBefore(node, labelNode3);

                Object[] locals1 = appendValue(ensureSize(locals, implVarIndex),
                        "org/jephyr/continuation/easyflow/ContinuationImpl");

                if (targetVarIndex != -1) {
                    locals1 = appendValue(locals1, "java/lang/reflect/Method");
                }

                if (objVarIndex != -1) {
                    locals1 = appendValue(locals1, "java/lang/Object");
                }

                instructions.insert(labelNode3, newFrameNode(locals1, stack));
            } else {
                instructions.insertBefore(node, new VarInsnNode(ALOAD, implVarIndex));

                LabelNode labelNode4 = newLabelNode();

                instructions.insertBefore(node, new JumpInsnNode(IFNULL, labelNode4));

                targetVarIndex = implVarIndex + 1;
                int varIndex = targetVarIndex + 1;

                for (int j = stack.length - 1, k = stack.length - argSize + 1; j >= k; j--) {
                    Object value = stack[j];
                    if (value == INTEGER) {
                        instructions.insertBefore(node, new VarInsnNode(ISTORE, varIndex));
                        varIndex += 1;
                    } else if (value == FLOAT) {
                        instructions.insertBefore(node, new VarInsnNode(FSTORE, varIndex));
                        varIndex += 1;
                    } else if (value == DOUBLE) {
                        instructions.insertBefore(node, new VarInsnNode(DSTORE, varIndex));
                        varIndex += 2;
                    } else if (value == LONG) {
                        instructions.insertBefore(node, new VarInsnNode(LSTORE, varIndex));
                        varIndex += 2;
                    } else if (value == NULL) {
                        instructions.insertBefore(node, new InsnNode(POP));
                    } else if (value instanceof String) {
                        instructions.insertBefore(node, new VarInsnNode(ASTORE, varIndex));
                        varIndex += 1;
                    }
                }

                if (maxLocals < varIndex) {
                    maxLocals = varIndex;
                }

                instructions.insertBefore(node, new VarInsnNode(ASTORE, targetVarIndex));
                instructions.insertBefore(node, new VarInsnNode(ALOAD, targetVarIndex));

                for (int j = stack.length - argSize + 1, n = stack.length; j < n; j++) {
                    Object value = stack[j];
                    if (value == INTEGER) {
                        varIndex -= 1;
                        instructions.insertBefore(node, new VarInsnNode(ILOAD, varIndex));
                    } else if (value == FLOAT) {
                        varIndex -= 1;
                        instructions.insertBefore(node, new VarInsnNode(FLOAD, varIndex));
                    } else if (value == DOUBLE) {
                        varIndex -= 2;
                        instructions.insertBefore(node, new VarInsnNode(DLOAD, varIndex));
                    } else if (value == LONG) {
                        varIndex -= 2;
                        instructions.insertBefore(node, new VarInsnNode(LLOAD, varIndex));
                    } else if (value == NULL) {
                        instructions.insertBefore(node, new InsnNode(ACONST_NULL));
                    } else if (value instanceof String) {
                        varIndex -= 1;
                        instructions.insertBefore(node, new VarInsnNode(ALOAD, varIndex));
                    }
                }

                instructions.insertBefore(node, new VarInsnNode(ALOAD, implVarIndex));
                instructions.insertBefore(node, new VarInsnNode(ALOAD, targetVarIndex));
                instructions.insertBefore(node, new LdcInsnNode(node.name));
                instructions.insertBefore(node, new LdcInsnNode(node.desc));
                instructions.insertBefore(node,
                        new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl",
                                "invocationStarting", "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)V",
                                false));

                instructions.insertBefore(node, new JumpInsnNode(GOTO, labelNode3));

                instructions.insertBefore(node, labelNode4);
                instructions.insert(labelNode4, newFrameNode(appendValue(ensureSize(locals, implVarIndex),
                        "org/jephyr/continuation/easyflow/ContinuationImpl"), stack));

                instructions.insertBefore(node, new InsnNode(ACONST_NULL));
                instructions.insertBefore(node, new VarInsnNode(ASTORE, targetVarIndex));

                instructions.insertBefore(node, labelNode3);
                instructions.insert(labelNode3, newFrameNode(appendValues(ensureSize(locals, implVarIndex),
                        "org/jephyr/continuation/easyflow/ContinuationImpl", "java/lang/Object"), stack));
            }

            updateMaxStack(stack.length + 4);

            // suspend

            Frame frame1 = findNextFrame(frames, node);
            Object[] stack1 = frame1.stack;
            int stackSize1 = stack1.length;

            LabelNode labelNode4 = newLabelNode();

            instructions.insert(node, labelNode4);

            if (!isNextFrameNode(labelNode4)) {
                instructions.insert(labelNode4, newFrameNode(appendValue(ensureSize(frame1.locals, implVarIndex),
                        "org/jephyr/continuation/easyflow/ContinuationImpl"), stack1));
            }

            instructions.insertBefore(labelNode4, new VarInsnNode(ALOAD, implVarIndex));
            instructions.insertBefore(labelNode4, new JumpInsnNode(IFNULL, labelNode4));

            instructions.insertBefore(labelNode4, new VarInsnNode(ALOAD, implVarIndex));
            instructions.insertBefore(labelNode4,
                    new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl",
                            "isSuspending", "()Z", false));
            instructions.insertBefore(labelNode4, new JumpInsnNode(IFEQ, labelNode4));

            int returnSize = sizes & 0x03;
            if (returnSize == 1) {
                instructions.insertBefore(labelNode4, new InsnNode(POP));
                stackSize1 -= 1;
            } else if (returnSize == 2) {
                instructions.insertBefore(labelNode4, new InsnNode(POP2));
                stackSize1 -= 2;
            }

            if (intCount > 0) {
                instructions.insertBefore(labelNode4, new VarInsnNode(ALOAD, implVarIndex));
                instructions.insertBefore(labelNode4, newPushNode(intCount));
                instructions.insertBefore(labelNode4,
                        new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl",
                                "ensureIntStackSize", "(I)V", false));
                updateMaxStack(stackSize1 + 1);
            }

            if (floatCount > 0) {
                instructions.insertBefore(labelNode4, new VarInsnNode(ALOAD, implVarIndex));
                instructions.insertBefore(labelNode4, newPushNode(floatCount));
                instructions.insertBefore(labelNode4,
                        new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl",
                                "ensureFloatStackSize", "(I)V", false));
                updateMaxStack(stackSize1 + 1);
            }

            if (longCount > 0) {
                instructions.insertBefore(labelNode4, new VarInsnNode(ALOAD, implVarIndex));
                instructions.insertBefore(labelNode4, newPushNode(longCount));
                instructions.insertBefore(labelNode4,
                        new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl",
                                "ensureLongStackSize", "(I)V", false));
                updateMaxStack(stackSize1 + 1);
            }

            if (doubleCount > 0) {
                instructions.insertBefore(labelNode4, new VarInsnNode(ALOAD, implVarIndex));
                instructions.insertBefore(labelNode4, newPushNode(doubleCount));
                instructions.insertBefore(labelNode4,
                        new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl",
                                "ensureDoubleStackSize", "(I)V", false));
                updateMaxStack(stackSize1 + 1);
            }

            if (objectCount > 0) {
                instructions.insertBefore(labelNode4, new VarInsnNode(ALOAD, implVarIndex));
                instructions.insertBefore(labelNode4, newPushNode(objectCount));
                instructions.insertBefore(labelNode4,
                        new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl",
                                "ensureObjectStackSize", "(I)V", false));
                updateMaxStack(stackSize1 + 1);
            }

            if (!invokeStatic) {
                if (node.owner.equals("java/lang/reflect/Method") && node.name.equals("invoke") &&
                        node.desc.equals("(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;")) {
                    if (objVarIndex != -1) {
                        instructions.insertBefore(labelNode4, new VarInsnNode(ALOAD, implVarIndex));
                        instructions.insertBefore(labelNode4, new VarInsnNode(ALOAD, objVarIndex));
                        instructions.insertBefore(labelNode4,
                                new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl",
                                        "pushObject", "(Ljava/lang/Object;)V", false));
                        updateMaxStack(stackSize1 + 2);
                    }
                    if (targetVarIndex != -1) {
                        instructions.insertBefore(labelNode4, new VarInsnNode(ALOAD, implVarIndex));
                        instructions.insertBefore(labelNode4, new VarInsnNode(ALOAD, targetVarIndex));
                        instructions.insertBefore(labelNode4,
                                new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl",
                                        "pushObject", "(Ljava/lang/Object;)V", false));
                        updateMaxStack(stackSize1 + 2);
                    }
                } else {
                    instructions.insertBefore(labelNode4, new VarInsnNode(ALOAD, implVarIndex));
                    instructions.insertBefore(labelNode4, new VarInsnNode(ALOAD, targetVarIndex));
                    instructions.insertBefore(labelNode4,
                            new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl",
                                    "pushObject", "(Ljava/lang/Object;)V", false));
                    updateMaxStack(stackSize1 + 2);
                }
            }

            for (int j = stack.length - argSize - 1; j >= 0; j--) {
                Object value = stack[j];
                if (value == INTEGER) {
                    instructions.insertBefore(labelNode4, new VarInsnNode(ALOAD, implVarIndex));
                    instructions.insertBefore(labelNode4, new InsnNode(SWAP));
                    instructions.insertBefore(labelNode4,
                            new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl",
                                    "pushInt", "(I)V", false));
                    updateMaxStack(stackSize1 + 1);
                    stackSize1 -= 1;
                } else if (value == FLOAT) {
                    instructions.insertBefore(labelNode4, new VarInsnNode(ALOAD, implVarIndex));
                    instructions.insertBefore(labelNode4, new InsnNode(SWAP));
                    instructions.insertBefore(labelNode4,
                            new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl",
                                    "pushFloat", "(F)V", false));
                    updateMaxStack(stackSize1 + 1);
                    stackSize1 -= 1;
                } else if (value == LONG) {
                    instructions.insertBefore(labelNode4, new VarInsnNode(ALOAD, implVarIndex));
                    instructions.insertBefore(labelNode4, new InsnNode(DUP_X2));
                    instructions.insertBefore(labelNode4, new InsnNode(POP));
                    instructions.insertBefore(labelNode4,
                            new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl",
                                    "pushLong", "(J)V", false));
                    updateMaxStack(stackSize1 + 2);
                    stackSize1 -= 2;
                } else if (value == DOUBLE) {
                    instructions.insertBefore(labelNode4, new VarInsnNode(ALOAD, implVarIndex));
                    instructions.insertBefore(labelNode4, new InsnNode(DUP_X2));
                    instructions.insertBefore(labelNode4, new InsnNode(POP));
                    instructions.insertBefore(labelNode4,
                            new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl",
                                    "pushDouble", "(D)V", false));
                    updateMaxStack(stackSize1 + 2);
                    stackSize1 -= 2;
                } else if (value == NULL) {
                    instructions.insertBefore(labelNode4, new InsnNode(POP));
                    stackSize1 -= 1;
                } else if (value instanceof String) {
                    instructions.insertBefore(labelNode4, new VarInsnNode(ALOAD, implVarIndex));
                    instructions.insertBefore(labelNode4, new InsnNode(SWAP));
                    instructions.insertBefore(labelNode4,
                            new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl",
                                    "pushObject", "(Ljava/lang/Object;)V", false));
                    updateMaxStack(stackSize1 + 1);
                    stackSize1 -= 1;
                }
            }

            for (int j = locals.length - 1; j >= 0; j--) {
                Object value = locals[j];
                if (value == INTEGER) {
                    instructions.insertBefore(labelNode4, new VarInsnNode(ALOAD, implVarIndex));
                    instructions.insertBefore(labelNode4, new VarInsnNode(ILOAD, j));
                    instructions.insertBefore(labelNode4,
                            new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl",
                                    "pushInt", "(I)V", false));
                    updateMaxStack(stackSize1 + 2);
                } else if (value == FLOAT) {
                    instructions.insertBefore(labelNode4, new VarInsnNode(ALOAD, implVarIndex));
                    instructions.insertBefore(labelNode4, new VarInsnNode(FLOAD, j));
                    instructions.insertBefore(labelNode4,
                            new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl",
                                    "pushFloat", "(F)V", false));
                    updateMaxStack(stackSize1 + 2);
                } else if (value == LONG) {
                    instructions.insertBefore(labelNode4, new VarInsnNode(ALOAD, implVarIndex));
                    instructions.insertBefore(labelNode4, new VarInsnNode(LLOAD, j));
                    instructions.insertBefore(labelNode4,
                            new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl",
                                    "pushLong", "(J)V", false));
                    updateMaxStack(stackSize1 + 3);
                } else if (value == DOUBLE) {
                    instructions.insertBefore(labelNode4, new VarInsnNode(ALOAD, implVarIndex));
                    instructions.insertBefore(labelNode4, new VarInsnNode(DLOAD, j));
                    instructions.insertBefore(labelNode4,
                            new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl",
                                    "pushDouble", "(D)V", false));
                    updateMaxStack(stackSize1 + 3);
                } else if (value instanceof String) {
                    instructions.insertBefore(labelNode4, new VarInsnNode(ALOAD, implVarIndex));
                    instructions.insertBefore(labelNode4, new VarInsnNode(ALOAD, j));
                    instructions.insertBefore(labelNode4,
                            new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl",
                                    "pushObject", "(Ljava/lang/Object;)V", false));
                    updateMaxStack(stackSize1 + 2);
                }
            }

            if (length > 0) {
                instructions.insertBefore(labelNode4, new VarInsnNode(ALOAD, implVarIndex));
                instructions.insertBefore(labelNode4, newPushNode(i));
                instructions.insertBefore(labelNode4,
                        new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl",
                                "pushInt", "(I)V", false));
                updateMaxStack(stackSize1 + 2);
            }

            int returnSize1 = returnType.getSize();
            if (returnSize1 > 0) {
                instructions.insertBefore(labelNode4, newPushDefaultNode(returnType));
                updateMaxStack(stackSize1 + returnSize1);
            }

            instructions.insertBefore(labelNode4, new InsnNode(returnType.getOpcode(IRETURN)));
        }

        instructions.insertBefore(labelNode, labelNode1);
        instructions.insert(labelNode1, newFrameNode(initialLocals, EMPTY_OBJECTS));

        addInvocationStartedHook(implVarIndex, labelNode);

        accept(mv);
    }

    private List<MethodInsnNode> findNodes() {
        List<MethodInsnNode> nodes = new ArrayList<>();
        for (AbstractInsnNode next = instructions.getFirst(); next != null; next = next.getNext()) {
            if (next instanceof MethodInsnNode) {
                MethodInsnNode node = (MethodInsnNode) next;
                if (node.getOpcode() != INVOKESPECIAL || node.name.charAt(0) != '<') {
                    nodes.add(node);
                }
            }
        }
        return nodes;
    }

    private void updateFrames(int implVarIndex) {
        AbstractInsnNode next = instructions.getFirst();
        while (next != null) {
            AbstractInsnNode node = next;
            next = next.getNext();
            if (node instanceof FrameNode) {
                FrameNode frameNode = (FrameNode) node;
                Collection<Object> locals = new ArrayList<>();
                for (Object value : frameNode.local) {
                    locals.add(value);
                    if (isLong(value)) {
                        locals.add(TOP);
                    }
                }
                Object[] locals1 = convertValues(appendValue(ensureSize(locals.toArray(), implVarIndex),
                        "org/jephyr/continuation/easyflow/ContinuationImpl"));
                List<Object> stack = frameNode.stack;
                instructions.set(node, new FrameNode(F_NEW, locals1.length, locals1, stack.size(), stack.toArray()));
            }
        }
    }

    private void addMonitorHooks(int implVarIndex) {
        AbstractInsnNode next = instructions.getFirst();
        while (next != null) {
            AbstractInsnNode node = next;
            next = next.getNext();
            int opcode = node.getOpcode();
            if (opcode == MONITORENTER || opcode == MONITOREXIT) {
                LabelNode labelNode = newLabelNode();

                instructions.insert(node, labelNode);

                Frame frame = findNextFrame(frames, node);
                Object[] stack = frame.stack;

                if (!isNextFrameNode(labelNode)) {
                    instructions.insert(labelNode, newFrameNode(appendValue(ensureSize(frame.locals, implVarIndex),
                            "org/jephyr/continuation/easyflow/ContinuationImpl"), stack));
                }

                instructions.insertBefore(labelNode, new VarInsnNode(ALOAD, implVarIndex));
                instructions.insertBefore(labelNode, new JumpInsnNode(IFNULL, labelNode));

                instructions.insertBefore(labelNode, new VarInsnNode(ALOAD, implVarIndex));
                instructions.insertBefore(labelNode,
                        new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl",
                                opcode == MONITORENTER ? "monitorEntered" : "monitorExited", "()V", false));

                updateMaxStack(stack.length + 1);
            }
        }
    }

    private void addInvocationStartedHook(int implVarIndex, LabelNode labelNode) {
        if ((access & ACC_STATIC) == 0) {
            instructions.insertBefore(labelNode, new VarInsnNode(ALOAD, implVarIndex));
            instructions.insertBefore(labelNode, new VarInsnNode(ALOAD, 0));
            instructions.insertBefore(labelNode, new LdcInsnNode(name));
            instructions.insertBefore(labelNode, new LdcInsnNode(desc));
            instructions.insertBefore(labelNode,
                    new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl",
                            "invocationStarted", "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)V", false));
        } else {
            instructions.insertBefore(labelNode, new VarInsnNode(ALOAD, implVarIndex));
            instructions.insertBefore(labelNode, new LdcInsnNode(Type.getType('L' + owner + ';')));
            instructions.insertBefore(labelNode, new LdcInsnNode(name));
            instructions.insertBefore(labelNode, new LdcInsnNode(desc));
            instructions.insertBefore(labelNode,
                    new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl",
                            "staticInvocationStarted", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;)V",
                            false));
        }

        updateMaxStack(4);
    }

    private void addInvocationEndedHook(int implVarIndex, LabelNode labelNode) {
        LabelNode startLabelNode = labelNode;
        LabelNode handlerLabelNode = newLabelNode();

        for (AbstractInsnNode next = labelNode.getNext(); next != null; next = next.getNext()) {
            int opcode = next.getOpcode();
            if (opcode == IRETURN || opcode == LRETURN || opcode == FRETURN || opcode == DRETURN ||
                    opcode == ARETURN || opcode == RETURN) {
                LabelNode endLabelNode = newLabelNode();

                instructions.insertBefore(next, endLabelNode);
                instructions.insertBefore(next, new VarInsnNode(ALOAD, implVarIndex));

                LabelNode labelNode1 = newLabelNode();

                instructions.insertBefore(next, new JumpInsnNode(IFNULL, labelNode1));
                instructions.insertBefore(next, new VarInsnNode(ALOAD, implVarIndex));
                instructions.insertBefore(next,
                        new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl",
                                "invocationEnded", "()V", false));

                Frame frame = frames.get(next);
                Object[] stack = frame.stack;

                instructions.insertBefore(next, labelNode1);
                instructions.insertBefore(next, newFrameNode(appendValue(ensureSize(frame.locals, implVarIndex),
                        "org/jephyr/continuation/easyflow/ContinuationImpl"), stack));

                addTryCatchBlockNode(startLabelNode, endLabelNode, handlerLabelNode);

                startLabelNode = newLabelNode();

                instructions.insert(next, startLabelNode);

                updateMaxStack(stack.length + 1);
            }
        }

        instructions.add(handlerLabelNode);

        addTryCatchBlockNode(startLabelNode, handlerLabelNode, handlerLabelNode);

        Object[] locals = new Object[implVarIndex + 1];
        for (int i = 0; i < implVarIndex; i++) {
            locals[i] = TOP;
        }
        locals[implVarIndex] = "org/jephyr/continuation/easyflow/ContinuationImpl";
        Object[] stack = { "java/lang/Throwable" };

        instructions.add(newFrameNode(locals, stack));

        instructions.add(new VarInsnNode(ALOAD, implVarIndex));

        LabelNode labelNode1 = newLabelNode();

        instructions.add(new JumpInsnNode(IFNULL, labelNode1));

        instructions.add(new VarInsnNode(ALOAD, implVarIndex));
        instructions.add(new MethodInsnNode(INVOKEVIRTUAL, "org/jephyr/continuation/easyflow/ContinuationImpl",
                "invocationEnded", "()V", false));

        instructions.add(labelNode1);
        instructions.add(newFrameNode(locals, stack));
        instructions.add(new InsnNode(ATHROW));

        updateMaxStack(2);
    }

    private void addTryCatchBlockNode(LabelNode startLabelNode, LabelNode endLabelNode, LabelNode handlerLabelNode) {
        for (AbstractInsnNode next = startLabelNode.getNext(); next != endLabelNode; next = next.getNext()) {
            if (next.getOpcode() != -1) {
                tryCatchBlocks.add(new TryCatchBlockNode(startLabelNode, endLabelNode, handlerLabelNode, null));
                return;
            }
        }
    }

    private static boolean isLong(Object value) {
        return value == LONG || value == DOUBLE;
    }

    private static Object[] ensureSize(Object[] values, int size) {
        if (values.length >= size) {
            return values;
        }
        Object[] values1 = new Object[size];
        System.arraycopy(values, 0, values1, 0, values.length);
        for (int i = values.length; i < size; i++) {
            values1[i] = TOP;
        }
        return values1;
    }

    private static Object[] appendValue(Object[] values, Object value) {
        Object[] values1 = new Object[values.length + 1];
        System.arraycopy(values, 0, values1, 0, values.length);
        values1[values.length] = value;
        return values1;
    }

    private static Object[] appendValues(Object[] values, Object... valuesToSet) {
        Object[] values1 = new Object[values.length + valuesToSet.length];
        System.arraycopy(values, 0, values1, 0, values.length);
        System.arraycopy(valuesToSet, 0, values1, values.length, valuesToSet.length);
        return values1;
    }

    private static LabelNode newLabelNode() {
        Label label = new Label();
        LabelNode labelNode = new LabelNode(label);
        label.info = labelNode;
        return labelNode;
    }

    private static FrameNode newFrameNode(Object[] locals, Object[] stack) {
        Object[] locals1 = convertValues(locals);
        Object[] stack1 = convertValues(stack);
        return new FrameNode(F_NEW, locals1.length, locals1, stack1.length, stack1);
    }

    private static Object[] convertValues(Object[] values) {
        int n = values.length;
        Collection<Object> values1 = new ArrayList<>(n);
        int i = 0;
        while (i < n) {
            Object value = values[i];
            values1.add(value);
            i += isLong(value) ? 2 : 1;
        }
        return values1.toArray();
    }

    private static boolean isNextFrameNode(AbstractInsnNode node) {
        for (AbstractInsnNode next = node.getNext(); next != null && next.getOpcode() == -1; next = next.getNext()) {
            if (next instanceof FrameNode) {
                return true;
            }
        }
        return false;
    }

    private static AbstractInsnNode newPushDefaultNode(Type type) {
        switch (type.getSort()) {
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

    private static Frame findNextFrame(Map<AbstractInsnNode, Frame> frames, AbstractInsnNode node) {
        AbstractInsnNode next = node.getNext();
        while (true) {
            Frame frame = frames.get(next);
            if (frame != null) {
                return frame;
            }
            next = next.getNext();
        }
    }

    private static AbstractInsnNode newPushNode(int operand) {
        if (operand <= 5) {
            return new InsnNode(ICONST_0 + operand);
        } else if (operand <= Byte.MAX_VALUE) {
            return new IntInsnNode(BIPUSH, operand);
        } else if (operand <= Short.MAX_VALUE) {
            return new IntInsnNode(SIPUSH, operand);
        } else {
            return new LdcInsnNode(operand);
        }
    }

    private void updateMaxStack(int stackSize) {
        if (maxStack < stackSize) {
            maxStack = stackSize;
        }
    }
}

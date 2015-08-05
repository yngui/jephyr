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

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ASM5;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.DUP2_X1;
import static org.objectweb.asm.Opcodes.DUP2_X2;
import static org.objectweb.asm.Opcodes.DUP_X1;
import static org.objectweb.asm.Opcodes.DUP_X2;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.ISTORE;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.POP2;
import static org.objectweb.asm.Opcodes.SWAP;

final class NewMover extends MethodNode {

    private final Collection<MethodInsnNode> methods = new ArrayList<>();
    private final String className;
    private final MethodVisitor mv;

    NewMover(int access, String name, String desc, String signature, String[] exceptions, String className,
            MethodVisitor mv) {
        super(ASM5, access, name, desc, signature, exceptions);
        this.className = className;
        this.mv = mv;
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
        MethodInsnNode mnode = new MethodInsnNode(opcode, owner, name, desc, itf);
        if (opcode == INVOKESPECIAL || name.charAt(0) == '<') {
            methods.add(mnode);
        }
        instructions.add(mnode);
    }

    @Override
    public void visitEnd() {
        if (methods.isEmpty()) {
            accept(mv);
            return;
        }

        Analyzer<SourceValue> analyzer = new Analyzer<>(new SourceInterpreter());
        try {
            analyzer.analyze(className, this);
        } catch (AnalyzerException e) {
            throw new RuntimeException(e);
        }
        Frame<SourceValue>[] frames = analyzer.getFrames();

        Map<AbstractInsnNode, MethodInsnNode> nodes = new HashMap<>();

        for (MethodInsnNode node : methods) {
            // require to move NEW instruction
            int n = instructions.indexOf(node);
            Frame<SourceValue> frame = frames[n];
            Type[] types = Type.getArgumentTypes(node.desc);
            SourceValue value = frame.getStack(frame.getStackSize() - types.length - 1);

            for (AbstractInsnNode node1 : value.insns) {
                if (node1.getOpcode() == NEW) {
                    nodes.put(node1, node);
                } else {
                    // other known patterns
                    int n1 = instructions.indexOf(node1);

                    if (node1.getOpcode() == DUP) { // <init> with params
                        AbstractInsnNode node2 = instructions.get(n1 - 1);
                        if (node2.getOpcode() == NEW) {
                            nodes.put(node2, node);
                        }
                    } else if (node1.getOpcode() == SWAP) { // in exception handler
                        AbstractInsnNode node2 = instructions.get(n1 - 1);
                        AbstractInsnNode node3 = instructions.get(n1 - 2);
                        if (node2.getOpcode() == DUP_X1 && node3.getOpcode() == NEW) {
                            nodes.put(node3, node);
                        }
                    }
                }
            }
        }

        int maxStackDelta = 0;

        for (Map.Entry<AbstractInsnNode, MethodInsnNode> entry : nodes.entrySet()) {
            AbstractInsnNode node1 = entry.getKey();
            int n1 = instructions.indexOf(node1);
            AbstractInsnNode node2 = instructions.get(n1 + 1);
            AbstractInsnNode node3 = instructions.get(n1 + 2);
            instructions.remove(node1); // NEW
            boolean requireDup = false;

            int opcode = node2.getOpcode();
            if (opcode == DUP) {
                instructions.remove(node2); // DUP
                requireDup = true;
            } else if (opcode == DUP_X1) {
                instructions.remove(node2); // DUP_X1
                instructions.remove(node3); // SWAP
                requireDup = true;
            }

            MethodInsnNode node = entry.getValue();
            Type[] types = Type.getArgumentTypes(node.desc);
            int n2 = types.length;

            // optimizations for some common cases
            if (n2 == 0) {
                InsnList list = new InsnList();
                list.add(node1); // NEW

                if (requireDup) {
                    list.add(new InsnNode(DUP));
                }

                instructions.insertBefore(node, list);
            } else if (n2 == 1 && types[0].getSize() == 1) {
                InsnList list = new InsnList();
                list.add(node1); // NEW

                if (requireDup) {
                    list.add(new InsnNode(DUP));
                    list.add(new InsnNode(DUP2_X1));
                    list.add(new InsnNode(POP2));

                    if (maxStackDelta < 2) {
                        maxStackDelta = 2; // a two extra slots for temp values
                    }
                } else {
                    list.add(new InsnNode(SWAP));
                }

                instructions.insertBefore(node, list);
            } else if (n2 == 1 && types[0].getSize() == 2 ||
                    n2 == 2 && types[0].getSize() == 1 && types[1].getSize() == 1) {
                // TODO this one untested!
                InsnList list = new InsnList();
                list.add(node1); // NEW

                if (requireDup) {
                    list.add(new InsnNode(DUP));
                    list.add(new InsnNode(DUP2_X2));
                    list.add(new InsnNode(POP2));

                    if (maxStackDelta < 2) {
                        maxStackDelta = 2; // a two extra slots for temp values
                    }
                } else {
                    list.add(new InsnNode(DUP_X2));
                    list.add(new InsnNode(POP));

                    if (maxStackDelta < 1) {
                        maxStackDelta = 1; // an extra slot for temp value
                    }
                }

                instructions.insertBefore(node, list);
            } else {
                InsnList list = new InsnList();
                int var = maxLocals;

                // generic code using temporary locals
                // save stack
                for (int i = n2 - 1; i >= 0; i--) {
                    Type type = types[i];
                    list.add(new VarInsnNode(type.getOpcode(ISTORE), var));
                    var += type.getSize();
                }

                maxLocals = var;

                list.add(node1); // NEW

                if (requireDup) {
                    list.add(new InsnNode(DUP));
                }

                // restore stack
                for (Type type : types) {
                    var -= type.getSize();
                    list.add(new VarInsnNode(type.getOpcode(ILOAD), var));

                    // clean up store to avoid memory leak?
                    int sort = type.getSort();
                    if (sort == Type.OBJECT || sort == Type.ARRAY) {
                        list.add(new InsnNode(ACONST_NULL));
                        list.add(new VarInsnNode(type.getOpcode(ISTORE), var));

                        if (maxStackDelta < 1) {
                            maxStackDelta = 1; // an extra slot for ACONST_NULL
                        }
                    }
                }

                instructions.insertBefore(node, list);
            }
        }

        maxStack += maxStackDelta;

        accept(mv);
    }
}

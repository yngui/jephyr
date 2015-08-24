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
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

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
import static org.objectweb.asm.Opcodes.ISTORE;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.POP2;
import static org.objectweb.asm.Opcodes.SWAP;

final class NewRelocator {

    void process(String owner, MethodNode methodNode) {
        Analyzer analyzer = new Analyzer();
        analyzer.analyze(owner, methodNode);

        Collection<Entry> entries = analyzer.entries;
        if (entries.isEmpty()) {
            return;
        }

        InsnList instructions = methodNode.instructions;
        int maxStackDelta = 0;

        for (Entry entry : entries) {
            AbstractInsnNode newNode = entry.newNode;
            AbstractInsnNode node2 = newNode.getNext();
            AbstractInsnNode node3 = node2.getNext();
            instructions.remove(newNode); // NEW
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

            MethodInsnNode node = entry.node;
            Type[] types = Type.getArgumentTypes(node.desc);
            int n = types.length;

            // optimizations for some common cases
            if (n == 0) {
                InsnList list = new InsnList();
                list.add(newNode); // NEW

                if (requireDup) {
                    list.add(new InsnNode(DUP));
                }

                instructions.insertBefore(node, list);
            } else if (n == 1 && types[0].getSize() == 1) {
                InsnList list = new InsnList();
                list.add(newNode); // NEW

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
            } else if (n == 1 && types[0].getSize() == 2 ||
                    n == 2 && types[0].getSize() == 1 && types[1].getSize() == 1) {
                // TODO this one untested!
                InsnList list = new InsnList();
                list.add(newNode); // NEW

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
                int var = methodNode.maxLocals;

                // generic code using temporary locals
                // save stack
                for (int i = n - 1; i >= 0; i--) {
                    Type type = types[i];
                    list.add(new VarInsnNode(type.getOpcode(ISTORE), var));
                    var += type.getSize();
                }

                methodNode.maxLocals = var;

                list.add(newNode); // NEW

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

        methodNode.maxStack += maxStackDelta;
    }

    private static final class Analyzer {

        Collection<Entry> entries;

        Analyzer() {
        }

        void analyze(final String owner, final MethodNode methodNode) {
            entries = new ArrayList<>();

            final class ClassAdapter extends AnalyzerAdapter {

                AbstractInsnNode node;
                private final Map<Label, AbstractInsnNode> nodes = new HashMap<>();

                ClassAdapter() {
                    super(ASM5, owner, methodNode.access, methodNode.name, methodNode.desc, null);
                    mv = new MethodVisitor(ASM5) {
                        @Override
                        public void visitLabel(Label label) {
                            nodes.put(label, node);
                        }
                    };
                }

                @Override
                public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                    if (name.charAt(0) == '<') {
                        Label label = (Label) stack.get(stack.size() - (Type.getArgumentsAndReturnSizes(desc) >> 2));
                        AbstractInsnNode node1 = nodes.get(label);
                        while (node1.getOpcode() != NEW) {
                            node1 = node1.getNext();
                        }
                        entries.add(new Entry((MethodInsnNode) node, node1));
                    }
                    super.visitMethodInsn(opcode, owner, name, desc, itf);
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
        final AbstractInsnNode newNode;

        Entry(MethodInsnNode node, AbstractInsnNode newNode) {
            this.node = node;
            this.newNode = newNode;
        }
    }
}

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

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SimpleVerifier;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ContinuationMethodAnalyzer extends MethodNode implements Opcodes {

    protected final String className;
    protected final ClassVisitor cv;
    protected final MethodVisitor mv;

    protected final List<Label> labels = new ArrayList<Label>();
    protected final List<MethodInsnNode> nodes = new ArrayList<MethodInsnNode>();
    protected final List<MethodInsnNode> methods = new ArrayList<MethodInsnNode>();

    protected Analyzer analyzer;
    public int stackRecorderVar;

    public ContinuationMethodAnalyzer(String className, ClassVisitor cv, MethodVisitor mv, int access, String name, String desc, String signature, String[] exceptions) {
        super(Opcodes.ASM5, access, name, desc, signature, exceptions);
        this.className = className;
        this.cv = cv;
        this.mv = mv;
    }

    public int getIndex(AbstractInsnNode node) {
        return instructions.indexOf(node);
    }

    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
        MethodInsnNode mnode = new MethodInsnNode(opcode, owner, name, desc, itf);
        if (opcode == INVOKESPECIAL || name.charAt(0) == '<') {
            methods.add(mnode);
        }
        if (needsFrameGuard(opcode, owner, name, desc) /* && transformer.inScope(owner, name)*/) {
            Label label = new Label();
            super.visitLabel(label);
            labels.add(label);
            nodes.add(mnode);
        }
        instructions.add(mnode);
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
        if (instructions.size() == 0 || labels.size() == 0) {
            accept(mv);
            return;
        }

        /*
        {
                  TraceMethodVisitor mv = new TraceMethodVisitor();
                  System.err.println(name + desc);
                  for (int j = 0; j < instructions.size(); ++j) {
                      ((AbstractInsnNode) instructions.get(j)).accept(mv);
                      System.err.print("   " + mv.text.get(j)); // mv.text.get(j));
                  }
                  System.err.println();
        }
        */

        this.stackRecorderVar = maxLocals;
        try {
            moveNew();

            // analyzer = new Analyzer(new BasicVerifier());
            analyzer = new Analyzer(new SimpleVerifier() {

                protected Class<?> getClass(Type t) {
                    try {
                        if (t.getSort() == Type.ARRAY) {
                            return Class.forName(t.getDescriptor().replace('/', '.'), true, Thread.currentThread().getContextClassLoader());
                        }
                        return Class.forName(t.getClassName(), true, Thread.currentThread().getContextClassLoader());
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e.toString());
                    }
                }
            }) {

                protected Frame newFrame(final int nLocals, final int nStack) {
                    return new MonitoringFrame(nLocals, nStack);
                }

                protected Frame newFrame(final Frame src) {
                    return new MonitoringFrame(src);
                }

                public Frame[] analyze(final String owner, final MethodNode m) throws AnalyzerException {
                    // System.out.println("Analyze: "+owner+"|"+m.name+"|"+m.signature+"|"+m.tryCatchBlocks);
                    final Frame[] frames = super.analyze(owner, m);
                    for (int i = 0; i < m.instructions.size(); i++) {
                        int opcode = m.instructions.get(i).getOpcode();
                        if (opcode == MONITORENTER || opcode == MONITOREXIT) {
                            // System.out.println(i);
                        }
                    }
                    return frames;
                }
            };

            analyzer.analyze(className, this);
            accept(new ContinuationMethodAdapter(this));

            /*
            {
                      TraceMethodVisitor mv = new TraceMethodVisitor();
                      System.err.println("=================");

                      System.err.println(name + desc);
                      for (int j = 0; j < instructions.size(); ++j) {
                          ((AbstractInsnNode) instructions.get(j)).accept(mv);
                          System.err.print("   " + mv.text.get(j)); // mv.text.get(j));
                      }
                      System.err.println();
            }
            */

        } catch (AnalyzerException ex) {
            throw new RuntimeException(ex);
        }
    }

    @SuppressWarnings("unchecked")
    void moveNew() throws AnalyzerException {
        SourceInterpreter i = new SourceInterpreter();
        Analyzer a = new Analyzer(i);
        a.analyze(className, this);

        final HashMap<AbstractInsnNode, MethodInsnNode> movable = new HashMap<AbstractInsnNode, MethodInsnNode>();

        Frame[] frames = a.getFrames();
        for (int j = 0; j < methods.size(); j++) {
            MethodInsnNode mnode = methods.get(j);
            // require to move NEW instruction
            int n = instructions.indexOf(mnode);
            Frame f = frames[n];
            Type[] args = Type.getArgumentTypes(mnode.desc);

            SourceValue v = (SourceValue) f.getStack(f.getStackSize() - args.length - 1);
            Set<AbstractInsnNode> insns = v.insns;
            for (final AbstractInsnNode ins : insns) {
                if (ins.getOpcode() == NEW) {
                    movable.put(ins, mnode);
                } else {
                    // other known patterns
                    int n1 = instructions.indexOf(ins);
                    if (ins.getOpcode() == DUP) { // <init> with params
                        AbstractInsnNode ins1 = instructions.get(n1 - 1);
                        if (ins1.getOpcode() == NEW) {
                            movable.put(ins1, mnode);
                        }
                    } else if (ins.getOpcode() == SWAP) { // in exception handler
                        AbstractInsnNode ins1 = instructions.get(n1 - 1);
                        AbstractInsnNode ins2 = instructions.get(n1 - 2);
                        if (ins1.getOpcode() == DUP_X1 && ins2.getOpcode() == NEW) {
                            movable.put(ins2, mnode);
                        }
                    }
                }
            }
        }

        int updateMaxStack = 0;
        for (final Map.Entry<AbstractInsnNode, MethodInsnNode> e : movable.entrySet()) {
            AbstractInsnNode node1 = e.getKey();
            int n1 = instructions.indexOf(node1);
            AbstractInsnNode node2 = instructions.get(n1 + 1);
            AbstractInsnNode node3 = instructions.get(n1 + 2);
            int producer = node2.getOpcode();

            instructions.remove(node1); // NEW
            boolean requireDup = false;
            if (producer == DUP) {
                instructions.remove(node2); // DUP
                requireDup = true;
            } else if (producer == DUP_X1) {
                instructions.remove(node2); // DUP_X1
                instructions.remove(node3); // SWAP
                requireDup = true;
            }

            MethodInsnNode mnode = (MethodInsnNode) e.getValue();
            AbstractInsnNode nm = mnode;

            int varOffset = stackRecorderVar + 1;
            Type[] args = Type.getArgumentTypes(mnode.desc);

            // optimizations for some common cases
            if (args.length == 0) {
                final InsnList doNew = new InsnList();
                doNew.add(node1); // NEW
                if (requireDup)
                    doNew.add(new InsnNode(DUP));
                instructions.insertBefore(nm, doNew);
                nm = doNew.getLast();
                continue;
            }

            if (args.length == 1 && args[0].getSize() == 1) {
                final InsnList doNew = new InsnList();
                doNew.add(node1); // NEW
                if (requireDup) {
                    doNew.add(new InsnNode(DUP));
                    doNew.add(new InsnNode(DUP2_X1));
                    doNew.add(new InsnNode(POP2));
                    updateMaxStack = updateMaxStack < 2 ? 2 : updateMaxStack; // a two extra slots for temp values
                } else
                    doNew.add(new InsnNode(SWAP));
                instructions.insertBefore(nm, doNew);
                nm = doNew.getLast();
                continue;
            }

            // TODO this one untested!
            if ((args.length == 1 && args[0].getSize() == 2) || (args.length == 2 && args[0].getSize() == 1 && args[1].getSize() == 1)) {
                final InsnList doNew = new InsnList();
                doNew.add(node1); // NEW
                if (requireDup) {
                    doNew.add(new InsnNode(DUP));
                    doNew.add(new InsnNode(DUP2_X2));
                    doNew.add(new InsnNode(POP2));
                    updateMaxStack = updateMaxStack < 2 ? 2 : updateMaxStack; // a two extra slots for temp values
                } else {
                    doNew.add(new InsnNode(DUP_X2));
                    doNew.add(new InsnNode(POP));
                    updateMaxStack = updateMaxStack < 1 ? 1 : updateMaxStack; // an extra slot for temp value
                }
                instructions.insertBefore(nm, doNew);
                nm = doNew.getLast();
                continue;
            }

            final InsnList doNew = new InsnList();
            // generic code using temporary locals
            // save stack
            for (int j = args.length - 1; j >= 0; j--) {
                Type type = args[j];

                doNew.add(new VarInsnNode(type.getOpcode(ISTORE), varOffset));
                varOffset += type.getSize();
            }
            if (varOffset > maxLocals) {
                maxLocals = varOffset;
            }

            doNew.add(node1); // NEW

            if (requireDup)
                doNew.add(new InsnNode(DUP));

            // restore stack
            for (int j = 0; j < args.length; j++) {
                Type type = args[j];
                varOffset -= type.getSize();

                doNew.add(new VarInsnNode(type.getOpcode(ILOAD), varOffset));

                // clean up store to avoid memory leak?
                if (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY) {
                    updateMaxStack = updateMaxStack < 1 ? 1 : updateMaxStack; // an extra slot for ACONST_NULL

                    doNew.add(new InsnNode(ACONST_NULL));

                    doNew.add(new VarInsnNode(type.getOpcode(ISTORE), varOffset));
                }
            }
            instructions.insertBefore(nm, doNew);
            nm = doNew.getLast();
        }

        maxStack += updateMaxStack;
    }

    boolean needsFrameGuard(int opcode, String owner, String name, String desc) {
        /* TODO: need to customize a way enchancer skips classes/methods
            if (owner.startsWith("java/")) {
                System.out.println("SKIP:: " + owner + "." + name + desc);
                return false;
            }
        */

        if (opcode == Opcodes.INVOKEINTERFACE || (opcode == Opcodes.INVOKESPECIAL && !"<init>".equals(name)) || opcode == Opcodes.INVOKESTATIC
                || opcode == Opcodes.INVOKEVIRTUAL) {
            return true;
        }
        return false;
    }

}
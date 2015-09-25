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

package org.jvnet.zephyr.continuation.instrument;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.commons.AnalyzerAdapter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.objectweb.asm.Opcodes.ASM5;
import static org.objectweb.asm.Opcodes.NEW;

abstract class AnalyzingMethodNode extends MethodNode {

    final Map<AbstractInsnNode, Frame> frames = new HashMap<>();
    AnalyzerAdapter adapter;

    AnalyzingMethodNode(int access, String name, String desc, String signature, String[] exceptions) {
        super(ASM5, access, name, desc, signature, exceptions);
    }

    @Override
    public final void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {
        FrameNode node = new FrameNode(type, nLocal, local == null ? null : convertValues(local), nStack,
                stack == null ? null : convertValues(stack));
        instructions.add(node);
        addFrame(node);
    }

    private Object[] convertValues(Object[] values) {
        int n = values.length;
        Object[] nodes = new Object[n];
        for (int i = 0; i < n; i++) {
            Object value = values[i];
            if (value instanceof Label) {
                nodes[i] = getLabelNode((Label) value);
            } else {
                nodes[i] = value;
            }
        }
        return nodes;
    }

    @Override
    public final void visitInsn(int opcode) {
        InsnNode node = new InsnNode(opcode);
        instructions.add(node);
        addFrame(node);
    }

    @Override
    public final void visitIntInsn(int opcode, int operand) {
        IntInsnNode node = new IntInsnNode(opcode, operand);
        instructions.add(node);
        addFrame(node);
    }

    @Override
    public final void visitVarInsn(int opcode, int var) {
        VarInsnNode node = new VarInsnNode(opcode, var);
        instructions.add(node);
        addFrame(node);
    }

    @Override
    public final void visitTypeInsn(int opcode, String type) {
        TypeInsnNode node = new TypeInsnNode(opcode, type);
        instructions.add(node);
        addFrame(node);
    }

    @Override
    public final void visitFieldInsn(int opcode, String owner, String name, String desc) {
        FieldInsnNode node = new FieldInsnNode(opcode, owner, name, desc);
        instructions.add(node);
        addFrame(node);
    }

    @Override
    public final void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
        MethodInsnNode node = new MethodInsnNode(opcode, owner, name, desc, itf);
        instructions.add(node);
        addFrame(node);
    }

    @Override
    public final void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
        InvokeDynamicInsnNode node = new InvokeDynamicInsnNode(name, desc, bsm, bsmArgs);
        instructions.add(node);
        addFrame(node);
    }

    @Override
    public final void visitJumpInsn(int opcode, Label label) {
        JumpInsnNode node = new JumpInsnNode(opcode, getLabelNode(label));
        instructions.add(node);
        addFrame(node);
    }

    @Override
    public final void visitLabel(Label label) {
        LabelNode node = getLabelNode(label);
        instructions.add(node);
        addFrame(node);
    }

    @Override
    public final void visitLdcInsn(Object cst) {
        LdcInsnNode node = new LdcInsnNode(cst);
        instructions.add(node);
        addFrame(node);
    }

    @Override
    public final void visitIincInsn(int var, int increment) {
        IincInsnNode node = new IincInsnNode(var, increment);
        instructions.add(node);
        addFrame(node);
    }

    @Override
    public final void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
        TableSwitchInsnNode node = new TableSwitchInsnNode(min, max, getLabelNode(dflt), getLabelNodes(labels));
        instructions.add(node);
        addFrame(node);
    }

    private LabelNode[] getLabelNodes(Label[] labels) {
        int n = labels.length;
        LabelNode[] nodes = new LabelNode[n];
        for (int i = 0; i < n; ++i) {
            nodes[i] = getLabelNode(labels[i]);
        }
        return nodes;
    }

    @Override
    public final void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
        LookupSwitchInsnNode node = new LookupSwitchInsnNode(getLabelNode(dflt), keys, getLabelNodes(labels));
        instructions.add(node);
        addFrame(node);
    }

    @Override
    public final void visitMultiANewArrayInsn(String desc, int dims) {
        MultiANewArrayInsnNode node = new MultiANewArrayInsnNode(desc, dims);
        instructions.add(node);
        addFrame(node);
    }

    @Override
    public final void visitLineNumber(int line, Label start) {
        LineNumberNode node = new LineNumberNode(line, getLabelNode(start));
        instructions.add(node);
        addFrame(node);
    }

    private void addFrame(AbstractInsnNode node) {
        Collection<?> locals = adapter.locals;
        if (locals != null) {
            frames.put(node, new Frame(convertValues(locals), convertValues(adapter.stack)));
        }
    }

    private static Object[] convertValues(Collection<?> values) {
        Object[] values1 = new Object[values.size()];
        int i = 0;
        for (Object value : values) {
            if (value instanceof Label) {
                AbstractInsnNode next = ((AbstractInsnNode) ((Label) value).info).getNext();
                while (next.getOpcode() != NEW) {
                    next = next.getNext();
                }
                values1[i] = next;
            } else {
                values1[i] = value;
            }
            i++;
        }
        return values1;
    }

    @Override
    protected final LabelNode getLabelNode(Label l) {
        Object info = l.info;
        if (info instanceof LabelNode) {
            return (LabelNode) info;
        }
        LabelNode labelNode = new LabelNode(l);
        l.info = labelNode;
        return labelNode;
    }

    static final class Frame {

        final Object[] locals;
        final Object[] stack;

        Frame(Object[] locals, Object[] stack) {
            this.locals = locals;
            this.stack = stack;
        }
    }
}

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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Predicate;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static java.util.Objects.requireNonNull;
import static org.objectweb.asm.ClassReader.SKIP_DEBUG;
import static org.objectweb.asm.ClassReader.SKIP_FRAMES;
import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ASM5;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;

public final class AnalyzingMethodRefPredicate implements Predicate<MethodRef> {

    private static final Predicate<AbstractInsnNode> IS_METHOD_INSN_NODE = t -> t instanceof MethodInsnNode;

    private final Map<MethodRef, Boolean> suspendables = new HashMap<>();

    public AnalyzingMethodRefPredicate(byte[] buffer, Predicate<MethodRef> predicate) {
        requireNonNull(buffer);
        requireNonNull(predicate);
        ClassReader reader = new ClassReader(buffer);
        ClassAdapter adapter = new ClassAdapter(suspendables, predicate);
        reader.accept(adapter, SKIP_DEBUG | SKIP_FRAMES);
    }

    @Override
    public boolean test(MethodRef t) {
        Boolean suspendable = suspendables.get(t);
        if (suspendable == null) {
            throw new IllegalArgumentException("Method " + t.getName() + t.getDesc() + " not found");
        }
        return suspendable;
    }

    private static <T> boolean any(Iterator<? extends T> iterator, Predicate<? super T> predicate) {
        while (iterator.hasNext()) {
            if (predicate.test(iterator.next())) {
                return true;
            }
        }
        return false;
    }

    private static final class ClassAdapter extends ClassVisitor {

        private final Map<MethodRef, MethodNode> nodes = new HashMap<>();
        private final Map<MethodRef, Boolean> suspendables;
        private final Predicate<MethodRef> predicate;
        private int access;
        private String name;

        ClassAdapter(Map<MethodRef, Boolean> suspendables, Predicate<MethodRef> predicate) {
            super(ASM5);
            this.suspendables = suspendables;
            this.predicate = predicate;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {
            this.access = access;
            this.name = name;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodNode node = new MethodNode(access, name, desc, signature, exceptions);
            nodes.put(new MethodRef(name, desc), node);
            return node;
        }

        @Override
        public void visitEnd() {
            Predicate<AbstractInsnNode> isForeign = t -> {
                if (t instanceof MethodInsnNode) {
                    MethodInsnNode insn = (MethodInsnNode) t;
                    if (!insn.owner.equals(name)) {
                        if (insn.owner.charAt(0) != '[' && (!insn.owner.startsWith("java/") ||
                                insn.getOpcode() != INVOKESPECIAL && insn.getOpcode() != INVOKESTATIC)) {
                            return true;
                        }
                    } else if (insn.getOpcode() != INVOKESPECIAL) {
                        MethodNode node = nodes.get(new MethodRef(insn.name, insn.desc));
                        if (node == null || (access & ACC_FINAL) == 0 &&
                                (node.access & (ACC_PRIVATE | ACC_STATIC | ACC_FINAL)) == 0) {
                            return true;
                        }
                    }
                }
                return false;
            };

            for (Map.Entry<MethodRef, MethodNode> entry : nodes.entrySet()) {
                MethodRef ref = entry.getKey();
                MethodNode node = entry.getValue();
                if (!predicate.test(ref) || !any(node.instructions.iterator(), IS_METHOD_INSN_NODE)) {
                    suspendables.put(ref, false);
                } else if (any(node.instructions.iterator(), isForeign)) {
                    suspendables.put(ref, true);
                }
            }

            Predicate<AbstractInsnNode> isSuspendable = t -> {
                if (t instanceof MethodInsnNode) {
                    MethodInsnNode insn = (MethodInsnNode) t;
                    Boolean suspendable = suspendables.get(new MethodRef(insn.name, insn.desc));
                    if (suspendable != null && suspendable) {
                        return true;
                    }
                }
                return false;
            };

            boolean found;
            do {
                found = false;
                for (Map.Entry<MethodRef, MethodNode> entry : nodes.entrySet()) {
                    MethodRef ref = entry.getKey();
                    if (!suspendables.containsKey(ref) &&
                            any(entry.getValue().instructions.iterator(), isSuspendable)) {
                        suspendables.put(ref, true);
                        found = true;
                    }
                }
            } while (found);

            nodes.keySet().stream().filter(ref -> !suspendables.containsKey(ref))
                    .forEach(ref -> suspendables.put(ref, false));
        }
    }
}

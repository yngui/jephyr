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

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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

    private static final Predicate<AbstractInsnNode> IS_METHOD_INSN_NODE = new Predicate<AbstractInsnNode>() {
        @Override
        public boolean apply(AbstractInsnNode input) {
            return input instanceof MethodInsnNode;
        }
    };

    private final ConcurrentMap<String, Map<Key, Boolean>> map = new ConcurrentHashMap<>();
    private final Predicate<MethodRef> parent;
    private final ClassLoader loader;

    public AnalyzingMethodRefPredicate(Predicate<MethodRef> parent, ClassLoader loader) {
        this.parent = requireNonNull(parent);
        this.loader = requireNonNull(loader);
    }

    @Override
    public boolean apply(MethodRef input) {
        String owner = input.getOwner();
        if (owner.charAt(0) == '[') {
            owner = "java/lang/Object";
        }
        Map<Key, Boolean> map = this.map.get(owner);
        if (map == null) {
            try (InputStream in = loader.getResourceAsStream(owner + ".class")) {
                if (in == null) {
                    throw new IllegalArgumentException("Object type " + owner + " not found");
                }
                ClassReader reader = new ClassReader(in);
                map = new HashMap<>();
                ClassAdapter adapter = new ClassAdapter(map);
                reader.accept(adapter, SKIP_DEBUG | SKIP_FRAMES);
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }
            Map<Key, Boolean> prevMap = this.map.putIfAbsent(owner, map);
            if (prevMap != null) {
                map = prevMap;
            }
        }
        String name = input.getName();
        String desc = input.getDesc();
        Boolean suspendable = map.get(new Key(name, desc));
        if (suspendable == null) {
            throw new IllegalArgumentException("Method " + owner + '.' + name + desc + " not found");
        }
        return suspendable;
    }

    private static <T> boolean any(Iterator<? extends T> iterator, Predicate<? super T> predicate) {
        while (iterator.hasNext()) {
            if (predicate.apply(iterator.next())) {
                return true;
            }
        }
        return false;
    }

    private final class ClassAdapter extends ClassVisitor {

        final Map<Key, MethodNode> methods = new HashMap<>();
        final Map<Key, Boolean> map;
        int access;
        String name;

        ClassAdapter(Map<Key, Boolean> map) {
            super(ASM5);
            this.map = map;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                String[] interfaces) {
            this.access = access;
            this.name = name;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodNode method = new MethodNode(access, name, desc, signature, exceptions);
            methods.put(new Key(name, desc), method);
            return method;
        }

        @Override
        public void visitEnd() {
            Predicate<AbstractInsnNode> isForeign = new Predicate<AbstractInsnNode>() {
                @Override
                public boolean apply(AbstractInsnNode input) {
                    if (input instanceof MethodInsnNode) {
                        MethodInsnNode insn = (MethodInsnNode) input;
                        if (!insn.owner.equals(name)) {
                            if (insn.owner.charAt(0) != '[' && (!insn.owner.startsWith("java/") ||
                                    insn.getOpcode() != INVOKESPECIAL && insn.getOpcode() != INVOKESTATIC)) {
                                return true;
                            }
                        } else if (insn.getOpcode() != INVOKESPECIAL) {
                            MethodNode method = methods.get(new Key(insn.name, insn.desc));
                            if (method == null || (access & ACC_FINAL) == 0 &&
                                    (method.access & (ACC_PRIVATE | ACC_STATIC | ACC_FINAL)) == 0) {
                                return true;
                            }
                        }
                    }
                    return false;
                }
            };

            for (MethodNode method : methods.values()) {
                if (!parent.apply(new MethodRef(name, method.name, method.desc)) ||
                        !any(method.instructions.iterator(), IS_METHOD_INSN_NODE)) {
                    map.put(new Key(method.name, method.desc), false);
                } else if (any(method.instructions.iterator(), isForeign)) {
                    map.put(new Key(method.name, method.desc), true);
                }
            }

            Predicate<AbstractInsnNode> isSuspendable = new Predicate<AbstractInsnNode>() {
                @Override
                public boolean apply(AbstractInsnNode input) {
                    if (input instanceof MethodInsnNode) {
                        MethodInsnNode insn = (MethodInsnNode) input;
                        Boolean suspendable = map.get(new Key(insn.name, insn.desc));
                        if (suspendable != null && suspendable) {
                            return true;
                        }
                    }
                    return false;
                }
            };

            boolean found;
            do {
                found = false;
                for (MethodNode method : methods.values()) {
                    Key key = new Key(method.name, method.desc);
                    if (!map.containsKey(key)) {
                        if (any(method.instructions.iterator(), isSuspendable)) {
                            map.put(key, true);
                            found = true;
                        }
                    }
                }
            } while (found);

            for (MethodNode method : methods.values()) {
                Key key = new Key(method.name, method.desc);
                if (!map.containsKey(key)) {
                    map.put(key, false);
                }
            }
        }
    }

    private static final class Key {

        private final String name;
        private final String desc;

        Key(String name, String desc) {
            this.name = name;
            this.desc = desc;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof Key)) {
                return false;
            }
            Key other = (Key) obj;
            return name.equals(other.name) && desc.equals(other.desc);
        }

        @Override
        public int hashCode() {
            int result = 17;
            result = 31 * result + name.hashCode();
            result = 31 * result + desc.hashCode();
            return result;
        }
    }

}

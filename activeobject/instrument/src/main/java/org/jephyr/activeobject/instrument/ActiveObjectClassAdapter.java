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

package org.jephyr.activeobject.instrument;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AnalyzerAdapter;
import org.objectweb.asm.commons.LocalVariablesSorter;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.ParameterNode;
import org.objectweb.asm.tree.TypeAnnotationNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import static org.objectweb.asm.Opcodes.ACC_ABSTRACT;
import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_NATIVE;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PROTECTED;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ACC_SYNTHETIC;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ANEWARRAY;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASM5;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.ATHROW;
import static org.objectweb.asm.Opcodes.BIPUSH;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.DOUBLE;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.DUP2_X1;
import static org.objectweb.asm.Opcodes.FLOAT;
import static org.objectweb.asm.Opcodes.F_APPEND;
import static org.objectweb.asm.Opcodes.F_NEW;
import static org.objectweb.asm.Opcodes.F_SAME;
import static org.objectweb.asm.Opcodes.F_SAME1;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.ICONST_1;
import static org.objectweb.asm.Opcodes.IFEQ;
import static org.objectweb.asm.Opcodes.IF_ACMPNE;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INTEGER;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.ISTORE;
import static org.objectweb.asm.Opcodes.LONG;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.POP2;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.PUTSTATIC;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.SIPUSH;
import static org.objectweb.asm.Opcodes.SWAP;
import static org.objectweb.asm.Opcodes.TOP;
import static org.objectweb.asm.Opcodes.UNINITIALIZED_THIS;

public final class ActiveObjectClassAdapter extends ClassVisitor {

    public Collection<ClassEntry> classEntries;
    private Collection<MethodNode> methodNodes;
    private int version;
    private String name;
    private String superName;
    private boolean instrument = true;
    private boolean activeObject;
    private Type mailbox = Type.getType("Lorg/jephyr/activeobject/mailbox/SingleConsumerMailboxSupplier;");
    private boolean clinit;
    private int nextTaskNum;

    public ActiveObjectClassAdapter(ClassVisitor cv) {
        super(ASM5, cv);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.version = version;
        this.name = name;
        this.superName = superName;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        AnnotationVisitor av = super.visitAnnotation(desc, visible);
        if (instrument) {
            if (desc.equals("Lorg/jephyr/activeobject/instrument/Instrumented;")) {
                instrument = false;
            } else if (desc.equals("Lorg/jephyr/activeobject/annotation/ActiveObject;")) {
                super.visitAnnotation("Lorg/jephyr/activeobject/instrument/Instrumented;", false);
                activeObject = true;
                classEntries = new ArrayList<>();
                methodNodes = new ArrayList<>();
                return new AnnotationVisitor(ASM5, av) {
                    @Override
                    public void visit(String name, Object value) {
                        if (name.equals("mailbox")) {
                            mailbox = (Type) value;
                        }
                        super.visit(name, value);
                    }
                };
            }
        }
        return av;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
        if (activeObject) {
            if ((access & ACC_STATIC) != 0 && name.equals("<clinit>") && desc.equals("()V")) {
                clinit = true;
                return new ClinitMethodAdapter(mv);
            } else if (name.charAt(0) == '<') {
                InitMethodAdapter adapter = new InitMethodAdapter(access, desc, mv);
                AnalyzerAdapter analyzerAdapter = new AnalyzerAdapter(this.name, access, name, desc, adapter);
                adapter.adapter = analyzerAdapter;
                return analyzerAdapter;
            } else {
                return new MethodAdapter(access, name, desc, signature, exceptions, mv);
            }
        }
        return mv;
    }

    @Override
    public void visitEnd() {
        if (activeObject) {
            FieldVisitor fv =
                    visitField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL | ACC_SYNTHETIC, "activeObject$mailboxSupplier",
                            "Ljava/util/function/Supplier;", null, null);
            fv.visitEnd();

            FieldVisitor fv1 = visitField(ACC_PRIVATE | ACC_FINAL | ACC_SYNTHETIC, "activeObject$thread",
                    "Lorg/jephyr/activeobject/support/ActiveObjectThread;", null, null);
            fv1.visitEnd();

            if (!clinit) {
                MethodVisitor mv = super.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
                mv.visitCode();
                visitClinit(mv);
                mv.visitInsn(RETURN);
                mv.visitMaxs(2, 0);
                mv.visitEnd();
            }

            MethodVisitor mv1 = super.visitMethod(ACC_PROTECTED, "activeObject$getThread",
                    "()Lorg/jephyr/activeobject/support/ActiveObjectThread;", null, null);
            mv1.visitCode();
            mv1.visitVarInsn(ALOAD, 0);
            mv1.visitFieldInsn(GETFIELD, name, "activeObject$thread",
                    "Lorg/jephyr/activeobject/support/ActiveObjectThread;");
            mv1.visitInsn(ARETURN);
            mv1.visitMaxs(1, 1);
            mv1.visitEnd();

            for (MethodNode node : methodNodes) {
                node.accept(super.visitMethod(node.access, node.name, node.desc, node.signature,
                        node.exceptions.toArray(new String[node.exceptions.size()])));
            }
        }

        super.visitEnd();
    }

    private void visitClinit(MethodVisitor mv) {
        mv.visitLdcInsn(mailbox);
        mv.visitInsn(ICONST_0);
        mv.visitTypeInsn(ANEWARRAY, "java/lang/Class");
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getConstructor",
                "([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;", false);
        mv.visitInsn(ICONST_0);
        mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/reflect/Constructor", "newInstance",
                "([Ljava/lang/Object;)Ljava/lang/Object;", false);
        mv.visitTypeInsn(CHECKCAST, "java/util/function/Supplier");
        mv.visitFieldInsn(PUTSTATIC, name, "activeObject$mailboxSupplier", "Ljava/util/function/Supplier;");
    }

    private static void visitPush(MethodVisitor mv, int operand) {
        if (operand <= 5) {
            mv.visitInsn(ICONST_0 + operand);
        } else if (operand <= Byte.MAX_VALUE) {
            mv.visitIntInsn(BIPUSH, operand);
        } else if (operand <= Short.MAX_VALUE) {
            mv.visitIntInsn(SIPUSH, operand);
        } else {
            mv.visitLdcInsn(operand);
        }
    }

    private static void acceptAllBeforeCode(MethodNode methodNode, MethodVisitor mv) {
        if (methodNode.parameters != null) {
            for (ParameterNode node : methodNode.parameters) {
                node.accept(mv);
            }
        }

        if (methodNode.annotationDefault != null) {
            AnnotationVisitor av = mv.visitAnnotationDefault();
            if (av != null) {
                acceptAnnotation(av, null, methodNode.annotationDefault);
                av.visitEnd();
            }
        }

        if (methodNode.visibleAnnotations != null) {
            for (AnnotationNode node : methodNode.visibleAnnotations) {
                node.accept(mv.visitAnnotation(node.desc, true));
            }
        }

        if (methodNode.invisibleAnnotations != null) {
            for (AnnotationNode node : methodNode.invisibleAnnotations) {
                node.accept(mv.visitAnnotation(node.desc, false));
            }
        }

        if (methodNode.visibleTypeAnnotations != null) {
            for (TypeAnnotationNode node : methodNode.visibleTypeAnnotations) {
                node.accept(mv.visitTypeAnnotation(node.typeRef, node.typePath, node.desc, true));
            }
        }

        if (methodNode.invisibleTypeAnnotations != null) {
            for (TypeAnnotationNode node : methodNode.invisibleTypeAnnotations) {
                node.accept(mv.visitTypeAnnotation(node.typeRef, node.typePath, node.desc, false));
            }
        }

        if (methodNode.visibleParameterAnnotations != null) {
            int parameter = 0;
            for (List<AnnotationNode> nodes : methodNode.visibleParameterAnnotations) {
                if (nodes != null) {
                    for (AnnotationNode node : nodes) {
                        node.accept(mv.visitParameterAnnotation(parameter, node.desc, true));
                    }
                }
                parameter++;
            }
        }

        if (methodNode.invisibleParameterAnnotations != null) {
            int parameter = 0;
            for (List<AnnotationNode> nodes : methodNode.invisibleParameterAnnotations) {
                if (nodes != null) {
                    for (AnnotationNode node : nodes) {
                        node.accept(mv.visitParameterAnnotation(parameter, node.desc, false));
                    }
                }
                parameter++;
            }
        }

        if (methodNode.attrs != null) {
            for (Attribute attribute : methodNode.attrs) {
                mv.visitAttribute(attribute);
            }
        }
    }

    private static void acceptAnnotation(AnnotationVisitor av, String name, Object value) {
        if (value instanceof String[]) {
            String[] args = (String[]) value;
            av.visitEnum(name, args[0], args[1]);
        } else if (value instanceof AnnotationNode) {
            AnnotationNode node = (AnnotationNode) value;
            node.accept(av.visitAnnotation(name, node.desc));
        } else if (value instanceof List) {
            AnnotationVisitor av1 = av.visitArray(name);
            if (av1 != null) {
                for (Object value1 : (Iterable<?>) value) {
                    acceptAnnotation(av1, null, value1);
                }
                av1.visitEnd();
            }
        } else {
            av.visit(name, value);
        }
    }

    private static void visitBoxing(MethodVisitor mv, Type type) {
        switch (type.getSort()) {
            case Type.VOID:
                mv.visitInsn(ACONST_NULL);
                break;
            case Type.BOOLEAN:
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                break;
            case Type.CHAR:
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false);
                break;
            case Type.BYTE:
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
                break;
            case Type.SHORT:
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false);
                break;
            case Type.INT:
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                break;
            case Type.FLOAT:
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
                break;
            case Type.LONG:
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
                break;
            case Type.DOUBLE:
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
                break;
        }
    }

    private static void visitUnboxing(MethodVisitor mv, Type type) {
        switch (type.getSort()) {
            case Type.VOID:
                mv.visitInsn(POP);
                break;
            case Type.BOOLEAN:
                mv.visitTypeInsn(CHECKCAST, "java/lang/Boolean");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
                break;
            case Type.CHAR:
                mv.visitTypeInsn(CHECKCAST, "java/lang/Character");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Character", "charValue", "()C", false);
                break;
            case Type.BYTE:
                mv.visitTypeInsn(CHECKCAST, "java/lang/Byte");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Byte", "byteValue", "()B", false);
                break;
            case Type.SHORT:
                mv.visitTypeInsn(CHECKCAST, "java/lang/Short");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Short", "shortValue", "()S", false);
                break;
            case Type.INT:
                mv.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                break;
            case Type.FLOAT:
                mv.visitTypeInsn(CHECKCAST, "java/lang/Float");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false);
                break;
            case Type.LONG:
                mv.visitTypeInsn(CHECKCAST, "java/lang/Long");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
                break;
            case Type.DOUBLE:
                mv.visitTypeInsn(CHECKCAST, "java/lang/Double");
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "valueOf", "()D", false);
                break;
            default:
                String internalName = type.getInternalName();
                if (!internalName.equals("java/lang/Object")) {
                    mv.visitTypeInsn(CHECKCAST, internalName);
                }
        }
    }

    private static void visitFrame(MethodVisitor mv, Type type) {
        switch (type.getSort()) {
            case Type.VOID:
                mv.visitFrame(F_SAME, 0, null, 0, null);
                break;
            case Type.BOOLEAN:
            case Type.CHAR:
            case Type.BYTE:
            case Type.SHORT:
            case Type.INT:
                mv.visitFrame(F_SAME1, 0, null, 1, new Object[] { INTEGER });
                break;
            case Type.FLOAT:
                mv.visitFrame(F_SAME1, 0, null, 1, new Object[] { FLOAT });
                break;
            case Type.LONG:
                mv.visitFrame(F_SAME1, 0, null, 1, new Object[] { LONG });
                break;
            case Type.DOUBLE:
                mv.visitFrame(F_SAME1, 0, null, 1, new Object[] { DOUBLE });
                break;
            default:
                mv.visitFrame(F_SAME1, 0, null, 1, new Object[] { type.getInternalName() });
        }
    }

    public static final class ClassEntry {

        public final String name;
        public final byte[] bytes;

        ClassEntry(String name, byte[] bytes) {
            this.name = name;
            this.bytes = bytes;
        }
    }

    private final class ClinitMethodAdapter extends MethodVisitor {

        ClinitMethodAdapter(MethodVisitor mv) {
            super(ASM5, mv);
        }

        @Override
        public void visitCode() {
            super.visitCode();
            visitClinit(this);
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            super.visitMaxs(Math.max(maxStack, 2), maxLocals);
        }
    }

    private final class InitMethodAdapter extends LocalVariablesSorter {

        AnalyzerAdapter adapter;
        private int maxStack;
        private int maxLocals;
        private int thisVarIndex;
        private Object thisVar = UNINITIALIZED_THIS;

        InitMethodAdapter(int access, String desc, MethodVisitor mv) {
            super(ASM5, access, desc, mv);
        }

        @Override
        public void visitCode() {
            super.visitCode();
            visitVarInsn(ALOAD, 0);
            thisVarIndex = newLocal(Type.getObjectType(name));
            mv.visitVarInsn(ASTORE, thisVarIndex);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            super.visitMethodInsn(opcode, owner, name, desc, itf);
            if (opcode == INVOKESPECIAL && owner.equals(superName) && name.charAt(0) == '<') {
                mv.visitVarInsn(ALOAD, thisVarIndex);
                super.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false);
                visitLdcInsn(Type.getType('L' + ActiveObjectClassAdapter.this.name + ';'));
                Label label = new Label();
                visitJumpInsn(IF_ACMPNE, label);

                visitTypeInsn(NEW, "org/jephyr/activeobject/support/ActiveObjectThread");
                visitInsn(DUP);
                visitFieldInsn(GETSTATIC, ActiveObjectClassAdapter.this.name, "activeObject$mailboxSupplier",
                        "Ljava/util/function/Supplier;");
                super.visitMethodInsn(INVOKEINTERFACE, "java/util/function/Supplier", "get", "()Ljava/lang/Object;",
                        true);
                visitTypeInsn(CHECKCAST, "org/jephyr/activeobject/mailbox/Mailbox");
                super.visitMethodInsn(INVOKESPECIAL, "org/jephyr/activeobject/support/ActiveObjectThread", "<init>",
                        "(Lorg/jephyr/activeobject/mailbox/Mailbox;)V", false);

                int threadVarIndex = nextLocal;
                mv.visitVarInsn(ASTORE, threadVarIndex);

                mv.visitVarInsn(ALOAD, threadVarIndex);

                visitTypeInsn(NEW, "java/lang/StringBuilder");
                visitInsn(DUP);
                visitPush(this, ActiveObjectClassAdapter.this.name.length() + 9);
                super.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(I)V", false);
                visitLdcInsn(ActiveObjectClassAdapter.this.name.replace('/', '.') + '@');
                super.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                        "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                mv.visitVarInsn(ALOAD, thisVarIndex);
                super.visitMethodInsn(INVOKESTATIC, "java/lang/System", "identityHashCode", "(Ljava/lang/Object;)I",
                        false);
                super.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "toHexString", "(I)Ljava/lang/String;", false);
                super.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                        "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                super.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;",
                        false);

                super.visitMethodInsn(INVOKEVIRTUAL, "org/jephyr/activeobject/support/ActiveObjectThread", "setName",
                        "(Ljava/lang/String;)V", false);

                mv.visitVarInsn(ALOAD, threadVarIndex);
                visitInsn(ICONST_1);
                super.visitMethodInsn(INVOKEVIRTUAL, "org/jephyr/activeobject/support/ActiveObjectThread", "setDaemon",
                        "(Z)V", false);

                super.visitMethodInsn(INVOKESTATIC, "org/jephyr/activeobject/support/Disposer", "defaultDisposer",
                        "()Lorg/jephyr/activeobject/support/Disposer;", false);
                mv.visitVarInsn(ALOAD, thisVarIndex);
                mv.visitVarInsn(ALOAD, threadVarIndex);
                super.visitMethodInsn(INVOKEVIRTUAL, "org/jephyr/activeobject/support/Disposer", "register",
                        "(Ljava/lang/Object;Lorg/jephyr/activeobject/support/Disposable;)V", false);

                mv.visitVarInsn(ALOAD, threadVarIndex);
                super.visitMethodInsn(INVOKEVIRTUAL, "org/jephyr/activeobject/support/ActiveObjectThread", "start",
                        "()V", false);

                mv.visitVarInsn(ALOAD, thisVarIndex);
                mv.visitVarInsn(ALOAD, threadVarIndex);
                visitFieldInsn(PUTFIELD, ActiveObjectClassAdapter.this.name, "activeObject$thread",
                        "Lorg/jephyr/activeobject/support/ActiveObjectThread;");

                visitLabel(label);

                thisVar = TOP;

                Object[] locals = convertValues(adapter.locals);
                Object[] stack = convertValues(
                        adapter.stack.subList(0, adapter.stack.size() - (Type.getArgumentsAndReturnSizes(desc) >> 2)));
                visitFrame(F_NEW, locals.length, locals, stack.length, stack);

                maxStack = adapter.stack.size() + 3;
                maxLocals = threadVarIndex + 1;
            }
        }

        private Object[] convertValues(Collection<?> values) {
            Collection<Object> values1 = new ArrayList<>(values.size());
            Iterator<?> iterator = values.iterator();
            while (iterator.hasNext()) {
                Object value = iterator.next();
                if (value == UNINITIALIZED_THIS) {
                    values1.add(name);
                } else {
                    values1.add(value);
                    if (value == LONG || value == DOUBLE) {
                        iterator.next();
                    }
                }
            }
            return values1.toArray();
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            mv.visitMaxs(Math.max(maxStack, this.maxStack), Math.max(nextLocal, this.maxLocals));
        }

        @Override
        protected void updateNewLocals(Object[] newLocals) {
            newLocals[thisVarIndex] = thisVar;
        }
    }

    private final class MethodAdapter extends MethodNode {

        private final MethodVisitor mv;
        private boolean exclude;
        private boolean oneway;
        private long timeout;

        MethodAdapter(int access, String name, String desc, String signature, String[] exceptions, MethodVisitor mv) {
            super(ASM5, access, name, desc, signature, exceptions);
            this.mv = mv;
            exclude = (access & ACC_PUBLIC) == 0 || (access & (ACC_STATIC | ACC_NATIVE | ACC_ABSTRACT)) != 0;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            AnnotationVisitor av = super.visitAnnotation(desc, visible);
            switch (desc) {
                case "Lorg/jephyr/activeobject/annotation/ActiveMethod;":
                    if ((access & (ACC_STATIC | ACC_NATIVE | ACC_ABSTRACT)) != 0) {
                        throw new IllegalStateException(
                                "@ActiveMethod is not allowed on static, native or abstract methods");
                    }
                    exclude = false;
                    return new AnnotationVisitor(ASM5, av) {
                        @Override
                        public void visit(String name, Object value) {
                            if (name.equals("exclude")) {
                                exclude = (boolean) value;
                            }
                            super.visit(name, value);
                        }
                    };
                case "Lorg/jephyr/activeobject/annotation/Oneway;":
                    if ((access & (ACC_STATIC | ACC_NATIVE | ACC_ABSTRACT)) != 0) {
                        throw new IllegalStateException("@Oneway is not allowed on static, native or abstract methods");
                    }
                    oneway = true;
                    return av;
                case "Lorg/jephyr/activeobject/annotation/Timeout;":
                    if ((access & (ACC_STATIC | ACC_NATIVE | ACC_ABSTRACT)) != 0) {
                        throw new IllegalStateException(
                                "@Timeout is not allowed on static, native or abstract methods");
                    }
                    return new AnnotationVisitor(ASM5, av) {
                        @Override
                        public void visit(String name, Object value) {
                            if (name.equals("value")) {
                                timeout = (long) value;
                            }
                            super.visit(name, value);
                        }
                    };
                default:
                    return av;
            }
        }

        @Override
        public void visitEnd() {
            if (exclude) {
                accept(mv);
                return;
            }

            nextTaskNum++;
            int taskNum = nextTaskNum;
            String taskMethodName = "activeObject$" + taskNum;
            String taskClassName = ActiveObjectClassAdapter.this.name + "$$ActiveObject$" + taskNum;

            if (oneway) {
                visitOneway(taskMethodName, taskClassName);
            } else {
                visitRegular(taskMethodName, taskClassName);
            }

            // original method

            access = ACC_FINAL | ACC_SYNTHETIC;
            name = taskMethodName;
            parameters = null;
            annotationDefault = null;
            visibleAnnotations = null;
            invisibleAnnotations = null;
            visibleTypeAnnotations = null;
            invisibleTypeAnnotations = null;
            visibleParameterAnnotations = null;
            invisibleParameterAnnotations = null;
            attrs = null;
            methodNodes.add(this);
        }

        private void visitOneway(String taskMethodName, String taskClassName) {
            // task class

            ClassWriter writer = new ClassWriter(0);

            writer.visit(version, ACC_FINAL | ACC_SUPER | ACC_SYNTHETIC, taskClassName, null, "java/lang/Object",
                    new String[] { "java/lang/Runnable" });

            String desc1 = 'L' + ActiveObjectClassAdapter.this.name + ';';

            writer.visitField(ACC_PRIVATE | ACC_FINAL, "arg$" + 1, desc1, null, null);

            Type[] types = Type.getArgumentTypes(desc);
            int fieldNum = 2;

            for (Type type : types) {
                writer.visitField(ACC_PRIVATE | ACC_FINAL, "arg$" + fieldNum, type.getDescriptor(), null, null);
                fieldNum++;
            }

            // <init>

            StringBuilder sb = new StringBuilder().append('(').append(desc1);

            for (Type type : types) {
                sb.append(type.getDescriptor());
            }

            String initDesc = sb.append(')').append('V').toString();

            MethodVisitor mv1 = writer.visitMethod(0, "<init>", initDesc, null, null);

            mv1.visitCode();

            mv1.visitVarInsn(ALOAD, 0);
            mv1.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);

            mv1.visitVarInsn(ALOAD, 0);
            mv1.visitVarInsn(ALOAD, 1);
            mv1.visitFieldInsn(PUTFIELD, taskClassName, "arg$" + 1, desc1);

            int maxStack1 = 2;
            int varIndex = 2;
            int fieldNum1 = 2;

            for (Type type : types) {
                mv1.visitVarInsn(ALOAD, 0);
                mv1.visitVarInsn(type.getOpcode(ILOAD), varIndex);
                mv1.visitFieldInsn(PUTFIELD, taskClassName, "arg$" + fieldNum1, type.getDescriptor());
                int size = type.getSize();
                int stackSize = size + 1;
                if (maxStack1 < stackSize) {
                    maxStack1 = stackSize;
                }
                varIndex += size;
                fieldNum1++;
            }

            mv1.visitInsn(RETURN);

            mv1.visitMaxs(maxStack1, varIndex);
            mv1.visitEnd();

            // run

            MethodVisitor mv2 = writer.visitMethod(ACC_PUBLIC, "run", "()V", null, null);

            mv2.visitCode();

            Label label = new Label();
            Label label1 = new Label();
            Label label2 = new Label();

            mv2.visitTryCatchBlock(label, label1, label2, "java/lang/Throwable");
            mv2.visitLabel(label);
            mv2.visitVarInsn(ALOAD, 0);

            mv2.visitVarInsn(ALOAD, 0);
            mv2.visitFieldInsn(GETFIELD, taskClassName, "arg$" + 1, desc1);

            int maxStack2 = 3;
            int fieldNum2 = 2;

            for (Type type : types) {
                mv2.visitVarInsn(ALOAD, 0);
                mv2.visitFieldInsn(GETFIELD, taskClassName, "arg$" + fieldNum2, type.getDescriptor());
                maxStack2 += type.getSize() + 1;
                fieldNum2++;
            }

            mv2.visitMethodInsn(INVOKEVIRTUAL, ActiveObjectClassAdapter.this.name, taskMethodName, desc, false);

            mv2.visitLabel(label1);
            mv2.visitInsn(RETURN);

            mv2.visitLabel(label2);
            mv2.visitFrame(F_SAME1, 0, null, 1, new Object[] { "java/lang/Throwable" });
            mv2.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Throwable", "printStackTrace", "()V", false);
            mv2.visitInsn(RETURN);

            mv2.visitMaxs(maxStack2, 1);
            mv2.visitEnd();

            classEntries.add(new ClassEntry(taskClassName, writer.toByteArray()));

            // task method

            acceptAllBeforeCode(this, mv);

            mv.visitCode();
            Label label3 = new Label();
            Label label4 = new Label();
            Label label5 = new Label();
            mv.visitTryCatchBlock(label3, label4, label5, "java/lang/InterruptedException");
            Label label6 = new Label();
            Label label7 = new Label();
            mv.visitTryCatchBlock(label3, label6, label7, null);

            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKEVIRTUAL, ActiveObjectClassAdapter.this.name, "activeObject$getThread",
                    "()Lorg/jephyr/activeobject/support/ActiveObjectThread;", false);
            mv.visitMethodInsn(INVOKEVIRTUAL, "org/jephyr/activeobject/support/ActiveObjectThread", "getMailbox",
                    "()Lorg/jephyr/activeobject/mailbox/Mailbox;", false);
            int mailboxVarIndex = Type.getArgumentsAndReturnSizes(desc) >> 2;
            mv.visitVarInsn(ASTORE, mailboxVarIndex);

            mv.visitTypeInsn(NEW, taskClassName);
            mv.visitInsn(DUP);

            mv.visitVarInsn(ALOAD, 0);

            int maxStack3 = 3;
            int varIndex2 = 1;

            for (Type type : types) {
                mv.visitVarInsn(type.getOpcode(ILOAD), varIndex2);
                int size = type.getSize();
                maxStack3 += size;
                varIndex2 += size;
            }

            mv.visitMethodInsn(INVOKESPECIAL, taskClassName, "<init>", initDesc, false);

            int taskVarIndex = mailboxVarIndex + 1;

            mv.visitVarInsn(ASTORE, taskVarIndex);

            mv.visitInsn(ICONST_0);

            int interruptedVarIndex = taskVarIndex + 1;

            mv.visitVarInsn(ISTORE, interruptedVarIndex);

            mv.visitLabel(label3);
            mv.visitFrame(F_APPEND, 3,
                    new Object[] { "org/jephyr/activeobject/mailbox/Mailbox", "java/lang/Runnable", INTEGER }, 0, null);

            mv.visitVarInsn(ALOAD, mailboxVarIndex);
            mv.visitVarInsn(ALOAD, taskVarIndex);
            mv.visitMethodInsn(INVOKEINTERFACE, "org/jephyr/activeobject/mailbox/Mailbox", "enqueue",
                    "(Ljava/lang/Runnable;)V", true);

            mv.visitLabel(label4);
            mv.visitJumpInsn(GOTO, label6);

            mv.visitLabel(label5);
            mv.visitFrame(F_SAME1, 0, null, 1, new Object[] { "java/lang/InterruptedException" });

            mv.visitInsn(POP);
            mv.visitInsn(ICONST_1);
            mv.visitVarInsn(ISTORE, interruptedVarIndex);
            mv.visitJumpInsn(GOTO, label3);

            mv.visitLabel(label6);
            mv.visitFrame(F_SAME, 0, null, 0, null);

            mv.visitVarInsn(ILOAD, interruptedVarIndex);

            Label l6 = new Label();

            mv.visitJumpInsn(IFEQ, l6);

            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Thread", "interrupt", "()V", false);
            mv.visitJumpInsn(GOTO, l6);

            mv.visitLabel(label7);
            mv.visitFrame(F_SAME1, 0, null, 1, new Object[] { "java/lang/Throwable" });

            mv.visitVarInsn(ILOAD, interruptedVarIndex);

            Label l7 = new Label();

            mv.visitJumpInsn(IFEQ, l7);

            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Thread", "interrupt", "()V", false);

            mv.visitLabel(l7);
            mv.visitFrame(F_SAME1, 0, null, 1, new Object[] { "java/lang/Throwable" });

            mv.visitInsn(ATHROW);

            mv.visitLabel(l6);
            mv.visitFrame(F_SAME, 0, null, 0, null);

            mv.visitInsn(RETURN);

            mv.visitMaxs(maxStack3, interruptedVarIndex + 1);
            mv.visitEnd();
        }

        private void visitRegular(String taskMethodName, String taskClassName) {
            // task class

            ClassWriter writer = new ClassWriter(0);

            writer.visit(version, ACC_FINAL | ACC_SUPER | ACC_SYNTHETIC, taskClassName, null,
                    "org/jephyr/activeobject/support/RunnableFutureTask", null);

            String desc1 = 'L' + ActiveObjectClassAdapter.this.name + ';';

            writer.visitField(ACC_PRIVATE | ACC_FINAL, "arg$" + 1, desc1, null, null);

            Type[] types = Type.getArgumentTypes(desc);
            int fieldNum = 2;

            for (Type type : types) {
                writer.visitField(ACC_PRIVATE | ACC_FINAL, "arg$" + fieldNum, type.getDescriptor(), null, null);
                fieldNum++;
            }

            // <init>

            StringBuilder sb = new StringBuilder().append('(').append(desc1);

            for (Type type : types) {
                sb.append(type.getDescriptor());
            }

            String initDesc = sb.append(')').append('V').toString();

            MethodVisitor mv1 = writer.visitMethod(0, "<init>", initDesc, null, null);

            mv1.visitCode();

            mv1.visitVarInsn(ALOAD, 0);
            mv1.visitMethodInsn(INVOKESPECIAL, "org/jephyr/activeobject/support/RunnableFutureTask", "<init>", "()V",
                    false);

            mv1.visitVarInsn(ALOAD, 0);
            mv1.visitVarInsn(ALOAD, 1);
            mv1.visitFieldInsn(PUTFIELD, taskClassName, "arg$" + 1, desc1);

            int maxStack1 = 2;
            int varIndex = 2;
            int fieldNum1 = 2;

            for (Type type : types) {
                mv1.visitVarInsn(ALOAD, 0);
                mv1.visitVarInsn(type.getOpcode(ILOAD), varIndex);
                mv1.visitFieldInsn(PUTFIELD, taskClassName, "arg$" + fieldNum1, type.getDescriptor());
                int size = type.getSize();
                int stackSize = size + 1;
                if (maxStack1 < stackSize) {
                    maxStack1 = stackSize;
                }
                varIndex += size;
                fieldNum1++;
            }

            mv1.visitInsn(RETURN);

            mv1.visitMaxs(maxStack1, varIndex);
            mv1.visitEnd();

            // run

            MethodVisitor mv2 = writer.visitMethod(ACC_PUBLIC, "run", "()V", null, null);

            mv2.visitCode();

            Label label = new Label();
            Label label1 = new Label();
            Label label2 = new Label();

            mv2.visitTryCatchBlock(label, label1, label2, "java/lang/Throwable");
            mv2.visitLabel(label);
            mv2.visitVarInsn(ALOAD, 0);

            mv2.visitVarInsn(ALOAD, 0);
            mv2.visitFieldInsn(GETFIELD, taskClassName, "arg$" + 1, desc1);

            int maxStack2 = 3;
            int fieldNum2 = 2;

            for (Type type : types) {
                mv2.visitVarInsn(ALOAD, 0);
                mv2.visitFieldInsn(GETFIELD, taskClassName, "arg$" + fieldNum2, type.getDescriptor());
                maxStack2 += type.getSize() + 1;
                fieldNum2++;
            }

            mv2.visitMethodInsn(INVOKEVIRTUAL, ActiveObjectClassAdapter.this.name, taskMethodName, desc, false);

            Type returnType = Type.getReturnType(desc);

            visitBoxing(mv2, returnType);

            mv2.visitMethodInsn(INVOKEVIRTUAL, taskClassName, "set", "(Ljava/lang/Object;)Z", false);
            mv2.visitInsn(POP);
            mv2.visitLabel(label1);
            mv2.visitInsn(RETURN);

            mv2.visitLabel(label2);
            mv2.visitFrame(F_SAME1, 0, null, 1, new Object[] { "java/lang/Throwable" });
            mv2.visitVarInsn(ALOAD, 0);
            mv2.visitInsn(SWAP);
            mv2.visitMethodInsn(INVOKEVIRTUAL, taskClassName, "setException", "(Ljava/lang/Throwable;)Z", false);
            mv2.visitInsn(POP);
            mv2.visitInsn(RETURN);

            mv2.visitMaxs(maxStack2, 1);
            mv2.visitEnd();

            classEntries.add(new ClassEntry(taskClassName, writer.toByteArray()));

            // task method

            acceptAllBeforeCode(this, mv);

            mv.visitCode();

            Label label3 = new Label();
            Label label4 = new Label();
            Label label5 = new Label();
            mv.visitTryCatchBlock(label3, label4, label5, "java/lang/InterruptedException");
            Label label6 = new Label();
            Label label7 = new Label();
            mv.visitTryCatchBlock(label3, label6, label7, null);

            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKEVIRTUAL, ActiveObjectClassAdapter.this.name, "activeObject$getThread",
                    "()Lorg/jephyr/activeobject/support/ActiveObjectThread;", false);
            int threadVarIndex = Type.getArgumentsAndReturnSizes(desc) >> 2;
            mv.visitVarInsn(ASTORE, threadVarIndex);

            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
            mv.visitVarInsn(ALOAD, threadVarIndex);
            Label label8 = new Label();
            mv.visitJumpInsn(IF_ACMPNE, label8);

            mv.visitVarInsn(ALOAD, 0);

            int varIndex1 = 1;

            for (Type type : types) {
                mv.visitVarInsn(type.getOpcode(ILOAD), varIndex1);
                varIndex1 += type.getSize();
            }

            mv.visitMethodInsn(INVOKESPECIAL, ActiveObjectClassAdapter.this.name, taskMethodName, desc, false);
            mv.visitInsn(returnType.getOpcode(IRETURN));

            mv.visitLabel(label8);
            mv.visitFrame(F_APPEND, 1, new Object[] { "org/jephyr/activeobject/support/ActiveObjectThread" }, 0, null);

            mv.visitVarInsn(ALOAD, threadVarIndex);
            mv.visitMethodInsn(INVOKEVIRTUAL, "org/jephyr/activeobject/support/ActiveObjectThread", "getMailbox",
                    "()Lorg/jephyr/activeobject/mailbox/Mailbox;", false);
            int mailboxVarIndex = threadVarIndex + 1;
            mv.visitVarInsn(ASTORE, mailboxVarIndex);

            mv.visitTypeInsn(NEW, taskClassName);
            mv.visitInsn(DUP);
            mv.visitVarInsn(ALOAD, 0);

            int maxStack3 = 3;
            int varIndex2 = 1;

            for (Type type : types) {
                mv.visitVarInsn(type.getOpcode(ILOAD), varIndex2);
                int size = type.getSize();
                maxStack3 += size;
                varIndex2 += size;
            }

            mv.visitMethodInsn(INVOKESPECIAL, taskClassName, "<init>", initDesc, false);
            int taskVarIndex = mailboxVarIndex + 1;
            mv.visitVarInsn(ASTORE, taskVarIndex);

            mv.visitInsn(ICONST_0);
            int interruptedVarIndex = taskVarIndex + 1;
            mv.visitVarInsn(ISTORE, interruptedVarIndex);

            mv.visitLabel(label3);
            mv.visitFrame(F_APPEND, 3,
                    new Object[] { "org/jephyr/activeobject/mailbox/Mailbox", "java/util/concurrent/RunnableFuture",
                            INTEGER }, 0, null);

            mv.visitVarInsn(ALOAD, mailboxVarIndex);
            mv.visitVarInsn(ALOAD, taskVarIndex);
            mv.visitMethodInsn(INVOKEINTERFACE, "org/jephyr/activeobject/mailbox/Mailbox", "enqueue",
                    "(Ljava/lang/Runnable;)V", true);

            mv.visitLabel(label4);
            mv.visitJumpInsn(GOTO, label6);

            mv.visitLabel(label5);
            mv.visitFrame(F_SAME1, 0, null, 1, new Object[] { "java/lang/InterruptedException" });

            mv.visitInsn(POP);
            mv.visitInsn(ICONST_1);
            mv.visitVarInsn(ISTORE, interruptedVarIndex);
            mv.visitJumpInsn(GOTO, label3);

            mv.visitLabel(label6);
            mv.visitFrame(F_SAME, 0, null, 0, null);

            mv.visitVarInsn(ILOAD, interruptedVarIndex);
            Label label9 = new Label();
            mv.visitJumpInsn(IFEQ, label9);

            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Thread", "interrupt", "()V", false);
            mv.visitJumpInsn(GOTO, label9);

            mv.visitLabel(label7);
            mv.visitFrame(F_SAME1, 0, null, 1, new Object[] { "java/lang/Throwable" });

            mv.visitVarInsn(ILOAD, interruptedVarIndex);
            Label label10 = new Label();
            mv.visitJumpInsn(IFEQ, label10);

            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Thread", "interrupt", "()V", false);

            mv.visitLabel(label10);
            mv.visitFrame(F_SAME1, 0, null, 1, new Object[] { "java/lang/Throwable" });

            mv.visitInsn(ATHROW);

            mv.visitLabel(label9);
            mv.visitFrame(F_SAME, 0, null, 0, null);

            if (timeout > 0) {
                Label label11 = new Label();
                Label label12 = new Label();
                Label label13 = new Label();
                mv.visitTryCatchBlock(label11, label12, label13, "java/lang/InterruptedException");
                Label label14 = new Label();
                mv.visitTryCatchBlock(label11, label12, label14, "java/util/concurrent/ExecutionException");
                mv.visitTryCatchBlock(label13, label14, label14, "java/util/concurrent/ExecutionException");
                Label label15 = new Label();
                mv.visitTryCatchBlock(label11, label12, label15, "java/util/concurrent/TimeoutException");
                mv.visitTryCatchBlock(label13, label14, label15, "java/util/concurrent/TimeoutException");
                Label label16 = new Label();
                mv.visitTryCatchBlock(label11, label12, label16, null);
                mv.visitTryCatchBlock(label13, label16, label16, null);

                mv.visitInsn(ICONST_0);
                mv.visitVarInsn(ISTORE, interruptedVarIndex);

                mv.visitLabel(label11);
                mv.visitFrame(F_SAME, 0, null, 0, null);

                mv.visitVarInsn(ALOAD, taskVarIndex);
                mv.visitLdcInsn(timeout);
                mv.visitFieldInsn(GETSTATIC, "java/util/concurrent/TimeUnit", "MILLISECONDS",
                        "Ljava/util/concurrent/TimeUnit;");
                mv.visitMethodInsn(INVOKEINTERFACE, "java/util/concurrent/RunnableFuture", "get",
                        "(JLjava/util/concurrent/TimeUnit;)Ljava/lang/Object;", true);

                visitUnboxing(mv, returnType);

                mv.visitLabel(label12);
                mv.visitVarInsn(ILOAD, interruptedVarIndex);
                Label label17 = new Label();
                mv.visitJumpInsn(IFEQ, label17);

                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Thread", "interrupt", "()V", false);

                mv.visitLabel(label17);

                ActiveObjectClassAdapter.visitFrame(mv, returnType);

                mv.visitInsn(returnType.getOpcode(IRETURN));

                mv.visitLabel(label13);
                mv.visitFrame(F_SAME1, 0, null, 1, new Object[] { "java/lang/InterruptedException" });

                mv.visitInsn(POP);
                mv.visitInsn(ICONST_1);
                mv.visitVarInsn(ISTORE, interruptedVarIndex);
                mv.visitJumpInsn(GOTO, label11);

                mv.visitLabel(label14);
                mv.visitFrame(F_SAME1, 0, null, 1, new Object[] { "java/util/concurrent/ExecutionException" });

                mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/concurrent/ExecutionException", "getCause",
                        "()Ljava/lang/Throwable;", false);
                mv.visitInsn(ATHROW);

                mv.visitLabel(label15);
                mv.visitFrame(F_SAME1, 0, null, 1, new Object[] { "java/util/concurrent/TimeoutException" });

                mv.visitTypeInsn(NEW, "java/lang/IllegalStateException");
                mv.visitInsn(DUP);
                mv.visitInsn(DUP2_X1);
                mv.visitInsn(POP2);
                mv.visitMethodInsn(INVOKESPECIAL, "java/lang/IllegalStateException", "<init>",
                        "(Ljava/lang/Throwable;)V", false);
                mv.visitInsn(ATHROW);

                mv.visitLabel(label16);
                mv.visitFrame(F_SAME1, 0, null, 1, new Object[] { "java/lang/Throwable" });

                mv.visitVarInsn(ILOAD, interruptedVarIndex);
                Label label18 = new Label();
                mv.visitJumpInsn(IFEQ, label18);

                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Thread", "interrupt", "()V", false);

                mv.visitLabel(label18);
                mv.visitFrame(F_SAME1, 0, null, 1, new Object[] { "java/lang/Throwable" });

                mv.visitInsn(ATHROW);

                mv.visitMaxs(Math.max(maxStack3, 5), interruptedVarIndex + 1);
            } else {
                Label label11 = new Label();
                Label label12 = new Label();
                Label label13 = new Label();
                mv.visitTryCatchBlock(label11, label12, label13, "java/lang/InterruptedException");
                Label label14 = new Label();
                mv.visitTryCatchBlock(label11, label12, label14, "java/util/concurrent/ExecutionException");
                mv.visitTryCatchBlock(label13, label14, label14, "java/util/concurrent/ExecutionException");
                Label label15 = new Label();
                mv.visitTryCatchBlock(label11, label12, label15, null);
                mv.visitTryCatchBlock(label13, label15, label15, null);

                mv.visitInsn(ICONST_0);
                mv.visitVarInsn(ISTORE, interruptedVarIndex);

                mv.visitLabel(label11);
                mv.visitFrame(F_SAME, 0, null, 0, null);

                mv.visitVarInsn(ALOAD, taskVarIndex);
                mv.visitMethodInsn(INVOKEINTERFACE, "java/util/concurrent/RunnableFuture", "get",
                        "()Ljava/lang/Object;", true);

                visitUnboxing(mv, returnType);

                mv.visitLabel(label12);
                mv.visitVarInsn(ILOAD, interruptedVarIndex);
                Label label16 = new Label();
                mv.visitJumpInsn(IFEQ, label16);

                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Thread", "interrupt", "()V", false);

                mv.visitLabel(label16);

                ActiveObjectClassAdapter.visitFrame(mv, returnType);

                mv.visitInsn(returnType.getOpcode(IRETURN));

                mv.visitLabel(label13);
                mv.visitFrame(F_SAME1, 0, null, 1, new Object[] { "java/lang/InterruptedException" });

                mv.visitInsn(POP);
                mv.visitInsn(ICONST_1);
                mv.visitVarInsn(ISTORE, interruptedVarIndex);
                mv.visitJumpInsn(GOTO, label11);

                mv.visitLabel(label14);
                mv.visitFrame(F_SAME1, 0, null, 1, new Object[] { "java/util/concurrent/ExecutionException" });

                mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/concurrent/ExecutionException", "getCause",
                        "()Ljava/lang/Throwable;", false);
                mv.visitInsn(ATHROW);

                mv.visitLabel(label15);
                mv.visitFrame(F_SAME1, 0, null, 1, new Object[] { "java/lang/Throwable" });

                mv.visitVarInsn(ILOAD, interruptedVarIndex);
                Label label17 = new Label();
                mv.visitJumpInsn(IFEQ, label17);

                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Thread", "interrupt", "()V", false);

                mv.visitLabel(label17);
                mv.visitFrame(F_SAME1, 0, null, 1, new Object[] { "java/lang/Throwable" });

                mv.visitInsn(ATHROW);

                mv.visitMaxs(maxStack3, interruptedVarIndex + 1);
            }

            mv.visitEnd();
        }
    }
}

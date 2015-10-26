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

package org.jephyr.remapping.instrument;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.signature.SignatureWriter;

import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static org.objectweb.asm.Opcodes.ASM5;

public final class RemappingClassAdapter extends ClassVisitor {

    private final Function<String, String> mapper;

    public RemappingClassAdapter(Function<String, String> mapper, ClassVisitor cv) {
        super(ASM5, cv);
        this.mapper = requireNonNull(mapper);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, map(name), signature == null ? null : mapSignature(signature),
                superName == null ? null : map(superName), interfaces == null ? null : mapNames(interfaces));
    }

    @Override
    public void visitOuterClass(String owner, String name, String desc) {
        super.visitOuterClass(map(owner), name, desc == null ? null : mapMethodDesc(desc));
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        AnnotationVisitor av = super.visitAnnotation(mapDesc(desc), visible);
        return av == null ? null : new AnnotationAdapter(av);
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
        AnnotationVisitor av = super.visitTypeAnnotation(typeRef, typePath, mapDesc(desc), visible);
        return av == null ? null : new AnnotationAdapter(av);
    }

    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        super.visitInnerClass(map(name), outerName == null ? null : map(outerName), innerName, access);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        FieldVisitor fv =
                super.visitField(access, name, mapDesc(desc), signature == null ? null : mapTypeSignature(signature),
                        mapValue(value));
        return fv == null ? null : new FieldAdapter(fv);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor mv =
                super.visitMethod(access, name, mapMethodDesc(desc), signature == null ? null : mapSignature(signature),
                        exceptions == null ? null : mapNames(exceptions));
        return mv == null ? null : (MethodVisitor) new MethodAdapter(mv);
    }

    private String map(String name) {
        String name1 = mapper.apply(name);
        return name1 == null ? name : name1;
    }

    private String mapSignature(String signature) {
        SignatureReader sr = new SignatureReader(signature);
        SignatureWriter sw = new SignatureWriter();
        sr.accept(new SignatureAdapter(sw));
        return sw.toString();
    }

    private String mapTypeSignature(String signature) {
        SignatureReader sr = new SignatureReader(signature);
        SignatureWriter sw = new SignatureWriter();
        sr.acceptType(new SignatureAdapter(sw));
        return sw.toString();
    }

    private String[] mapNames(String[] names) {
        int n = names.length;
        String[] names1 = new String[n];
        for (int i = 0; i < n; i++) {
            names1[i] = map(names[i]);
        }
        return names1;
    }

    public String mapMethodDesc(String desc) {
        StringBuilder sb = new StringBuilder();
        sb.append('(');
        for (Type type : Type.getArgumentTypes(desc)) {
            sb.append(mapDesc(type.getDescriptor()));
        }
        sb.append(')');
        Type returnType = Type.getReturnType(desc);
        if (returnType == Type.VOID_TYPE) {
            sb.append('V');
        } else {
            sb.append(mapDesc(returnType.getDescriptor()));
        }
        return sb.toString();
    }

    public String mapDesc(String desc) {
        Type type = Type.getType(desc);
        switch (type.getSort()) {
            case Type.ARRAY:
                StringBuilder sb = new StringBuilder();
                for (int i = 0, n = type.getDimensions(); i < n; i++) {
                    sb.append('[');
                }
                return sb.append(mapDesc(type.getElementType().getDescriptor())).toString();
            case Type.OBJECT:
                return 'L' + map(type.getInternalName()) + ';';
            default:
                return desc;
        }
    }

    public Object mapValue(Object value) {
        if (value instanceof Type) {
            return mapType((Type) value);
        } else if (value instanceof Handle) {
            Handle handle = (Handle) value;
            return new Handle(handle.getTag(), map(handle.getOwner()), handle.getName(),
                    mapMethodDesc(handle.getDesc()));
        } else {
            return value;
        }
    }

    private Type mapType(Type type) {
        switch (type.getSort()) {
            case Type.ARRAY:
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < type.getDimensions(); ++i) {
                    sb.append('[');
                }
                return Type.getType(sb.append(mapDesc(type.getElementType().getDescriptor())).toString());
            case Type.OBJECT:
                return Type.getObjectType(map(type.getInternalName()));
            case Type.METHOD:
                return Type.getMethodType(mapMethodDesc(type.getDescriptor()));
            default:
                return type;
        }
    }

    private final class FieldAdapter extends FieldVisitor {

        FieldAdapter(FieldVisitor fv) {
            super(ASM5, fv);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            AnnotationVisitor av = super.visitAnnotation(mapDesc(desc), visible);
            return av == null ? null : new AnnotationAdapter(av);
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
            AnnotationVisitor av = super.visitTypeAnnotation(typeRef, typePath, mapDesc(desc), visible);
            return av == null ? null : new AnnotationAdapter(av);
        }
    }

    private final class MethodAdapter extends MethodVisitor {

        MethodAdapter(MethodVisitor mv) {
            super(ASM5, mv);
        }

        @Override
        public AnnotationVisitor visitAnnotationDefault() {
            AnnotationVisitor av = super.visitAnnotationDefault();
            return av == null ? null : new AnnotationAdapter(av);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            AnnotationVisitor av = super.visitAnnotation(mapDesc(desc), visible);
            return av == null ? null : new AnnotationAdapter(av);
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
            AnnotationVisitor av = super.visitTypeAnnotation(typeRef, typePath, mapDesc(desc), visible);
            return av == null ? null : new AnnotationAdapter(av);
        }

        @Override
        public AnnotationVisitor visitParameterAnnotation(int parameter, String desc, boolean visible) {
            AnnotationVisitor av = super.visitParameterAnnotation(parameter, mapDesc(desc), visible);
            return av == null ? null : new AnnotationAdapter(av);
        }

        @Override
        public void visitFrame(int type, int nLocal, Object[] local, int nStack, Object[] stack) {
            super.visitFrame(type, nLocal, mapValues(local, nLocal), nStack, mapValues(stack, nStack));
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            super.visitTypeInsn(opcode, mapName(type));
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            super.visitFieldInsn(opcode, map(owner), name, mapDesc(desc));
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            super.visitMethodInsn(opcode, map(owner), name, mapMethodDesc(desc), itf);
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
            for (int i = 0, n = bsmArgs.length; i < n; i++) {
                bsmArgs[i] = mapValue(bsmArgs[i]);
            }
            super.visitInvokeDynamicInsn(name, mapMethodDesc(desc), (Handle) mapValue(bsm), bsmArgs);
        }

        @Override
        public void visitLdcInsn(Object cst) {
            super.visitLdcInsn(mapValue(cst));
        }

        @Override
        public void visitMultiANewArrayInsn(String desc, int dims) {
            super.visitMultiANewArrayInsn(mapDesc(desc), dims);
        }

        @Override
        public AnnotationVisitor visitInsnAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
            AnnotationVisitor av = super.visitInsnAnnotation(typeRef, typePath, mapDesc(desc), visible);
            return av == null ? null : new AnnotationAdapter(av);
        }

        @Override
        public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
            super.visitTryCatchBlock(start, end, handler, type == null ? null : map(type));
        }

        @Override
        public AnnotationVisitor visitTryCatchAnnotation(int typeRef, TypePath typePath, String desc, boolean visible) {
            AnnotationVisitor av = super.visitTryCatchAnnotation(typeRef, typePath, mapDesc(desc), visible);
            return av == null ? null : new AnnotationAdapter(av);
        }

        @Override
        public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
            super.visitLocalVariable(name, mapDesc(desc), signature == null ? null : mapTypeSignature(signature), start,
                    end, index);
        }

        @Override
        public AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start,
                Label[] end, int[] index, String desc, boolean visible) {
            AnnotationVisitor av =
                    super.visitLocalVariableAnnotation(typeRef, typePath, start, end, index, mapDesc(desc), visible);
            return av == null ? null : new AnnotationAdapter(av);
        }

        private Object[] mapValues(Object[] values, int length) {
            Object[] values1 = new Object[length];
            for (int i = 0; i < length; i++) {
                Object value = values[i];
                values1[i] = value instanceof String ? mapName((String) value) : value;
            }
            return values1;
        }

        private String mapName(String name) {
            return mapType(Type.getObjectType(name)).getInternalName();
        }
    }

    private final class AnnotationAdapter extends AnnotationVisitor {

        AnnotationAdapter(AnnotationVisitor av) {
            super(ASM5, av);
        }

        @Override
        public void visit(String name, Object value) {
            super.visit(name, mapValue(value));
        }

        @Override
        public void visitEnum(String name, String desc, String value) {
            super.visitEnum(name, mapDesc(desc), value);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String name, String desc) {
            AnnotationVisitor av = super.visitAnnotation(name, mapDesc(desc));
            return av == null ? null : new AnnotationAdapter(av);
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            AnnotationVisitor av = super.visitArray(name);
            return av == null ? null : new AnnotationAdapter(av);
        }
    }

    private final class SignatureAdapter extends SignatureVisitor {

        private final SignatureVisitor sv;
        private String name;

        SignatureAdapter(SignatureVisitor sv) {
            super(ASM5);
            this.sv = sv;
        }

        @Override
        public void visitFormalTypeParameter(String name) {
            sv.visitFormalTypeParameter(name);
        }

        @Override
        public SignatureVisitor visitClassBound() {
            sv.visitClassBound();
            return this;
        }

        @Override
        public SignatureVisitor visitInterfaceBound() {
            sv.visitInterfaceBound();
            return this;
        }

        @Override
        public SignatureVisitor visitSuperclass() {
            sv.visitSuperclass();
            return this;
        }

        @Override
        public SignatureVisitor visitInterface() {
            sv.visitInterface();
            return this;
        }

        @Override
        public SignatureVisitor visitParameterType() {
            sv.visitParameterType();
            return this;
        }

        @Override
        public SignatureVisitor visitReturnType() {
            sv.visitReturnType();
            return this;
        }

        @Override
        public SignatureVisitor visitExceptionType() {
            sv.visitExceptionType();
            return this;
        }

        @Override
        public void visitBaseType(char descriptor) {
            sv.visitBaseType(descriptor);
        }

        @Override
        public void visitTypeVariable(String name) {
            sv.visitTypeVariable(name);
        }

        @Override
        public SignatureVisitor visitArrayType() {
            sv.visitArrayType();
            return this;
        }

        @Override
        public void visitClassType(String name) {
            this.name = name;
            sv.visitClassType(map(name));
        }

        @Override
        public void visitInnerClassType(String name) {
            String remappedOuter = map(this.name) + '$';
            this.name += '$' + name;
            String remappedName = map(this.name);
            int index =
                    remappedName.startsWith(remappedOuter) ? remappedOuter.length() : remappedName.lastIndexOf('$') + 1;
            sv.visitInnerClassType(remappedName.substring(index));
        }

        @Override
        public void visitTypeArgument() {
            sv.visitTypeArgument();
        }

        @Override
        public SignatureVisitor visitTypeArgument(char wildcard) {
            sv.visitTypeArgument(wildcard);
            return this;
        }

        @Override
        public void visitEnd() {
            sv.visitEnd();
        }
    }
}

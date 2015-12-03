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

package sun.misc;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Label;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Type;
import sun.security.action.GetBooleanAction;

import static jdk.internal.org.objectweb.asm.Opcodes.AASTORE;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_FINAL;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_STATIC;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_SUPER;
import static jdk.internal.org.objectweb.asm.Opcodes.ACONST_NULL;
import static jdk.internal.org.objectweb.asm.Opcodes.ALOAD;
import static jdk.internal.org.objectweb.asm.Opcodes.ANEWARRAY;
import static jdk.internal.org.objectweb.asm.Opcodes.ARETURN;
import static jdk.internal.org.objectweb.asm.Opcodes.ASTORE;
import static jdk.internal.org.objectweb.asm.Opcodes.ATHROW;
import static jdk.internal.org.objectweb.asm.Opcodes.BIPUSH;
import static jdk.internal.org.objectweb.asm.Opcodes.CHECKCAST;
import static jdk.internal.org.objectweb.asm.Opcodes.DLOAD;
import static jdk.internal.org.objectweb.asm.Opcodes.DRETURN;
import static jdk.internal.org.objectweb.asm.Opcodes.DUP;
import static jdk.internal.org.objectweb.asm.Opcodes.FLOAD;
import static jdk.internal.org.objectweb.asm.Opcodes.FRETURN;
import static jdk.internal.org.objectweb.asm.Opcodes.GETFIELD;
import static jdk.internal.org.objectweb.asm.Opcodes.GETSTATIC;
import static jdk.internal.org.objectweb.asm.Opcodes.ICONST_0;
import static jdk.internal.org.objectweb.asm.Opcodes.ILOAD;
import static jdk.internal.org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static jdk.internal.org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static jdk.internal.org.objectweb.asm.Opcodes.INVOKESTATIC;
import static jdk.internal.org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static jdk.internal.org.objectweb.asm.Opcodes.IRETURN;
import static jdk.internal.org.objectweb.asm.Opcodes.LLOAD;
import static jdk.internal.org.objectweb.asm.Opcodes.LRETURN;
import static jdk.internal.org.objectweb.asm.Opcodes.NEW;
import static jdk.internal.org.objectweb.asm.Opcodes.POP;
import static jdk.internal.org.objectweb.asm.Opcodes.PUTSTATIC;
import static jdk.internal.org.objectweb.asm.Opcodes.RETURN;
import static jdk.internal.org.objectweb.asm.Opcodes.SIPUSH;
import static jdk.internal.org.objectweb.asm.Opcodes.V1_6;

public class ProxyGenerator {
    /*
     * In the comments below, "JVMS" refers to The Java Virtual Machine
     * Specification Second Edition and "JLS" refers to the original
     * version of The Java Language Specification, unless otherwise
     * specified.
     */

    /**
     * name of the superclass of proxy classes
     */
    private static final String SUPERCLASS_NAME = "java/lang/reflect/Proxy";

    /**
     * name of field for storing a proxy instance's invocation handler
     */
    private static final String HANDLER_FIELD_NAME = "h";

    /**
     * debugging flag for saving generated class files
     */
    private static final boolean saveGeneratedFiles =
            AccessController.doPrivileged(new GetBooleanAction("sun.misc.ProxyGenerator.saveGeneratedFiles"));

    /**
     * Generate a public proxy class given a name and a list of proxy interfaces.
     */
    public static byte[] generateProxyClass(String name, Class<?>[] interfaces) {
        return generateProxyClass(name, interfaces, ACC_PUBLIC | ACC_FINAL | ACC_SUPER);
    }

    /**
     * Generate a proxy class given a name and a list of proxy interfaces.
     *
     * @param name        the class name of the proxy class
     * @param interfaces  proxy interfaces
     * @param accessFlags access flags of the proxy class
     */
    public static byte[] generateProxyClass(String name, Class<?>[] interfaces, int accessFlags) {
        ProxyGenerator gen = new ProxyGenerator(name, interfaces, accessFlags);
        byte[] classFile = gen.generateClassFile();

        if (saveGeneratedFiles) {
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                @Override
                public Void run() {
                    try {
                        int i = name.lastIndexOf('.');
                        Path path;
                        if (i > 0) {
                            Path dir = Paths.get(name.substring(0, i).replace('.', File.separatorChar));
                            Files.createDirectories(dir);
                            path = dir.resolve(name.substring(i + 1, name.length()) + ".class");
                        } else {
                            path = Paths.get(name + ".class");
                        }
                        Files.write(path, classFile);
                        return null;
                    } catch (IOException e) {
                        throw new InternalError("I/O exception saving generated file: " + e);
                    }
                }
            });
        }

        return classFile;
    }

    /* preloaded Method objects for methods in java.lang.Object */
    private static final Method hashCodeMethod;
    private static final Method equalsMethod;
    private static final Method toStringMethod;

    static {
        try {
            hashCodeMethod = Object.class.getMethod("hashCode");
            equalsMethod = Object.class.getMethod("equals", Object.class);
            toStringMethod = Object.class.getMethod("toString");
        } catch (NoSuchMethodException e) {
            throw new NoSuchMethodError(e.getMessage());
        }
    }

    /**
     * name of proxy class
     */
    private final String className;

    /**
     * proxy interfaces
     */
    private final Class<?>[] interfaces;

    /**
     * proxy class access flags
     */
    private final int accessFlags;

    /**
     * maps method signature string to list of ProxyMethod objects for
     * proxy methods with that signature
     */
    private final Map<String, List<ProxyMethod>> proxyMethods = new HashMap<>();

    /**
     * count of ProxyMethod objects added to proxyMethods
     */
    private int proxyMethodCount;

    /**
     * Construct a ProxyGenerator to generate a proxy class with the
     * specified name and for the given interfaces.
     * <p>
     * A ProxyGenerator object contains the state for the ongoing
     * generation of a particular proxy class.
     */
    private ProxyGenerator(String className, Class<?>[] interfaces, int accessFlags) {
        this.className = className;
        this.interfaces = interfaces;
        this.accessFlags = accessFlags;
    }

    /**
     * Generate a class file for the proxy class.  This method drives the
     * class file generation process.
     */
    private byte[] generateClassFile() {

        /* ============================================================
         * Step 1: Assemble ProxyMethod objects for all methods to
         * generate proxy dispatching code for.
         */

        /*
         * Record that proxy methods are needed for the hashCode, equals,
         * and toString methods of java.lang.Object.  This is done before
         * the methods from the proxy interfaces so that the methods from
         * java.lang.Object take precedence over duplicate methods in the
         * proxy interfaces.
         */
        addProxyMethod(hashCodeMethod);
        addProxyMethod(equalsMethod);
        addProxyMethod(toStringMethod);

        /*
         * Now record all of the methods from the proxy interfaces, giving
         * earlier interfaces precedence over later ones with duplicate
         * methods.
         */
        for (Class<?> intf : interfaces) {
            for (Method method : intf.getMethods()) {
                addProxyMethod(method);
            }
        }

        /*
         * For each set of proxy methods with the same signature,
         * verify that the methods' return types are compatible.
         */
        for (List<ProxyMethod> sigmethods : proxyMethods.values()) {
            checkReturnTypes(sigmethods);
        }

        /* ============================================================
         * Step 2: Assemble FieldInfo and MethodInfo structs for all of
         * fields and methods in the class we are generating.
         */
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);

        String[] interfaces = new String[this.interfaces.length];
        for (int i = 0, n = this.interfaces.length; i < n; i++) {
            interfaces[i] = dotToSlash(this.interfaces[i].getName());
        }

        cw.visit(V1_6, accessFlags, dotToSlash(className), null, SUPERCLASS_NAME, interfaces);

        generateConstructor(cw);

        for (List<ProxyMethod> sigmethods : proxyMethods.values()) {
            for (ProxyMethod pm : sigmethods) {

                // add static field for method's Method object
                cw.visitField(ACC_PRIVATE | ACC_STATIC, pm.methodFieldName, "Ljava/lang/reflect/Method;", null, null);

                // generate code for proxy method and add it
                generateMethod(pm, cw);
            }
        }

        generateStaticInitializer(cw);

        cw.visitEnd();

        /* ============================================================
         * Step 3: Write the final class file.
         */
        return cw.toByteArray();
    }

    /**
     * Add another method to be proxied, either by creating a new
     * ProxyMethod object or augmenting an old one for a duplicate
     * method.
     * <p>
     * "fromClass" indicates the proxy interface that the method was
     * found through, which may be different from (a subinterface of)
     * the method's "declaring class".  Note that the first Method
     * object passed for a given name and descriptor identifies the
     * Method object (and thus the declaring class) that will be
     * passed to the invocation handler's "invoke" method for a given
     * set of duplicate methods.
     */
    private void addProxyMethod(Method method) {
        Class<?> returnType = method.getReturnType();
        Class<?>[] exceptionTypes = method.getExceptionTypes();

        String sig = method.getName() + Type.getMethodDescriptor(method);
        List<ProxyMethod> sigmethods = proxyMethods.get(sig);
        if (sigmethods != null) {
            for (ProxyMethod pm : sigmethods) {
                if (returnType == pm.method.getReturnType()) {
                    /*
                     * Found a match: reduce exception types to the
                     * greatest set of exceptions that can thrown
                     * compatibly with the throws clauses of both
                     * overridden methods.
                     */
                    List<Class<?>> legalExceptions = new ArrayList<>();
                    collectCompatibleTypes(exceptionTypes, pm.exceptionTypes, legalExceptions);
                    collectCompatibleTypes(pm.exceptionTypes, exceptionTypes, legalExceptions);
                    pm.exceptionTypes = new Class<?>[legalExceptions.size()];
                    pm.exceptionTypes = legalExceptions.toArray(pm.exceptionTypes);
                    return;
                }
            }
        } else {
            sigmethods = new ArrayList<>(3);
            proxyMethods.put(sig, sigmethods);
        }

        sigmethods.add(new ProxyMethod(method, "m" + proxyMethodCount));
        proxyMethodCount++;
    }

    /**
     * For a given set of proxy methods with the same signature, check
     * that their return types are compatible according to the Proxy
     * specification.
     * <p>
     * Specifically, if there is more than one such method, then all
     * of the return types must be reference types, and there must be
     * one return type that is assignable to each of the rest of them.
     */
    private static void checkReturnTypes(List<ProxyMethod> methods) {
        /*
         * If there is only one method with a given signature, there
         * cannot be a conflict.  This is the only case in which a
         * primitive (or void) return type is allowed.
         */
        if (methods.size() < 2) {
            return;
        }

        /*
         * List of return types that are not yet known to be
         * assignable from ("covered" by) any of the others.
         */
        List<Class<?>> uncoveredReturnTypes = new LinkedList<>();

        nextNewReturnType:
        for (ProxyMethod pm : methods) {
            Class<?> newReturnType = pm.method.getReturnType();
            if (newReturnType.isPrimitive()) {
                throw new IllegalArgumentException("methods with same signature " +
                        getFriendlyMethodSignature(pm.method.getName(), pm.method.getParameterTypes()) +
                        " but incompatible return types: " +
                        newReturnType.getName() + " and others");
            }
            boolean added = false;

            /*
             * Compare the new return type to the existing uncovered
             * return types.
             */
            ListIterator<Class<?>> liter = uncoveredReturnTypes.listIterator();
            while (liter.hasNext()) {
                Class<?> uncoveredReturnType = liter.next();

                /*
                 * If an existing uncovered return type is assignable
                 * to this new one, then we can forget the new one.
                 */
                if (newReturnType.isAssignableFrom(uncoveredReturnType)) {
                    assert !added;
                    continue nextNewReturnType;
                }

                /*
                 * If the new return type is assignable to an existing
                 * uncovered one, then should replace the existing one
                 * with the new one (or just forget the existing one,
                 * if the new one has already be put in the list).
                 */
                if (uncoveredReturnType.isAssignableFrom(newReturnType)) {
                    // (we can assume that each return type is unique)
                    if (added) {
                        liter.remove();
                    } else {
                        liter.set(newReturnType);
                        added = true;
                    }
                }
            }

            /*
             * If we got through the list of existing uncovered return
             * types without an assignability relationship, then add
             * the new return type to the list of uncovered ones.
             */
            if (!added) {
                uncoveredReturnTypes.add(newReturnType);
            }
        }

        /*
         * We shouldn't end up with more than one return type that is
         * not assignable from any of the others.
         */
        if (uncoveredReturnTypes.size() > 1) {
            ProxyMethod pm = methods.get(0);
            throw new IllegalArgumentException("methods with same signature " +
                    getFriendlyMethodSignature(pm.method.getName(), pm.method.getParameterTypes()) +
                    " but incompatible return types: " + uncoveredReturnTypes);
        }
    }

    private void generateMethod(ProxyMethod pm, ClassVisitor cv) {
        String desc = Type.getMethodDescriptor(pm.method);

        Class<?>[] exceptionTypes = pm.exceptionTypes;
        String[] exceptions = new String[exceptionTypes.length];
        for (int i = 0, n = exceptionTypes.length; i < n; i++) {
            exceptions[i] = dotToSlash(exceptionTypes[i].getName());
        }

        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC | ACC_FINAL, pm.method.getName(), desc, null, exceptions);
        mv.visitCode();

        Label start = new Label();
        mv.visitLabel(start);

        Class<?>[] parameterTypes = pm.method.getParameterTypes();
        int[] parameterSlot = new int[parameterTypes.length];
        int nextSlot = 1;
        for (int i = 0, n = parameterSlot.length; i < n; i++) {
            parameterSlot[i] = nextSlot;
            nextSlot += parameterTypes[i] == long.class || parameterTypes[i] == double.class ? 2 : 1;
        }

        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, SUPERCLASS_NAME, HANDLER_FIELD_NAME, "Ljava/lang/reflect/InvocationHandler;");
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETSTATIC, dotToSlash(className), pm.methodFieldName, "Ljava/lang/reflect/Method;");

        if (parameterTypes.length > 0) {
            visitPush(mv, parameterTypes.length);

            mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");

            for (int i = 0, n = parameterTypes.length; i < n; i++) {
                mv.visitInsn(DUP);
                visitPush(mv, i);
                codeWrapArgument(parameterTypes[i], parameterSlot[i], mv);
                mv.visitInsn(AASTORE);
            }
        } else {
            mv.visitInsn(ACONST_NULL);
        }

        mv.visitMethodInsn(INVOKEINTERFACE, "java/lang/reflect/InvocationHandler", "invoke",
                "(Ljava/lang/Object;Ljava/lang/reflect/Method;[Ljava/lang/Object;)Ljava/lang/Object;", true);

        Class<?> returnType = pm.method.getReturnType();
        if (returnType == void.class) {
            mv.visitInsn(POP);
            mv.visitInsn(RETURN);
        } else {
            codeUnwrapReturnValue(returnType, mv);
        }

        List<Class<?>> catchList = computeUniqueCatchList(exceptionTypes);
        if (!catchList.isEmpty()) {
            Label end = new Label();
            mv.visitLabel(end);

            Label handler1 = new Label();
            mv.visitLabel(handler1);

            for (Class<?> ex : catchList) {
                mv.visitTryCatchBlock(start, end, handler1, dotToSlash(ex.getName()));
            }

            mv.visitInsn(ATHROW);

            Label handler2 = new Label();
            mv.visitLabel(handler2);

            mv.visitTryCatchBlock(start, end, handler2, "java/lang/Throwable");

            mv.visitVarInsn(ASTORE, nextSlot);
            mv.visitTypeInsn(NEW, "java/lang/reflect/UndeclaredThrowableException");
            mv.visitInsn(DUP);
            mv.visitVarInsn(ALOAD, nextSlot);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/reflect/UndeclaredThrowableException", "<init>",
                    "(Ljava/lang/Throwable;)V", false);
            mv.visitInsn(ATHROW);
        }

        mv.visitMaxs(10, nextSlot + 1);
        mv.visitEnd();
    }

    /**
     * Generate code for wrapping an argument of the given type
     * whose value can be found at the specified local variable
     * index, in order for it to be passed (as an Object) to the
     * invocation handler's "invoke" method.  The code is written
     * to the supplied stream.
     */
    private static void codeWrapArgument(Class<?> type, int slot, MethodVisitor mv) {
        if (type.isPrimitive()) {
            PrimitiveTypeInfo prim = PrimitiveTypeInfo.get(type);

            if (type == int.class || type == boolean.class || type == byte.class || type == char.class ||
                    type == short.class) {
                mv.visitVarInsn(ILOAD, slot);
            } else if (type == long.class) {
                mv.visitVarInsn(LLOAD, slot);
            } else if (type == float.class) {
                mv.visitVarInsn(FLOAD, slot);
            } else if (type == double.class) {
                mv.visitVarInsn(DLOAD, slot);
            } else {
                throw new AssertionError();
            }

            mv.visitMethodInsn(INVOKESTATIC, prim.wrapperClassName, "valueOf", prim.wrapperValueOfDesc, false);
        } else {
            mv.visitVarInsn(ALOAD, slot);
        }
    }

    /**
     * Generate code for unwrapping a return value of the given
     * type from the invocation handler's "invoke" method (as type
     * Object) to its correct type.  The code is written to the
     * supplied stream.
     */
    private static void codeUnwrapReturnValue(Class<?> type, MethodVisitor mv) {
        if (type.isPrimitive()) {
            PrimitiveTypeInfo prim = PrimitiveTypeInfo.get(type);
            mv.visitTypeInsn(CHECKCAST, prim.wrapperClassName);
            mv.visitMethodInsn(INVOKEVIRTUAL, prim.wrapperClassName, prim.unwrapMethodName, prim.unwrapMethodDesc,
                    false);

            if (type == int.class || type == boolean.class || type == byte.class || type == char.class ||
                    type == short.class) {
                mv.visitInsn(IRETURN);
            } else if (type == long.class) {
                mv.visitInsn(LRETURN);
            } else if (type == float.class) {
                mv.visitInsn(FRETURN);
            } else if (type == double.class) {
                mv.visitInsn(DRETURN);
            } else {
                throw new AssertionError();
            }
        } else {
            mv.visitTypeInsn(CHECKCAST, dotToSlash(type.getName()));
            mv.visitInsn(ARETURN);
        }
    }

    /**
     * Generate code for initializing the static field that stores
     * the Method object for this proxy method.  The code is written
     * to the supplied stream.
     */
    private void codeFieldInitialization(ProxyMethod pm, MethodVisitor mv) {
        mv.visitLdcInsn(pm.method.getDeclaringClass().getName());
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;", false);
        mv.visitLdcInsn(pm.method.getName());
        Class<?>[] parameterTypes = pm.method.getParameterTypes();
        visitPush(mv, parameterTypes.length);
        mv.visitTypeInsn(ANEWARRAY, "java/lang/Class");

        for (int i = 0, n = parameterTypes.length; i < n; i++) {
            mv.visitInsn(DUP);
            visitPush(mv, i);

            if (parameterTypes[i].isPrimitive()) {
                PrimitiveTypeInfo prim = PrimitiveTypeInfo.get(parameterTypes[i]);
                mv.visitFieldInsn(GETSTATIC, prim.wrapperClassName, "TYPE", "Ljava/lang/Class;");
            } else {
                mv.visitLdcInsn(parameterTypes[i].getName());
                mv.visitMethodInsn(INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;",
                        false);
            }

            mv.visitInsn(AASTORE);
        }

        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Class", "getMethod",
                "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", false);
        mv.visitFieldInsn(PUTSTATIC, dotToSlash(className), pm.methodFieldName, "Ljava/lang/reflect/Method;");
    }

    /**
     * Generate the constructor method for the proxy class.
     */
    private static void generateConstructor(ClassVisitor cv) {
        MethodVisitor mv = cv.visitMethod(ACC_PUBLIC, "<init>", "(Ljava/lang/reflect/InvocationHandler;)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKESPECIAL, SUPERCLASS_NAME, "<init>", "(Ljava/lang/reflect/InvocationHandler;)V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(10, 2);
        mv.visitEnd();
    }

    /**
     * Generate the static initializer method for the proxy class.
     */
    private void generateStaticInitializer(ClassVisitor cv) {
        MethodVisitor mv = cv.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null);
        mv.visitCode();

        Label start = new Label();
        mv.visitLabel(start);

        for (List<ProxyMethod> sigmethods : proxyMethods.values()) {
            for (ProxyMethod pm : sigmethods) {
                codeFieldInitialization(pm, mv);
            }
        }

        mv.visitInsn(RETURN);

        Label end = new Label();
        mv.visitLabel(end);

        Label handler1 = new Label();
        mv.visitLabel(handler1);

        mv.visitTryCatchBlock(start, end, handler1, "java/lang/NoSuchMethodException");

        mv.visitVarInsn(ASTORE, 1);
        mv.visitTypeInsn(NEW, "java/lang/NoSuchMethodError");
        mv.visitInsn(DUP);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Throwable", "getMessage", "()Ljava/lang/String;", false);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/NoSuchMethodError", "<init>", "(Ljava/lang/String;)V", false);
        mv.visitInsn(ATHROW);

        Label handler2 = new Label();
        mv.visitLabel(handler2);

        mv.visitTryCatchBlock(start, end, handler2, "java/lang/ClassNotFoundException");

        mv.visitVarInsn(ASTORE, 1);
        mv.visitTypeInsn(NEW, "java/lang/NoClassDefFoundError");
        mv.visitInsn(DUP);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Throwable", "getMessage", "()Ljava/lang/String;", false);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/NoClassDefFoundError", "<init>", "(Ljava/lang/String;)V", false);
        mv.visitInsn(ATHROW);

        mv.visitMaxs(10, 2);
        mv.visitEnd();
    }


    /*
     * =============== Code Generation Utility Methods ===============
     */

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

    /*
     * ==================== General Utility Methods ====================
     */

    /**
     * Convert a fully qualified class name that uses '.' as the package
     * separator, the external representation used by the Java language
     * and APIs, to a fully qualified class name that uses '/' as the
     * package separator, the representation used in the class file
     * format (see JVMS section 4.2).
     */
    private static String dotToSlash(String name) {
        return name.replace('.', '/');
    }

    /**
     * Returns a human-readable string representing the signature of a
     * method with the given name and parameter types.
     */
    private static String getFriendlyMethodSignature(String name, Class<?>[] parameterTypes) {
        StringBuilder sig = new StringBuilder(name);
        sig.append('(');
        for (int i = 0, n = parameterTypes.length; i < n; i++) {
            if (i > 0) {
                sig.append(',');
            }
            Class<?> parameterType = parameterTypes[i];
            int dimensions = 0;
            while (parameterType.isArray()) {
                parameterType = parameterType.getComponentType();
                dimensions++;
            }
            sig.append(parameterType.getName());
            while (dimensions-- > 0) {
                sig.append("[]");
            }
        }
        sig.append(')');
        return sig.toString();
    }

    /**
     * Add to the given list all of the types in the "from" array that
     * are not already contained in the list and are assignable to at
     * least one of the types in the "with" array.
     * <p>
     * This method is useful for computing the greatest common set of
     * declared exceptions from duplicate methods inherited from
     * different interfaces.
     */
    private static void collectCompatibleTypes(Class<?>[] from, Class<?>[] with, Collection<Class<?>> list) {
        for (Class<?> fc : from) {
            if (!list.contains(fc)) {
                for (Class<?> wc : with) {
                    if (wc.isAssignableFrom(fc)) {
                        list.add(fc);
                        break;
                    }
                }
            }
        }
    }

    /**
     * Given the exceptions declared in the throws clause of a proxy method,
     * compute the exceptions that need to be caught from the invocation
     * handler's invoke method and rethrown intact in the method's
     * implementation before catching other Throwables and wrapping them
     * in UndeclaredThrowableExceptions.
     * <p>
     * The exceptions to be caught are returned in a List object.  Each
     * exception in the returned list is guaranteed to not be a subclass of
     * any of the other exceptions in the list, so the catch blocks for
     * these exceptions may be generated in any order relative to each other.
     * <p>
     * Error and RuntimeException are each always contained by the returned
     * list (if none of their superclasses are contained), since those
     * unchecked exceptions should always be rethrown intact, and thus their
     * subclasses will never appear in the returned list.
     * <p>
     * The returned List will be empty if java.lang.Throwable is in the
     * given list of declared exceptions, indicating that no exceptions
     * need to be caught.
     */
    private static List<Class<?>> computeUniqueCatchList(Class<?>[] exceptions) {
        List<Class<?>> uniqueList = new ArrayList<>();
        // unique exceptions to catch

        uniqueList.add(Error.class);            // always catch/rethrow these
        uniqueList.add(RuntimeException.class);

        nextException:
        for (Class<?> ex : exceptions) {
            if (ex.isAssignableFrom(Throwable.class)) {
                /*
                 * If Throwable is declared to be thrown by the proxy method,
                 * then no catch blocks are necessary, because the invoke
                 * can, at most, throw Throwable anyway.
                 */
                uniqueList.clear();
                break;
            }

            if (!Throwable.class.isAssignableFrom(ex)) {
                /*
                 * Ignore types that cannot be thrown by the invoke method.
                 */
                continue;
            }
            /*
             * Compare this exception against the current list of
             * exceptions that need to be caught:
             */
            for (int j = 0; j < uniqueList.size(); ) {
                Class<?> ex2 = uniqueList.get(j);
                if (ex2.isAssignableFrom(ex)) {
                    /*
                     * if a superclass of this exception is already on
                     * the list to catch, then ignore this one and continue;
                     */
                    continue nextException;
                } else if (ex.isAssignableFrom(ex2)) {
                    /*
                     * if a subclass of this exception is on the list
                     * to catch, then remove it;
                     */
                    uniqueList.remove(j);
                } else {
                    j++;        // else continue comparing.
                }
            }
            // This exception is unique (so far): add it to the list to catch.
            uniqueList.add(ex);
        }
        return uniqueList;
    }

    /**
     * A ProxyMethod object represents a proxy method in the proxy class
     * being generated: a method whose implementation will encode and
     * dispatch invocations to the proxy instance's invocation handler.
     */
    private static final class ProxyMethod {

        final Method method;
        final String methodFieldName;
        Class<?>[] exceptionTypes;

        ProxyMethod(Method method, String methodFieldName) {
            this.method = method;
            this.methodFieldName = methodFieldName;
            exceptionTypes = method.getExceptionTypes();
        }
    }

    /**
     * A PrimitiveTypeInfo object contains assorted information about
     * a primitive type in its public fields.  The struct for a particular
     * primitive type can be obtained using the static "get" method.
     */
    private static final class PrimitiveTypeInfo {

        /**
         * "base type" used in various descriptors (see JVMS section 4.3.2)
         */
        String baseTypeString;

        /**
         * name of corresponding wrapper class
         */
        String wrapperClassName;

        /**
         * method descriptor for wrapper class "valueOf" factory method
         */
        String wrapperValueOfDesc;

        /**
         * name of wrapper class method for retrieving primitive value
         */
        String unwrapMethodName;

        /**
         * descriptor of same method
         */
        String unwrapMethodDesc;

        private static final Map<Class<?>, PrimitiveTypeInfo> table = new HashMap<>();

        static {
            add(byte.class, Byte.class);
            add(char.class, Character.class);
            add(double.class, Double.class);
            add(float.class, Float.class);
            add(int.class, Integer.class);
            add(long.class, Long.class);
            add(short.class, Short.class);
            add(boolean.class, Boolean.class);
        }

        private static void add(Class<?> primitiveClass, Class<?> wrapperClass) {
            table.put(primitiveClass, new PrimitiveTypeInfo(primitiveClass, wrapperClass));
        }

        private PrimitiveTypeInfo(Class<?> primitiveClass, Class<?> wrapperClass) {
            assert primitiveClass.isPrimitive();

            baseTypeString = Array.newInstance(primitiveClass, 0).getClass().getName().substring(1);
            wrapperClassName = dotToSlash(wrapperClass.getName());
            wrapperValueOfDesc = "(" + baseTypeString + ")L" + wrapperClassName + ";";
            unwrapMethodName = primitiveClass.getName() + "Value";
            unwrapMethodDesc = "()" + baseTypeString;
        }

        static PrimitiveTypeInfo get(Class<?> cl) {
            return table.get(cl);
        }
    }
}

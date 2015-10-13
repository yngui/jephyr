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

package org.jvnet.zephyr.continuation.easyflow;

import org.jvnet.zephyr.continuation.UnsuspendableError;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class ContinuationImpl implements Serializable {

    private static final long serialVersionUID = -1464502970408391110L;

    private static final int SUSPENDED = 1;
    private static final int RESUMED = 2;
    private static final int SUSPENDING = 3;
    private static final int DONE = 4;
    private static final int[] EMPTY_INTS = {};
    private static final float[] EMPTY_FLOATS = {};
    private static final long[] EMPTY_LONGS = {};
    private static final double[] EMPTY_DOUBLES = {};
    private static final Object[] EMPTY_OBJECTS = {};

    private final Runnable target;
    private int state;
    private boolean unsuspendable;
    private int depth;
    private Object obj;
    private Class<?> cls;
    private String name;
    private String desc;
    private transient int[] intStack = EMPTY_INTS;
    private transient int intTop;
    private transient float[] floatStack = EMPTY_FLOATS;
    private transient int floatTop;
    private transient long[] longStack = EMPTY_LONGS;
    private transient int longTop;
    private transient double[] doubleStack = EMPTY_DOUBLES;
    private transient int doubleTop;
    private transient Object[] objectStack = EMPTY_OBJECTS;
    private transient int objectTop;

    ContinuationImpl(Runnable target) {
        this.target = target;
        invocationStarting(target, "run", "()V");
    }

    public static ContinuationImpl currentImpl() {
        return EasyFlowContinuation.currentImpl();
    }

    void suspend() {
        if (unsuspendable || !isStaticInvocationExpected(EasyFlowContinuation.class, "suspend", "()V")) {
            throw new UnsuspendableError("not allowed");
        }
        state = state == SUSPENDED ? RESUMED : SUSPENDING;
    }

    boolean resume() {
        if (state == DONE) {
            throw new IllegalStateException();
        }
        try {
            target.run();
        } catch (Throwable e) {
            state = DONE;
            throw e;
        }
        if (state == SUSPENDING) {
            state = SUSPENDED;
            return true;
        } else {
            state = DONE;
            return false;
        }
    }

    public boolean isSuspending() {
        return state == SUSPENDING;
    }

    public boolean isSuspended() {
        return state == SUSPENDED;
    }

    public void invocationStarting(Object obj, String name, String desc) {
        this.obj = obj;
        cls = null;
        this.name = name;
        this.desc = desc;
    }

    public void staticInvocationStarting(Class<?> cls, String name, String desc) {
        obj = null;
        this.cls = cls;
        this.name = name;
        this.desc = desc;
    }

    public void reflectiveInvocationStarting(Method method, Object obj) {
        if ((method.getModifiers() & Modifier.STATIC) == 0) {
            this.obj = obj;
            cls = null;
        } else {
            this.obj = null;
            cls = method.getDeclaringClass();
        }
        name = method.getName();
        desc = getDescriptor(method);
    }

    private static String getDescriptor(Method method) {
        StringBuffer sb = new StringBuffer().append('(');
        for (Class<?> parameter : method.getParameterTypes()) {
            appendDescriptor(sb, parameter);
        }
        sb.append(')');
        appendDescriptor(sb, method.getReturnType());
        return sb.toString();
    }

    private static void appendDescriptor(StringBuffer sb, Class<?> type) {
        while (true) {
            if (type.isPrimitive()) {
                char c;
                if (type == Void.TYPE) {
                    c = 'V';
                } else if (type == Boolean.TYPE) {
                    c = 'Z';
                } else if (type == Character.TYPE) {
                    c = 'C';
                } else if (type == Byte.TYPE) {
                    c = 'B';
                } else if (type == Short.TYPE) {
                    c = 'S';
                } else if (type == Integer.TYPE) {
                    c = 'I';
                } else if (type == Float.TYPE) {
                    c = 'F';
                } else if (type == Long.TYPE) {
                    c = 'J';
                } else {
                    c = 'D';
                }
                sb.append(c);
                return;
            } else if (type.isArray()) {
                sb.append('[');
                type = type.getComponentType();
            } else {
                sb.append('L');
                String name = type.getName();
                for (int i = 0, n = name.length(); i < n; i++) {
                    char c = name.charAt(i);
                    sb.append(c == '.' ? '/' : c);
                }
                sb.append(';');
                return;
            }
        }
    }

    public void invocationStarted(Object obj, String name, String desc) {
        if (unsuspendable) {
            depth++;
        } else if (!isInvocationExpected(obj, name, desc)) {
            unsuspendable = true;
        }
    }

    private boolean isInvocationExpected(Object obj, String name, String desc) {
        return this.obj == obj && cls == null && name.equals(this.name) && desc.equals(this.desc);
    }

    public void staticInvocationStarted(Class<?> cls, String name, String desc) {
        if (unsuspendable) {
            depth++;
        } else if (!isStaticInvocationExpected(cls, name, desc)) {
            unsuspendable = true;
        }
    }

    private boolean isStaticInvocationExpected(Class<?> cls, String name, String desc) {
        return obj == null && cls.equals(this.cls) && name.equals(this.name) && desc.equals(this.desc);
    }

    public void invocationEnded() {
        if (depth > 0) {
            depth--;
        } else {
            unsuspendable = false;
        }
    }

    public void monitorEntered() {
        if (unsuspendable) {
            depth++;
        } else {
            unsuspendable = true;
        }
    }

    public void monitorExited() {
        if (depth > 0) {
            depth--;
        } else {
            unsuspendable = false;
        }
    }

    public void ensureIntStackSize(int size) {
        int n = intStack.length;
        int n1 = intTop + size;
        if (n < n1) {
            int[] stack = new int[n1];
            System.arraycopy(intStack, 0, stack, 0, n);
            intStack = stack;
        }
    }

    public void ensureFloatStackSize(int size) {
        int n = floatStack.length;
        int n1 = floatTop + size;
        if (n < n1) {
            float[] stack = new float[n1];
            System.arraycopy(floatStack, 0, stack, 0, n);
            floatStack = stack;
        }
    }

    public void ensureLongStackSize(int size) {
        int n = longStack.length;
        int n1 = longTop + size;
        if (n < n1) {
            long[] stack = new long[n1];
            System.arraycopy(longStack, 0, stack, 0, n);
            longStack = stack;
        }
    }

    public void ensureDoubleStackSize(int size) {
        int n = doubleStack.length;
        int n1 = doubleTop + size;
        if (n < n1) {
            double[] stack = new double[n1];
            System.arraycopy(doubleStack, 0, stack, 0, n);
            doubleStack = stack;
        }
    }

    public void ensureObjectStackSize(int size) {
        int n = objectStack.length;
        int n1 = objectTop + size;
        if (n < n1) {
            Object[] stack = new Object[n1];
            System.arraycopy(objectStack, 0, stack, 0, n);
            objectStack = stack;
        }
    }

    public void pushInt(int value) {
        intStack[intTop++] = value;
    }

    public int popInt() {
        return intStack[--intTop];
    }

    public void pushFloat(float value) {
        floatStack[floatTop++] = value;
    }

    public float popFloat() {
        return floatStack[--floatTop];
    }

    public void pushLong(long value) {
        longStack[longTop++] = value;
    }

    public long popLong() {
        return longStack[--longTop];
    }

    public void pushDouble(double value) {
        doubleStack[doubleTop++] = value;
    }

    public double popDouble() {
        return doubleStack[--doubleTop];
    }

    public void pushObject(Object value) {
        objectStack[objectTop++] = value;
    }

    public Object popObject() {
        int index = --objectTop;
        Object value = objectStack[index];
        objectStack[index] = null;
        return value;
    }

    public static Object[] getDefaultArguments(Class<?>[] types) {
        int n = types.length;
        Object[] args = new Object[n];
        for (int i = 0; i < n; i++) {
            Class<?> type = types[i];
            if (type.isPrimitive()) {
                Object arg;
                if (type == Void.TYPE) {
                    arg = null;
                } else if (type == Boolean.TYPE) {
                    arg = false;
                } else if (type == Character.TYPE) {
                    arg = '\0';
                } else if (type == Byte.TYPE) {
                    arg = (byte) 0;
                } else if (type == Short.TYPE) {
                    arg = (short) 0;
                } else if (type == Integer.TYPE) {
                    arg = 0;
                } else if (type == Float.TYPE) {
                    arg = 0.0F;
                } else if (type == Long.TYPE) {
                    arg = 0L;
                } else {
                    arg = 0.0;
                }
                args[i] = arg;
            } else if (type.isArray()) {
                args[i] = null;
            }
        }
        return args;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();

        out.writeInt(intTop);
        for (int i = 0; i < intTop; i++) {
            out.writeInt(intStack[i]);
        }

        out.writeInt(longTop);
        for (int i = 0; i < longTop; i++) {
            out.writeLong(longStack[i]);
        }

        out.writeInt(doubleTop);
        for (int i = 0; i < doubleTop; i++) {
            out.writeDouble(doubleStack[i]);
        }

        out.writeInt(floatTop);
        for (int i = 0; i < floatTop; i++) {
            out.writeDouble(floatStack[i]);
        }

        out.writeInt(objectTop);
        for (int i = 0; i < objectTop; i++) {
            out.writeObject(objectStack[i]);
        }
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();

        intTop = in.readInt();
        intStack = new int[intTop];
        for (int i = 0; i < intTop; i++) {
            intStack[i] = in.readInt();
        }

        longTop = in.readInt();
        longStack = new long[longTop];
        for (int i = 0; i < longTop; i++) {
            longStack[i] = in.readLong();
        }

        doubleTop = in.readInt();
        doubleStack = new double[doubleTop];
        for (int i = 0; i < doubleTop; i++) {
            doubleStack[i] = in.readDouble();
        }

        floatTop = in.readInt();
        floatStack = new float[floatTop];
        for (int i = 0; i < floatTop; i++) {
            floatStack[i] = in.readFloat();
        }

        objectTop = in.readInt();
        objectStack = new Object[objectTop];
        for (int i = 0; i < objectTop; i++) {
            objectStack[i] = in.readObject();
        }
    }
}

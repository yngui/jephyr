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

package org.jvnet.zephyr.easyflow;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import static java.util.Objects.requireNonNull;

public final class Continuation implements Serializable {

    private static final long serialVersionUID = 3809856530567778346L;

    private static final int SUSPENDED = 1;
    private static final int RESUMED = 2;
    private static final int SUSPENDING = 3;
    private static final int DONE = 4;
    private static final int[] EMPTY_INTS = {};
    private static final float[] EMPTY_FLOATS = {};
    private static final long[] EMPTY_LONGS = {};
    private static final double[] EMPTY_DOUBLES = {};
    private static final Object[] EMPTY_OBJECTS = {};

    private static final ThreadLocal<Continuation> currentContinuation = new ThreadLocal<>();

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

    private Continuation(Runnable target) {
        this.target = requireNonNull(target);
        beforeInvocation(target, "run", "()V");
    }

    public static Continuation create(Runnable target) {
        return new Continuation(target);
    }

    public static Continuation currentContinuation() {
        return currentContinuation.get();
    }

    public static void suspend() {
        Continuation continuation = currentContinuation.get();
        if (continuation == null) {
            throw new IllegalStateException();
        }
        if (continuation.unsuspendable ||
                !continuation.isStaticInvocationExpected(Continuation.class, "suspend", "()V")) {
            throw new UnsuspendableError("not allowed");
        }
        continuation.state = continuation.state == SUSPENDED ? RESUMED : SUSPENDING;
    }

    public boolean resume() {
        if (state == DONE) {
            throw new IllegalStateException();
        }
        Continuation continuation = currentContinuation.get();
        currentContinuation.set(this);
        try {
            target.run();
        } catch (Throwable e) {
            state = DONE;
            throw e;
        } finally {
            currentContinuation.set(continuation);
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

    public void beforeInvocation(Object obj, String name, String desc) {
        this.obj = obj;
        cls = null;
        this.name = name;
        this.desc = desc;
    }

    public void beforeStaticInvocation(Class<?> cls, String name, String desc) {
        obj = null;
        this.cls = cls;
        this.name = name;
        this.desc = desc;
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

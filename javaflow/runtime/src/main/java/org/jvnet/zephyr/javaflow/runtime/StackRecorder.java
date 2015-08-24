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

package org.jvnet.zephyr.javaflow.runtime;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import static java.util.Objects.requireNonNull;

public final class StackRecorder implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final int[] EMPTY_INT_STACK = {};
    private static final float[] EMPTY_FLOAT_STACK = {};
    private static final long[] EMPTY_LONG_STACK = {};
    private static final double[] EMPTY_DOUBLE_STACK = {};
    private static final Object[] EMPTY_OBJECT_STACK = {};
    private static final int NEW = 0;
    private static final int SUSPENDED = 1;
    private static final int RESUMED = 2;
    private static final int SUSPENDING = 3;
    private static final int DONE = 4;

    private static final ThreadLocal<StackRecorder> stackRecorder = new ThreadLocal<>();

    private transient int[] intStack = EMPTY_INT_STACK;
    private transient float[] floatStack = EMPTY_FLOAT_STACK;
    private transient long[] longStack = EMPTY_LONG_STACK;
    private transient double[] doubleStack = EMPTY_DOUBLE_STACK;
    private transient Object[] objectStack = EMPTY_OBJECT_STACK;
    private transient int intTop;
    private transient int floatTop;
    private transient int longTop;
    private transient int doubleTop;
    private transient int objectTop;
    private transient int state;
    private transient boolean unsuspendable;
    private transient int depth;

    public StackRecorder(Runnable target) {
        pushObject(requireNonNull(target));
    }

    public static StackRecorder stackRecorder() {
        return stackRecorder.get();
    }

    public static void suspend() {
        StackRecorder stackRecorder = StackRecorder.stackRecorder.get();
        if (stackRecorder == null) {
            throw new IllegalStateException("No continuation is running");
        }
        if (!stackRecorder.unsuspendable) {
            stackRecorder.state = stackRecorder.state == SUSPENDED ? RESUMED : SUSPENDING;
        }
    }

    public void resume() {
        if (state != NEW && state != SUSPENDED) {
            throw new IllegalStateException();
        }
        Runnable target = (Runnable) popObject();
        StackRecorder stackRecorder = StackRecorder.stackRecorder.get();
        StackRecorder.stackRecorder.set(this);
        try {
            target.run();
        } finally {
            StackRecorder.stackRecorder.set(stackRecorder);
            state = state == SUSPENDING ? SUSPENDED : DONE;
        }
    }

    public boolean isDone() {
        return state == DONE;
    }

    public boolean isSuspending() {
        return state == SUSPENDING;
    }

    public boolean isSuspended() {
        return state == SUSPENDED;
    }

    public void setUnsuspendable() {
        if (unsuspendable) {
            depth++;
        } else {
            unsuspendable = true;
        }
    }

    public void resetUnsuspendable() {
        if (depth > 0) {
            depth--;
        } else {
            unsuspendable = false;
        }
    }

    public void pushInt(int value) {
        int length = intStack.length;
        if (length == 0) {
            intStack = new int[8];
        } else if (intTop == length) {
            int[] array = new int[length << 1];
            System.arraycopy(intStack, 0, array, 0, length);
            intStack = array;
        }
        intStack[intTop++] = value;
    }

    public int popInt() {
        int index = intTop - 1;
        int value = intStack[index];
        intTop = index;
        return value;
    }

    public void pushFloat(float value) {
        int length = floatStack.length;
        if (length == 0) {
            floatStack = new float[8];
        } else if (floatTop == length) {
            float[] array = new float[length << 1];
            System.arraycopy(floatStack, 0, array, 0, length);
            floatStack = array;
        }
        floatStack[floatTop++] = value;
    }

    public float popFloat() {
        int index = floatTop - 1;
        float value = floatStack[index];
        floatTop = index;
        return value;
    }

    public void pushLong(long value) {
        int length = longStack.length;
        if (length == 0) {
            longStack = new long[8];
        } else if (longTop == length) {
            long[] array = new long[length << 1];
            System.arraycopy(longStack, 0, array, 0, length);
            longStack = array;
        }
        longStack[longTop++] = value;
    }

    public long popLong() {
        int index = longTop - 1;
        long value = longStack[index];
        longTop = index;
        return value;
    }

    public void pushDouble(double value) {
        int length = doubleStack.length;
        if (length == 0) {
            doubleStack = new double[8];
        } else if (doubleTop == length) {
            double[] array = new double[length << 1];
            System.arraycopy(doubleStack, 0, array, 0, length);
            doubleStack = array;
        }
        doubleStack[doubleTop++] = value;
    }

    public double popDouble() {
        int index = doubleTop - 1;
        double value = doubleStack[index];
        doubleTop = index;
        return value;
    }

    public void pushObject(Object value) {
        int length = objectStack.length;
        if (length == 0) {
            objectStack = new Object[8];
        } else if (objectTop == length) {
            Object[] array = new Object[length << 1];
            System.arraycopy(objectStack, 0, array, 0, length);
            objectStack = array;
        }
        objectStack[objectTop++] = value;
    }

    public Object popObject() {
        int index = objectTop - 1;
        Object value = objectStack[index];
        objectStack[index] = null;
        objectTop = index;
        return value;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();

        if (state != NEW && state != SUSPENDED) {
            throw new IllegalStateException();
        }

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

        out.writeBoolean(state == SUSPENDED);
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

        state = in.readBoolean() ? SUSPENDED : NEW;
    }
}

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

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.DCONST_0;
import static org.objectweb.asm.Opcodes.DLOAD;
import static org.objectweb.asm.Opcodes.DSTORE;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.DUP2_X1;
import static org.objectweb.asm.Opcodes.DUP2_X2;
import static org.objectweb.asm.Opcodes.FCONST_0;
import static org.objectweb.asm.Opcodes.FLOAD;
import static org.objectweb.asm.Opcodes.FSTORE;
import static org.objectweb.asm.Opcodes.F_NEW;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.ISTORE;
import static org.objectweb.asm.Opcodes.LCONST_0;
import static org.objectweb.asm.Opcodes.LLOAD;
import static org.objectweb.asm.Opcodes.LSTORE;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.NOP;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.POP2;
import static org.objectweb.asm.Opcodes.RETURN;

public class NewRelocatorMethodAdapterTest {

    private static final Object[] EMPTY_OBJECTS = new Object[0];

    private MethodVisitor adapter;
    @Mock
    private MethodVisitor mv;
    @Captor
    private ArgumentCaptor<Label> labelCaptor;

    @BeforeMethod
    public void setUp() throws Exception {
        initMocks(this);
        adapter = NewRelocatorMethodAdapter.create("C", ACC_STATIC, "m", "()V", null, null, mv);
    }

    @Test
    public void testVisitEndNop() throws Exception {
        adapter.visitCode();
        adapter.visitFrame(F_NEW, 0, EMPTY_OBJECTS, 0, EMPTY_OBJECTS);
        adapter.visitInsn(NOP);
        adapter.visitFrame(F_NEW, 0, EMPTY_OBJECTS, 0, EMPTY_OBJECTS);
        adapter.visitInsn(RETURN);
        adapter.visitMaxs(0, 0);
        adapter.visitEnd();

        InOrder inOrder = inOrder(mv);
        inOrder.verify(mv).visitCode();
        inOrder.verify(mv).visitFrame(F_NEW, 0, EMPTY_OBJECTS, 0, EMPTY_OBJECTS);
        inOrder.verify(mv).visitInsn(NOP);
        inOrder.verify(mv).visitFrame(F_NEW, 0, EMPTY_OBJECTS, 0, EMPTY_OBJECTS);
        inOrder.verify(mv).visitInsn(RETURN);
        inOrder.verify(mv).visitMaxs(0, 0);
        inOrder.verify(mv).visitEnd();
        verifyNoMoreInteractions(mv);
    }

    @Test
    public void testVisitEndInvokespecialInit() throws Exception {
        adapter.visitCode();
        adapter.visitInsn(ICONST_0);
        adapter.visitTypeInsn(NEW, "C");
        adapter.visitInsn(DUP);
        adapter.visitVarInsn(ASTORE, 0);
        adapter.visitInsn(ICONST_0);
        adapter.visitInsn(FCONST_0);
        adapter.visitInsn(LCONST_0);
        adapter.visitInsn(DCONST_0);
        adapter.visitInsn(ACONST_NULL);
        adapter.visitLdcInsn("test");
        adapter.visitMethodInsn(INVOKESPECIAL, "C", "<init>", "(IFJDLjava/lang/String;[I)V", false);
        adapter.visitInsn(RETURN);
        adapter.visitMaxs(10, 1);
        adapter.visitEnd();

        InOrder inOrder = inOrder(mv);
        inOrder.verify(mv).visitCode();
        inOrder.verify(mv).visitInsn(ICONST_0);
        inOrder.verify(mv).visitLabel(any(Label.class));
        inOrder.verify(mv).visitInsn(ICONST_0);
        inOrder.verify(mv).visitInsn(FCONST_0);
        inOrder.verify(mv).visitInsn(LCONST_0);
        inOrder.verify(mv).visitInsn(DCONST_0);
        inOrder.verify(mv).visitInsn(ACONST_NULL);
        inOrder.verify(mv).visitLdcInsn("test");
        inOrder.verify(mv).visitVarInsn(ASTORE, 2);
        inOrder.verify(mv).visitInsn(POP);
        inOrder.verify(mv).visitVarInsn(DSTORE, 3);
        inOrder.verify(mv).visitVarInsn(LSTORE, 5);
        inOrder.verify(mv).visitVarInsn(FSTORE, 7);
        inOrder.verify(mv).visitVarInsn(ISTORE, 8);
        inOrder.verify(mv).visitTypeInsn(NEW, "C");
        inOrder.verify(mv).visitVarInsn(ASTORE, 1);
        inOrder.verify(mv).visitVarInsn(ALOAD, 1);
        inOrder.verify(mv).visitVarInsn(ASTORE, 0);
        inOrder.verify(mv).visitVarInsn(ALOAD, 1);
        inOrder.verify(mv).visitVarInsn(ILOAD, 8);
        inOrder.verify(mv).visitVarInsn(FLOAD, 7);
        inOrder.verify(mv).visitVarInsn(LLOAD, 5);
        inOrder.verify(mv).visitVarInsn(DLOAD, 3);
        inOrder.verify(mv).visitInsn(ACONST_NULL);
        inOrder.verify(mv).visitVarInsn(ALOAD, 2);
        inOrder.verify(mv).visitMethodInsn(INVOKESPECIAL, "C", "<init>", "(IFJDLjava/lang/String;[I)V", false);
        inOrder.verify(mv).visitInsn(RETURN);
        inOrder.verify(mv).visitMaxs(10, 9);
        inOrder.verify(mv).visitEnd();
        verifyNoMoreInteractions(mv);
    }

    @Test
    public void testVisitEndInvokespecialInitOptimization1() throws Exception {
        adapter.visitCode();
        adapter.visitInsn(ICONST_0);
        adapter.visitTypeInsn(NEW, "C");
        adapter.visitInsn(DUP);
        adapter.visitMethodInsn(INVOKESPECIAL, "C", "<init>", "()V", false);
        adapter.visitInsn(RETURN);
        adapter.visitMaxs(3, 0);
        adapter.visitEnd();

        InOrder inOrder = inOrder(mv);
        inOrder.verify(mv).visitCode();
        inOrder.verify(mv).visitInsn(ICONST_0);
        inOrder.verify(mv).visitLabel(any(Label.class));
        inOrder.verify(mv).visitTypeInsn(NEW, "C");
        inOrder.verify(mv).visitInsn(DUP);
        inOrder.verify(mv).visitMethodInsn(INVOKESPECIAL, "C", "<init>", "()V", false);
        inOrder.verify(mv).visitInsn(RETURN);
        inOrder.verify(mv).visitMaxs(3, 0);
        inOrder.verify(mv).visitEnd();
        verifyNoMoreInteractions(mv);
    }

    @Test
    public void testVisitEndInvokespecialInitOptimization2() throws Exception {
        adapter.visitCode();
        adapter.visitInsn(ICONST_0);
        adapter.visitTypeInsn(NEW, "C");
        adapter.visitInsn(DUP);
        adapter.visitInsn(ICONST_0);
        adapter.visitInsn(LCONST_0);
        adapter.visitInsn(ICONST_0);
        adapter.visitInsn(LCONST_0);
        adapter.visitInsn(FCONST_0);
        adapter.visitInsn(DCONST_0);
        adapter.visitInsn(ACONST_NULL);
        adapter.visitLdcInsn("test");
        adapter.visitMethodInsn(INVOKESPECIAL, "C", "<init>", "(IJIJFDLjava/lang/String;[I)V", false);
        adapter.visitInsn(RETURN);
        adapter.visitMaxs(14, 0);
        adapter.visitEnd();

        InOrder inOrder = inOrder(mv);
        inOrder.verify(mv).visitCode();
        inOrder.verify(mv).visitInsn(ICONST_0);
        inOrder.verify(mv).visitLabel(any(Label.class));
        inOrder.verify(mv).visitInsn(ICONST_0);
        inOrder.verify(mv).visitInsn(LCONST_0);
        inOrder.verify(mv).visitInsn(ICONST_0);
        inOrder.verify(mv).visitInsn(LCONST_0);
        inOrder.verify(mv).visitInsn(FCONST_0);
        inOrder.verify(mv).visitInsn(DCONST_0);
        inOrder.verify(mv).visitInsn(ACONST_NULL);
        inOrder.verify(mv).visitLdcInsn("test");
        inOrder.verify(mv).visitVarInsn(ASTORE, 0);
        inOrder.verify(mv).visitInsn(POP);
        inOrder.verify(mv).visitVarInsn(DSTORE, 1);
        inOrder.verify(mv).visitVarInsn(FSTORE, 3);
        inOrder.verify(mv).visitVarInsn(LSTORE, 4);
        inOrder.verify(mv).visitVarInsn(ISTORE, 6);
        inOrder.verify(mv).visitVarInsn(LSTORE, 7);
        inOrder.verify(mv).visitTypeInsn(NEW, "C");
        inOrder.verify(mv).visitInsn(DUP);
        inOrder.verify(mv).visitInsn(DUP2_X1);
        inOrder.verify(mv).visitInsn(POP2);
        inOrder.verify(mv).visitVarInsn(LLOAD, 7);
        inOrder.verify(mv).visitVarInsn(ILOAD, 6);
        inOrder.verify(mv).visitVarInsn(LLOAD, 4);
        inOrder.verify(mv).visitVarInsn(FLOAD, 3);
        inOrder.verify(mv).visitVarInsn(DLOAD, 1);
        inOrder.verify(mv).visitInsn(ACONST_NULL);
        inOrder.verify(mv).visitVarInsn(ALOAD, 0);
        inOrder.verify(mv).visitMethodInsn(INVOKESPECIAL, "C", "<init>", "(IJIJFDLjava/lang/String;[I)V", false);
        inOrder.verify(mv).visitInsn(RETURN);
        inOrder.verify(mv).visitMaxs(14, 9);
        inOrder.verify(mv).visitEnd();
        verifyNoMoreInteractions(mv);
    }

    @Test
    public void testVisitEndInvokespecialInitOptimization3() throws Exception {
        adapter.visitCode();
        adapter.visitInsn(ICONST_0);
        adapter.visitTypeInsn(NEW, "C");
        adapter.visitInsn(DUP);
        adapter.visitInsn(ICONST_0);
        adapter.visitInsn(FCONST_0);
        adapter.visitInsn(ICONST_0);
        adapter.visitInsn(LCONST_0);
        adapter.visitInsn(FCONST_0);
        adapter.visitInsn(DCONST_0);
        adapter.visitInsn(ACONST_NULL);
        adapter.visitLdcInsn("test");
        adapter.visitMethodInsn(INVOKESPECIAL, "C", "<init>", "(IFIJFDLjava/lang/String;[I)V", false);
        adapter.visitInsn(RETURN);
        adapter.visitMaxs(13, 0);
        adapter.visitEnd();

        InOrder inOrder = inOrder(mv);
        inOrder.verify(mv).visitCode();
        inOrder.verify(mv).visitInsn(ICONST_0);
        inOrder.verify(mv).visitLabel(any(Label.class));
        inOrder.verify(mv).visitInsn(ICONST_0);
        inOrder.verify(mv).visitInsn(FCONST_0);
        inOrder.verify(mv).visitInsn(ICONST_0);
        inOrder.verify(mv).visitInsn(LCONST_0);
        inOrder.verify(mv).visitInsn(FCONST_0);
        inOrder.verify(mv).visitInsn(DCONST_0);
        inOrder.verify(mv).visitInsn(ACONST_NULL);
        inOrder.verify(mv).visitLdcInsn("test");
        inOrder.verify(mv).visitVarInsn(ASTORE, 0);
        inOrder.verify(mv).visitInsn(POP);
        inOrder.verify(mv).visitVarInsn(DSTORE, 1);
        inOrder.verify(mv).visitVarInsn(FSTORE, 3);
        inOrder.verify(mv).visitVarInsn(LSTORE, 4);
        inOrder.verify(mv).visitVarInsn(ISTORE, 6);
        inOrder.verify(mv).visitTypeInsn(NEW, "C");
        inOrder.verify(mv).visitInsn(DUP);
        inOrder.verify(mv).visitInsn(DUP2_X2);
        inOrder.verify(mv).visitInsn(POP2);
        inOrder.verify(mv).visitVarInsn(ILOAD, 6);
        inOrder.verify(mv).visitVarInsn(LLOAD, 4);
        inOrder.verify(mv).visitVarInsn(FLOAD, 3);
        inOrder.verify(mv).visitVarInsn(DLOAD, 1);
        inOrder.verify(mv).visitInsn(ACONST_NULL);
        inOrder.verify(mv).visitVarInsn(ALOAD, 0);
        inOrder.verify(mv).visitMethodInsn(INVOKESPECIAL, "C", "<init>", "(IFIJFDLjava/lang/String;[I)V", false);
        inOrder.verify(mv).visitInsn(RETURN);
        inOrder.verify(mv).visitMaxs(13, 7);
        inOrder.verify(mv).visitEnd();
        verifyNoMoreInteractions(mv);
    }
}

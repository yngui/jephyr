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

package org.jephyr.common.agent;

import java.util.function.Predicate;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import static java.util.Objects.requireNonNull;
import static org.objectweb.asm.Opcodes.ASM5;

public final class ClassNameAwareClassAdapter extends DelegationClassAdapter {

    private final Predicate<String> predicate;
    private final ClassWriter secondary;
    private boolean primary;

    public ClassNameAwareClassAdapter(Predicate<String> predicate, ClassVisitor primary, ClassWriter secondary) {
        super(ASM5, primary);
        this.predicate = requireNonNull(predicate);
        this.secondary = secondary;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        primary = predicate.test(name);
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    protected ClassVisitor delegate() {
        return primary ? cv : secondary;
    }
}

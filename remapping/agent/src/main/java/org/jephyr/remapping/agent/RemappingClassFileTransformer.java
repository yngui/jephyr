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

package org.jephyr.remapping.agent;

import org.jephyr.common.agent.ClassNameAwareClassAdapter;
import org.jephyr.remapping.instrument.RemappingClassAdapter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.objectweb.asm.ClassReader.EXPAND_FRAMES;

final class RemappingClassFileTransformer implements ClassFileTransformer {

    private final Predicate<String> classNamePredicate;
    private final Function<String, String> mapper;

    RemappingClassFileTransformer(Predicate<String> classNamePredicate, Function<String, String> mapper) {
        this.classNamePredicate = classNamePredicate;
        this.mapper = mapper;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        try {
            ClassReader reader = new ClassReader(classfileBuffer);
            ClassWriter writer = new ClassWriter(0);
            reader.accept(new ClassNameAwareClassAdapter(classNamePredicate, new RemappingClassAdapter(mapper, writer),
                    writer), EXPAND_FRAMES);
            return writer.toByteArray();
        } catch (Throwable e) {
            System.err.println("Failed to transform class " + className);
            e.printStackTrace(System.err);
            return null;
        }
    }
}

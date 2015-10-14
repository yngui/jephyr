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

package org.jvnet.zephyr.easyflow.agent;

import org.jvnet.zephyr.common.agent.ClassNameAwareClassAdapter;
import org.jvnet.zephyr.common.util.function.Predicate;
import org.jvnet.zephyr.easyflow.instrument.AnalyzingMethodRefPredicate;
import org.jvnet.zephyr.easyflow.instrument.EasyFlowClassAdapter;
import org.jvnet.zephyr.easyflow.instrument.MethodRef;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.regex.Pattern;

import static org.jvnet.zephyr.common.util.function.Predicates.alwaysTrue;
import static org.objectweb.asm.ClassReader.EXPAND_FRAMES;

final class EasyFlowClassFileTransformer implements ClassFileTransformer {

    private final Predicate<String> classNamePredicate;
    private final Pattern methodRefPattern;

    EasyFlowClassFileTransformer(Predicate<String> classNamePredicate, Pattern methodRefPattern) {
        this.classNamePredicate = classNamePredicate;
        this.methodRefPattern = methodRefPattern;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        try {
            Predicate<MethodRef> methodRefPredicate;
            if (methodRefPattern == null || className == null) {
                methodRefPredicate = alwaysTrue();
            } else {
                methodRefPredicate = new AnalyzingMethodRefPredicate(classfileBuffer,
                        new MethodRefPredicate(methodRefPattern, className));
            }
            ClassReader reader = new ClassReader(classfileBuffer);
            ClassWriter writer = new ClassWriter(0);
            reader.accept(new ClassNameAwareClassAdapter(classNamePredicate,
                    new EasyFlowClassAdapter(methodRefPredicate, writer), writer), EXPAND_FRAMES);
            return writer.toByteArray();
        } catch (Throwable e) {
            System.err.println("Failed to transform class " + className);
            e.printStackTrace(System.err);
            return null;
        }
    }

    private static final class MethodRefPredicate implements Predicate<MethodRef> {

        private final Pattern pattern;
        private final String className;

        MethodRefPredicate(Pattern pattern, String className) {
            this.pattern = pattern;
            this.className = className;
        }

        @Override
        public boolean test(MethodRef t) {
            return pattern.matcher(className + '.' + t.getName() + t.getDesc()).find();
        }
    }
}

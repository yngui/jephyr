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

package org.jvnet.zephyr.javaflow.agent;

import org.jvnet.zephyr.common.agent.ClassNameCheckClassAdapter;
import org.jvnet.zephyr.common.agent.ClassNamePredicate;
import org.jvnet.zephyr.common.util.Predicate;
import org.jvnet.zephyr.javaflow.instrument.ContinuationClassAdapter;
import org.jvnet.zephyr.javaflow.instrument.MethodRef;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Properties;
import java.util.regex.Pattern;

import static org.jvnet.zephyr.common.agent.AgentUtils.parseArgs;
import static org.objectweb.asm.ClassReader.EXPAND_FRAMES;

public final class JavaflowAgent {

    public static void premain(String agentArgs, Instrumentation inst) throws IOException {
        Properties props = parseArgs(agentArgs);
        Predicate<String> classNamePredicate = new ClassNamePredicate(getPattern(props.getProperty("includes")),
                getPattern(props.getProperty("excludes")));
        Predicate<MethodRef> methodRefPredicate =
                new MethodRefPredicate(getPattern(props.getProperty("excludeMethods")));
        inst.addTransformer(new ContinuationClassFileTransformer(classNamePredicate, methodRefPredicate));
    }

    private static Pattern getPattern(String regex) {
        if (regex == null) {
            return null;
        }
        return Pattern.compile(regex);
    }

    private static final class MethodRefPredicate implements Predicate<MethodRef> {

        private final Pattern pattern;

        MethodRefPredicate(Pattern pattern) {
            this.pattern = pattern;
        }

        @Override
        public boolean test(MethodRef t) {
            return pattern == null || pattern.matcher(t.getOwner() + '.' + t.getName() + t.getDesc()).find();
        }
    }

    private static final class ContinuationClassFileTransformer implements ClassFileTransformer {

        private final Predicate<String> classNamePredicate;
        private final Predicate<MethodRef> methodRefPredicate;

        ContinuationClassFileTransformer(Predicate<String> classNamePredicate,
                Predicate<MethodRef> methodRefPredicate) {
            this.classNamePredicate = classNamePredicate;
            this.methodRefPredicate = methodRefPredicate;
        }

        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                ProtectionDomain protectionDomain, byte[] classfileBuffer) {
            ClassWriter writer = new ClassWriter(0);
            ClassReader reader = new ClassReader(classfileBuffer);
            reader.accept(new ClassNameCheckClassAdapter(classNamePredicate,
                    new ContinuationClassAdapter(writer, methodRefPredicate)), EXPAND_FRAMES);
            return writer.toByteArray();
        }
    }
}

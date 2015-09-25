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

package org.jvnet.zephyr.remapping.agent;

import org.jvnet.zephyr.common.agent.ClassNameCheckClassAdapter;
import org.jvnet.zephyr.common.agent.ClassNamePredicate;
import org.jvnet.zephyr.common.util.Predicate;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.RemappingClassAdapter;
import org.objectweb.asm.commons.SimpleRemapper;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

import static org.jvnet.zephyr.common.agent.AgentUtils.parseArgs;
import static org.objectweb.asm.ClassReader.EXPAND_FRAMES;

public final class Agent {

    private static final String KEY_VALUE_DELIM = ":";
    private static final String ENTRY_DELIM = ";";

    public static void premain(String agentArgs, Instrumentation inst) throws IOException {
        Properties props = parseArgs(agentArgs);
        Predicate<String> classNamePredicate = new ClassNamePredicate(getPattern(props.getProperty("includes")),
                getPattern(props.getProperty("excludes")));
        Remapper remapper = new SimpleRemapper(parseMapping(props.getProperty("mapping")));
        inst.addTransformer(new RemappingClassFileTransformer(classNamePredicate, remapper));
    }

    private static Pattern getPattern(String regex) {
        if (regex == null) {
            return null;
        }
        return Pattern.compile(regex);
    }

    private static Map<String, String> parseMapping(String mapping) {
        if (mapping == null) {
            return Collections.emptyMap();
        }

        Map<String, String> map = new HashMap<>();
        int length1 = KEY_VALUE_DELIM.length();
        int length2 = ENTRY_DELIM.length();
        int fromIndex = 0;
        while (true) {
            int index1 = mapping.indexOf(KEY_VALUE_DELIM, fromIndex);
            String key = mapping.substring(fromIndex, index1);
            fromIndex = index1 + length1;
            int index2 = mapping.indexOf(ENTRY_DELIM, fromIndex);
            if (index2 < 0) {
                map.put(key, mapping.substring(fromIndex));
                return map;
            }
            map.put(key, mapping.substring(fromIndex, index2));
            fromIndex = index2 + length2;
        }
    }

    private static final class RemappingClassFileTransformer implements ClassFileTransformer {

        private final Predicate<String> classNamePredicate;
        private final Remapper remapper;

        RemappingClassFileTransformer(Predicate<String> classNamePredicate, Remapper remapper) {
            this.classNamePredicate = classNamePredicate;
            this.remapper = remapper;
        }

        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                ProtectionDomain protectionDomain, byte[] classfileBuffer) {
            ClassWriter writer = new ClassWriter(0);
            ClassReader reader = new ClassReader(classfileBuffer);
            reader.accept(
                    new ClassNameCheckClassAdapter(classNamePredicate, new RemappingClassAdapter(writer, remapper)),
                    EXPAND_FRAMES);
            return writer.toByteArray();
        }
    }
}

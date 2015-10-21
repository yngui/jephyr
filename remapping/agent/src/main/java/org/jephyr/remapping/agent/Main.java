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

import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.SimpleRemapper;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static org.jephyr.common.agent.AgentUtils.parseArgs;

public final class Main {

    private static final String KEY_VALUE_DELIM = ":";
    private static final String ENTRY_DELIM = ";";

    public static void premain(String agentArgs, Instrumentation inst) throws IOException {
        Properties props = parseArgs(agentArgs);
        Pattern includes = getPattern(props.getProperty("includes"));
        Pattern excludes = getPattern(props.getProperty("excludes"));
        Predicate<String> classNamePredicate = t -> (includes == null || includes.matcher(t).find()) &&
                (excludes == null || !excludes.matcher(t).find());
        Remapper remapper = new SimpleRemapper(parseMapping(props.getProperty("mapping")));
        inst.addTransformer(new RemappingClassFileTransformer(classNamePredicate, remapper));
    }

    private static Pattern getPattern(String regex) {
        return regex == null ? null : Pattern.compile(regex);
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
}

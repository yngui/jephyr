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

package org.jephyr.activeobject.agent;

import org.jephyr.common.util.function.Predicate;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.util.Properties;
import java.util.regex.Pattern;

import static org.jephyr.common.agent.AgentUtils.parseArgs;

public final class Main {

    public static void premain(String agentArgs, Instrumentation inst) throws IOException {
        Properties props = parseArgs(agentArgs);
        Pattern includes = getPattern(props.getProperty("includes"));
        Pattern excludes = getPattern(props.getProperty("excludes"));
        Predicate<String> classNamePredicate = new Predicate<String>() {
            @Override
            public boolean test(String t) {
                return (includes == null || includes.matcher(t).find()) &&
                        (excludes == null || !excludes.matcher(t).find());
            }
        };
        inst.addTransformer(new ActiveObjectClassFileTransformer(classNamePredicate, inst));
    }

    private static Pattern getPattern(String regex) {
        return regex == null ? null : Pattern.compile(regex);
    }
}

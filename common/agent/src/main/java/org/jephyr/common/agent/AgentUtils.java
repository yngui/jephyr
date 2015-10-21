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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

public final class AgentUtils {

    private static final Properties DEFAULT_PROPERTIES;
    private static final String KEY_VALUE_DELIM = "=";
    private static final String OPTION_DELIM = ",";

    static {
        DEFAULT_PROPERTIES = new Properties();
        DEFAULT_PROPERTIES.setProperty("excludes", "^(java/|javax/|sun/|com/sun/|jdk/|org/objectweb/asm/)");
    }

    private AgentUtils() {
    }

    public static Properties parseArgs(String args) throws IOException {
        Properties props = new Properties(DEFAULT_PROPERTIES);

        if (args != null && !args.isEmpty()) {
            int length1 = KEY_VALUE_DELIM.length();
            int length2 = OPTION_DELIM.length();
            int fromIndex = 0;
            while (true) {
                int index1 = args.indexOf(KEY_VALUE_DELIM, fromIndex);
                String key = args.substring(fromIndex, index1);
                fromIndex = index1 + length1;
                int index2 = args.indexOf(OPTION_DELIM, fromIndex);
                if (index2 < 0) {
                    props.setProperty(key, args.substring(fromIndex));
                    break;
                }
                props.setProperty(key, args.substring(fromIndex, index2));
                fromIndex = index2 + length2;
            }
        }

        String file = props.getProperty("file");
        if (file != null) {
            URL url;
            if (new File(file).exists()) {
                url = new URL("file", null, -1, file);
            } else {
                url = new URL(file);
            }

            try (InputStream in = url.openStream()) {
                props.load(in);
            }
        }

        return props;
    }
}

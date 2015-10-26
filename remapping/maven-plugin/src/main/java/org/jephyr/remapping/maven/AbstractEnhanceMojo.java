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

package org.jephyr.remapping.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.jephyr.remapping.instrument.RemappingClassAdapter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.apache.commons.io.FileUtils.readFileToByteArray;
import static org.apache.commons.io.FileUtils.writeByteArrayToFile;
import static org.objectweb.asm.ClassReader.EXPAND_FRAMES;

public abstract class AbstractEnhanceMojo extends org.jephyr.common.maven.AbstractEnhanceMojo {

    @Parameter
    private Collection<MappingEntry> mappingEntries;
    private Function<String, String> mapper;

    @Override
    protected final void enhance(File srcFile, File destFile) throws MojoExecutionException {
        if (mapper == null) {
            mapper = createMapper();
        }

        byte[] original;
        try {
            original = readFileToByteArray(srcFile);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to read " + srcFile, e);
        }

        ClassWriter writer = new ClassWriter(0);
        ClassReader reader = new ClassReader(original);
        reader.accept(new RemappingClassAdapter(mapper, writer), EXPAND_FRAMES);
        byte[] enhanced = writer.toByteArray();

        try {
            writeByteArrayToFile(destFile, enhanced);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write " + destFile, e);
        }
    }

    private Function<String, String> createMapper() {
        if (mappingEntries == null) {
            return t -> null;
        }
        Map<String, String> map = new HashMap<>(mappingEntries.size());
        for (MappingEntry mappingEntry : mappingEntries) {
            map.put(mappingEntry.getOldName(), mappingEntry.getNewName());
        }
        return map::get;
    }
}

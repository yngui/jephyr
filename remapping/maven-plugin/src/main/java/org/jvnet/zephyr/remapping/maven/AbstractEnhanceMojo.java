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

package org.jvnet.zephyr.remapping.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.RemappingClassAdapter;
import org.objectweb.asm.commons.SimpleRemapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.apache.commons.io.FileUtils.copyFile;
import static org.apache.commons.io.FileUtils.listFiles;
import static org.apache.commons.io.FileUtils.readFileToByteArray;
import static org.apache.commons.io.FileUtils.writeByteArrayToFile;
import static org.apache.commons.io.FilenameUtils.isExtension;
import static org.codehaus.plexus.util.SelectorUtils.matchPath;
import static org.objectweb.asm.ClassReader.EXPAND_FRAMES;

public abstract class AbstractEnhanceMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;
    @Parameter
    private Set<String> includes;
    @Parameter
    private Set<String> excludes;
    @Parameter
    private Collection<MappingEntry> mappingEntries;

    protected final void execute(File classesDirectory, File outputDirectory) throws MojoExecutionException {
        Path classesPath = classesDirectory.toPath();
        Path outputPath = outputDirectory.toPath();
        SimpleRemapper remapper = new SimpleRemapper(createMapping());

        if (classesDirectory.isDirectory()) {
            for (File srcFile : listFiles(classesDirectory, null, true)) {
                Path relativePath = classesPath.relativize(srcFile.toPath());
                File destFile = outputPath.resolve(relativePath).toFile();
                if (srcFile.equals(destFile) || srcFile.lastModified() > destFile.lastModified()) {
                    String name = relativePath.toString();
                    if (isExtension(name, "class") && isIncluded(name)) {
                        enhance(srcFile, destFile, remapper);
                    } else {
                        copy(srcFile, destFile);
                    }
                }
            }
        }
    }

    private boolean isIncluded(String name) {
        boolean include;
        if (includes == null) {
            include = true;
        } else {
            include = false;
            for (String pattern : includes) {
                include |= matchPath(pattern, name);
            }
        }
        if (include && excludes != null) {
            for (String pattern : excludes) {
                include &= !matchPath(pattern, name);
            }
        }
        return include;
    }

    private static void enhance(File srcFile, File destFile, Remapper remapper) throws MojoExecutionException {
        byte[] original;
        try {
            original = readFileToByteArray(srcFile);
        } catch (IOException e) {
            throw new MojoExecutionException("An error occurred while reading " + srcFile, e);
        }

        ClassWriter writer = new ClassWriter(0);
        ClassReader reader = new ClassReader(original);
        reader.accept(new RemappingClassAdapter(writer, remapper), EXPAND_FRAMES);
        byte[] enhanced = writer.toByteArray();

        try {
            writeByteArrayToFile(destFile, enhanced);
        } catch (IOException e) {
            throw new MojoExecutionException("An error occurred while writing " + destFile, e);
        }
    }

    private static void copy(File srcFile, File destFile) throws MojoExecutionException {
        if (!srcFile.equals(destFile)) {
            try {
                copyFile(srcFile, destFile);
            } catch (IOException e) {
                throw new MojoExecutionException("An error occurred while copying " + srcFile + " to " + destFile, e);
            }
        }
    }

    private Map<String, String> createMapping() {
        if (mappingEntries == null) {
            return Collections.emptyMap();
        }
        Map<String, String> mapping = new HashMap<>(mappingEntries.size());
        for (MappingEntry mappingEntry : mappingEntries) {
            mapping.put(mappingEntry.getOldName(), mappingEntry.getNewName());
        }
        return mapping;
    }
}

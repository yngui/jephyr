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
        if (classesDirectory.equals(outputDirectory)) {
            throw new MojoExecutionException(
                    "Classes directory " + classesDirectory + " and output directory " + outputDirectory +
                            " are the same");
        }

        Path outputPath = outputDirectory.toPath();
        Path classesPath = classesDirectory.toPath();
        SimpleRemapper remapper = new SimpleRemapper(createMapping());

        if (classesDirectory.isDirectory()) {
            for (File file : listFiles(classesDirectory, null, true)) {
                Path relativePath = classesPath.relativize(file.toPath());
                File destination = outputPath.resolve(relativePath).toFile();
                if (file.lastModified() > destination.lastModified()) {
                    String name = relativePath.toString();
                    if (isExtension(name, "class") && isIncluded(name)) {
                        transform(remapper, file, destination);
                    } else {
                        copy(file, destination);
                    }
                }
            }
        }
    }

    private boolean isIncluded(String name) {
        boolean include;
        if (includes == null) {
            include = matchPath("**/**", name);
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

    private static void transform(Remapper remapper, File file, File destination) throws MojoExecutionException {
        byte[] original;
        try {
            original = readFileToByteArray(file);
        } catch (IOException e) {
            throw new MojoExecutionException("An error occurred while reading " + file, e);
        }

        ClassWriter writer = new ClassWriter(0);
        ClassReader reader = new ClassReader(original);
        reader.accept(new RemappingClassAdapter(writer, remapper), EXPAND_FRAMES);
        byte[] transformed = writer.toByteArray();

        try {
            writeByteArrayToFile(destination, transformed);
        } catch (IOException e) {
            throw new MojoExecutionException("An error occurred while writing " + destination, e);
        }
    }

    private static void copy(File file, File destination) throws MojoExecutionException {
        try {
            copyFile(file, destination);
        } catch (IOException e) {
            throw new MojoExecutionException("An error occurred while copying " + file + " to " + destination, e);
        }
    }

    private Map<String, String> createMapping() {
        Map<String, String> mapping = new HashMap<>(mappingEntries.size());
        for (MappingEntry mappingEntry : mappingEntries) {
            mapping.put(mappingEntry.getOldName(), mappingEntry.getNewName());
        }
        return mapping;
    }
}

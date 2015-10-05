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

package org.jvnet.zephyr.easyflow.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.jvnet.zephyr.common.util.Predicate;
import org.jvnet.zephyr.easyflow.instrument.AnalyzingMethodRefPredicate;
import org.jvnet.zephyr.easyflow.instrument.EasyFlowClassAdapter;
import org.jvnet.zephyr.easyflow.instrument.MethodRef;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;

import static org.apache.commons.io.FileUtils.copyFile;
import static org.apache.commons.io.FileUtils.listFiles;
import static org.apache.commons.io.FileUtils.readFileToByteArray;
import static org.apache.commons.io.FileUtils.writeByteArrayToFile;
import static org.apache.commons.io.FilenameUtils.isExtension;
import static org.apache.commons.io.FilenameUtils.removeExtension;
import static org.apache.commons.io.FilenameUtils.separatorsToUnix;
import static org.codehaus.plexus.util.SelectorUtils.matchPath;
import static org.jvnet.zephyr.common.util.Predicates.alwaysTrue;
import static org.objectweb.asm.ClassReader.EXPAND_FRAMES;

public abstract class AbstractEnhanceMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;
    @Parameter
    private Set<String> includes;
    @Parameter
    private Set<String> excludes;
    @Parameter
    private Collection<String> excludedMethods;

    protected final void execute(File classesDirectory, File outputDirectory) throws MojoExecutionException {
        Path classesPath = classesDirectory.toPath();
        Path outputPath = outputDirectory.toPath();

        if (classesDirectory.isDirectory()) {
            for (File srcFile : listFiles(classesDirectory, null, true)) {
                Path relativePath = classesPath.relativize(srcFile.toPath());
                File destFile = outputPath.resolve(relativePath).toFile();
                if (srcFile.equals(destFile) || srcFile.lastModified() > destFile.lastModified()) {
                    String name = relativePath.toString();
                    if (isExtension(name, "class") && isIncluded(name)) {
                        enhance(srcFile, destFile, separatorsToUnix(removeExtension(name)));
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

    private void enhance(File srcFile, File destFile, final String className) throws MojoExecutionException {
        byte[] original;
        try {
            original = readFileToByteArray(srcFile);
        } catch (IOException e) {
            throw new MojoExecutionException("An error occurred while reading " + srcFile, e);
        }

        Predicate<MethodRef> methodRefPredicate;
        if (excludedMethods == null) {
            methodRefPredicate = alwaysTrue();
        } else {
            methodRefPredicate = new AnalyzingMethodRefPredicate(original, new Predicate<MethodRef>() {
                @Override
                public boolean test(MethodRef t) {
                    return !excludedMethods.contains(className + '.' + t.getName() + t.getDesc());
                }
            });
        }

        ClassWriter writer = new ClassWriter(0);
        ClassReader reader = new ClassReader(original);
        reader.accept(new EasyFlowClassAdapter(methodRefPredicate, writer), EXPAND_FRAMES);
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
}

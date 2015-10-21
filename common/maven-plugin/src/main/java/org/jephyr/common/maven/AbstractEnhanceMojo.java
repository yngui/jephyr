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

package org.jephyr.common.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

import static org.apache.commons.io.FileUtils.copyFile;
import static org.apache.commons.io.FileUtils.listFiles;
import static org.apache.commons.io.FilenameUtils.isExtension;
import static org.apache.commons.io.FilenameUtils.separatorsToUnix;
import static org.codehaus.plexus.util.SelectorUtils.matchPath;

public abstract class AbstractEnhanceMojo extends AbstractMojo {

    @Parameter
    private Set<String> includes;
    @Parameter
    private Set<String> excludes;

    @Override
    public final void execute() throws MojoExecutionException {
        File classesDirectory = getClassesDirectory();
        if (!classesDirectory.isDirectory()) {
            return;
        }

        Path classesPath = classesDirectory.toPath();
        Path outputPath = getOutputDirectory().toPath();

        for (File srcFile : listFiles(classesDirectory, null, true)) {
            Path relativePath = classesPath.relativize(srcFile.toPath());
            File destFile = outputPath.resolve(relativePath).toFile();
            if (srcFile.equals(destFile)) {
                if (shouldEnhance(separatorsToUnix(relativePath.toString()))) {
                    enhance(srcFile, destFile);
                }
            } else if (srcFile.lastModified() > destFile.lastModified()) {
                if (shouldEnhance(separatorsToUnix(relativePath.toString()))) {
                    enhance(srcFile, destFile);
                } else {
                    try {
                        copyFile(srcFile, destFile);
                    } catch (IOException e) {
                        throw new MojoExecutionException("Failed to copy " + srcFile + " to " + destFile, e);
                    }
                }
            }
        }
    }

    private boolean shouldEnhance(String name) {
        if (!isExtension(name, "class")) {
            return false;
        }
        boolean include;
        if (includes == null) {
            include = true;
        } else {
            include = false;
            for (String pattern : includes) {
                include |= matchPath(pattern, name, "/", true);
            }
        }
        if (include && excludes != null) {
            for (String pattern : excludes) {
                include &= !matchPath(pattern, name, "/", true);
            }
        }
        return include;
    }

    protected abstract File getClassesDirectory();

    protected abstract File getOutputDirectory();

    protected abstract void enhance(File srcFile, File destFile) throws MojoExecutionException;
}

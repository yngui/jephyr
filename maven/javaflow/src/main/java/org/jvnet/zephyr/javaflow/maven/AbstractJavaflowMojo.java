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

package org.jvnet.zephyr.javaflow.maven;

import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.jvnet.zephyr.javaflow.instrument.AnalazingMethodRefPredicate;
import org.jvnet.zephyr.javaflow.instrument.AsmClassTransformer;
import org.jvnet.zephyr.javaflow.instrument.MethodRef;
import org.jvnet.zephyr.javaflow.instrument.Predicate;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.apache.commons.io.FileUtils.listFiles;
import static org.apache.commons.io.FileUtils.readFileToByteArray;
import static org.apache.commons.io.FileUtils.writeByteArrayToFile;
import static org.apache.commons.io.FilenameUtils.isExtension;
import static org.codehaus.plexus.util.SelectorUtils.matchPath;

public abstract class AbstractJavaflowMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;
    @Parameter
    private Set<String> includes = Collections.singleton("**/**");
    @Parameter
    private Set<String> excludes = Collections.emptySet();
    @Parameter
    private Collection<String> excludedMethods = Collections.emptySet();

    @SuppressWarnings("unchecked")
    protected final void execute(File classesDirectory, File outputDirectory) throws MojoExecutionException {
        if (classesDirectory.equals(outputDirectory)) {
            throw new MojoExecutionException(
                    "Classes directory " + classesDirectory + " and output directory " + outputDirectory +
                            " are the same");
        }

        List<String> classpathElements;
        try {
            classpathElements = project.getCompileClasspathElements();
        } catch (DependencyResolutionRequiredException e) {
            throw new MojoExecutionException("An error occurred while getting compile classpath elements", e);
        }

        List<URL> urls = new ArrayList<>(classpathElements.size());

        urls.add(toURL(classesDirectory));

        for (String element : classpathElements) {
            urls.add(toURL(new File(element)));
        }

        Thread thread = Thread.currentThread();
        ClassLoader loader = new URLClassLoader(urls.toArray(new URL[urls.size()]), thread.getContextClassLoader());
        thread.setContextClassLoader(loader);

        Path outputPath = outputDirectory.toPath();
        Path classesPath = classesDirectory.toPath();
        AsmClassTransformer transformer = new AsmClassTransformer(
                new AnalazingMethodRefPredicate(new Predicate<MethodRef>() {
                    @Override
                    public boolean apply(MethodRef input) {
                        return !excludedMethods.contains(input.getOwner() + '.' + input.getName() + input.getDesc());
                    }
                }, Thread.currentThread().getContextClassLoader()));

        if (classesDirectory.isDirectory()) {
            for (File file : listFiles(classesDirectory, null, true)) {
                Path relativePath = classesPath.relativize(file.toPath());
                File destination = outputPath.resolve(relativePath).toFile();
                if (file.lastModified() > destination.lastModified()) {
                    String name = relativePath.toString();
                    if (isExtension(name, "class") && isIncluded(name)) {
                        transform(transformer, file, destination);
                    } else {
                        copy(file, destination);
                    }
                }
            }
        }
    }

    private static URL toURL(File file) throws MojoExecutionException {
        URI uri = file.toURI();
        URL url;
        try {
            url = uri.toURL();
        } catch (MalformedURLException e) {
            throw new MojoExecutionException("An error occurred while constructing the URL from " + uri, e);
        }
        return url;
    }

    private boolean isIncluded(String name) {
        boolean include = false;
        for (String pattern : includes) {
            include |= matchPath(pattern, name);
        }
        if (include) {
            for (String pattern : excludes) {
                include &= !matchPath(pattern, name);
            }
        }
        return include;
    }

    private static void transform(AsmClassTransformer transformer, File file, File destination)
            throws MojoExecutionException {
        byte[] original;
        try {
            original = readFileToByteArray(file);
        } catch (IOException e) {
            throw new MojoExecutionException("An error occurred while reading " + file, e);
        }
        byte[] transformed = transformer.transform(original);
        try {
            writeByteArrayToFile(destination, transformed);
        } catch (IOException e) {
            throw new MojoExecutionException("An error occurred while writing " + destination, e);
        }
    }

    private static void copy(File file, File destination) throws MojoExecutionException {
        try {
            FileUtils.copyFile(file, destination);
        } catch (IOException e) {
            throw new MojoExecutionException("An error occurred while copying " + file + " to " + destination, e);
        }
    }
}

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

import java.io.File;
import java.io.FileOutputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.security.ProtectionDomain;
import java.util.Collection;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.jephyr.activeobject.instrument.ActiveObjectClassAdapter;
import org.jephyr.activeobject.instrument.ActiveObjectClassAdapter.ClassEntry;
import org.jephyr.common.agent.ClassNameAwareClassAdapter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

final class ActiveObjectClassFileTransformer implements ClassFileTransformer {

    private final Predicate<String> classNamePredicate;
    private final Instrumentation instrumentation;

    ActiveObjectClassFileTransformer(Predicate<String> classNamePredicate, Instrumentation instrumentation) {
        this.classNamePredicate = classNamePredicate;
        this.instrumentation = instrumentation;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        try {
            ClassWriter writer = new ClassWriter(0);
            ClassReader reader = new ClassReader(classfileBuffer);
            ActiveObjectClassAdapter cv = new ActiveObjectClassAdapter(writer);
            reader.accept(new ClassNameAwareClassAdapter(classNamePredicate, cv, writer), 0);

            Collection<ClassEntry> classEntries = cv.classEntries;
            if (classEntries != null && !classEntries.isEmpty()) {
                File file = Files.createTempFile(null, null).toFile();
                file.deleteOnExit();
                Manifest manifest = new Manifest();
                manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

                try (JarOutputStream out = new JarOutputStream(new FileOutputStream(file), manifest)) {
                    for (ClassEntry entry : classEntries) {
                        JarEntry jarEntry = new JarEntry(entry.name + ".class");
                        out.putNextEntry(jarEntry);
                        out.write(entry.bytes, 0, entry.bytes.length);
                        out.closeEntry();
                    }
                }

                instrumentation.appendToSystemClassLoaderSearch(new JarFile(file));
            }

            return writer.toByteArray();
        } catch (Throwable e) {
            System.err.println("Failed to transform class " + className);
            e.printStackTrace(System.err);
            return null;
        }
    }
}

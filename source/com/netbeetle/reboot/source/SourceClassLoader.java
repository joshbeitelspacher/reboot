/*
 * Copyright 2011-2012 Josh Beitelspacher
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netbeetle.reboot.source;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicLong;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import com.netbeetle.reboot.core.Reboot;
import com.netbeetle.reboot.core.RebootByteFile;
import com.netbeetle.reboot.core.RebootClassLoader;
import com.netbeetle.reboot.core.RebootClassLoaderContext;
import com.netbeetle.reboot.core.RebootDirectory;
import com.netbeetle.reboot.core.RebootFile;

public class SourceClassLoader extends RebootClassLoader
{
    private static final AtomicLong COMPILATION_COUNT = new AtomicLong(0);
    private static final String NEW_LINE = String.format("%n");
    private static final String SINGLE_INDENT = NEW_LINE + "  ";
    private static final String DOUBLE_INDENT = SINGLE_INDENT + "  ";

    private class CompiledDirectory extends RebootDirectory
    {
        private final RebootFile file;
        private final boolean compile;

        private CompiledDirectory(String name, RebootFile file, boolean compile)
        {
            super(name);
            this.file = file;
            this.compile = compile;
        }

        @Override
        public Collection<RebootFile> list(boolean recursive) throws IOException
        {
            List<RebootFile> filesToCompile = new ArrayList<RebootFile>();
            List<RebootFile> contents = new ArrayList<RebootFile>();
            for (RebootFile oldFile : file.list(recursive))
            {
                if (oldFile.isDirectory())
                {
                    contents.add(new CompiledDirectory(oldFile.getName(), oldFile, compile));
                }
                if (oldFile.getName().endsWith(".java"))
                {
                    if (!compiledFiles.contains(oldFile.getName()))
                    {
                        if (compile)
                        {
                            filesToCompile.add(oldFile);
                        }
                        else
                        {
                            contents.add(oldFile);
                        }
                    }
                }
                else
                {
                    contents.add(oldFile);
                }
            }

            if (!filesToCompile.isEmpty())
            {
                compile(filesToCompile);
            }

            String packageName = getName();
            String upperBound = packageName + Character.MAX_VALUE;

            Collection<RebootFile> compiledClasses =
                cache.subMap(packageName, upperBound).values();

            if (recursive)
            {
                contents.addAll(compiledClasses);
            }
            else
            {
                int packageNameLength = packageName.length();

                for (RebootFile compiledClass : compiledClasses)
                {
                    String compiledClassFileName =
                        compiledClass.getName().substring(packageNameLength);
                    if (!compiledClassFileName.contains("/"))
                    {
                        contents.add(compiledClass);
                    }
                }
            }

            return contents;
        }
    }

    private final ConcurrentNavigableMap<String, RebootFile> cache =
        new ConcurrentSkipListMap<String, RebootFile>();
    private final SortedSet<String> compiledFiles = new ConcurrentSkipListSet<String>();

    public SourceClassLoader(RebootClassLoaderContext context)
    {
        super(context);
    }

    @Override
    public RebootFile findRebootFile(String name) throws IOException
    {
        return findRebootFile(name, true);
    }

    public RebootFile findRebootFile(String name, boolean compile) throws IOException
    {
        if (name.isEmpty() || name.endsWith("/"))
        {
            final RebootFile file = super.findRebootFile(name);
            if (file == null || !file.isDirectory())
            {
                return null;
            }
            return new CompiledDirectory(name, file, compile);
        }

        if (name.endsWith(".java"))
        {
            if (compile || compiledFiles.contains(name))
            {
                return null;
            }
            return super.findRebootFile(name);
        }

        RebootFile file = super.findRebootFile(name);
        if (file != null)
        {
            return file;
        }

        if (name.endsWith(".class"))
        {
            RebootFile cachedFile = cache.get(name);
            if (cachedFile != null)
            {
                return cachedFile;
            }

            if (compile)
            {
                // inner classes are compiled with their outer classes
                String sourceName;
                int index = name.indexOf('$');
                if (index == -1)
                {
                    sourceName = name.substring(0, name.length() - 6) + ".java";
                }
                else
                {
                    sourceName = name.substring(0, index) + ".java";
                }

                // don't try to recompile something that has already been
                // compiled
                if (compiledFiles.contains(sourceName))
                {
                    return null;
                }

                file = super.findRebootFile(sourceName);
                if (file == null)
                {
                    return null;
                }

                compile(Collections.singleton(file));

                return cache.get(name);
            }
        }

        return null;
    }

    private synchronized void compile(Collection<RebootFile> files)
    {
        final long compilationNumber = COMPILATION_COUNT.incrementAndGet();

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        JavaFileManager standardFileManager = compiler.getStandardFileManager(null, null, null);

        RebootFileManager fileManager = new RebootFileManager(standardFileManager, this);
        try
        {
            final Map<String, JavaFileObject> compilationUnits =
                new ConcurrentHashMap<String, JavaFileObject>();
            StringBuilder message = new StringBuilder("Starting compilation request ");
            message.append(compilationNumber).append(':');
            for (RebootFile file : files)
            {
                try
                {
                    String className =
                        file.getName().replace('/', '.')
                            .substring(0, file.getName().length() - 5);
                    JavaFileObject javaFileForInput =
                        fileManager.getJavaFileForInput(StandardLocation.SOURCE_PATH,
                            className, JavaFileObject.Kind.SOURCE);
                    if (javaFileForInput != null)
                    {
                        compilationUnits.put(file.getName(), javaFileForInput);
                        message.append(SINGLE_INDENT).append(javaFileForInput);
                    }
                }
                catch (IOException e)
                {
                    // do nothing
                }
            }

            if (compilationUnits.isEmpty())
            {
                return;
            }

            Reboot.info(message.toString());

            message.delete(0, message.length());

            boolean success = false;

            final StringBuffer warnings = new StringBuffer();
            final StringBuffer errors = new StringBuffer();

            // If annotation processors are installed an "expansion" phase must
            // be run prior to the final compilation. Annotation processors are
            // only used on classes explicitly listed in the compilation
            // request. During the expansion phase any classes that will be
            // implicitly included in the compilation are found and are
            // explicitly added to the list of classes to compile. Only after
            // all the classes are found can the final compilation be completed.
            boolean annotationProcessingEnabled =
                findResource("META-INF/services/javax.annotation.processing.Processor") != null;

            int numberOfClassesToCompile = 0;

            while (!compilationUnits.isEmpty())
            {
                final boolean expanding =
                    annotationProcessingEnabled
                        && numberOfClassesToCompile != compilationUnits.size();

                CompilationTask compilationTask =
                    compiler
                        .getTask(null, fileManager, new DiagnosticListener<JavaFileObject>()
                        {
                            @Override
                            public void report(Diagnostic<? extends JavaFileObject> diagnostic)
                            {
                                String indentedOutput =
                                    DOUBLE_INDENT
                                        + diagnostic.toString()
                                            .replace(NEW_LINE, DOUBLE_INDENT);

                                String fileName;

                                JavaFileObject source = diagnostic.getSource();
                                if (source != null && source instanceof RebootFileObject)
                                {
                                    fileName = ((RebootFileObject) source).getFileName();
                                }
                                else
                                {
                                    fileName = null;
                                }

                                if (expanding && fileName != null)
                                {
                                    // don't record errors or warnings during
                                    // the expansion phase
                                    compilationUnits.put(fileName, (RebootFileObject) source);
                                }
                                else if (diagnostic.getKind() == Diagnostic.Kind.ERROR)
                                {
                                    if (fileName == null)
                                    {
                                        // abort the compile if an error that
                                        // isn't associated with a file occurs
                                        compiledFiles.addAll(compilationUnits.keySet());
                                        compilationUnits.clear();
                                    }
                                    else
                                    {
                                        // prevent the file with an error from
                                        // being compiled again
                                        compilationUnits.remove(fileName);
                                        compiledFiles.add(fileName);
                                    }
                                    errors.append(indentedOutput);
                                }
                                else
                                {
                                    warnings.append(indentedOutput);
                                }
                            }
                        }, null, null, new ArrayList<JavaFileObject>(compilationUnits.values()));

                success = compilationTask.call();

                if (success && !expanding)
                {
                    break;
                }
                else
                {
                    if (expanding)
                    {
                        for (MemoryFileObject memoryFile : fileManager.getMemoryFiles())
                        {
                            String baseName = memoryFile.getClassName();
                            if (baseName.indexOf('$') == -1)
                            {
                                try
                                {
                                    RebootFileObject file =
                                        (RebootFileObject) fileManager.getJavaFileForInput(
                                            StandardLocation.SOURCE_PATH, baseName,
                                            JavaFileObject.Kind.SOURCE);
                                    if (file != null)
                                    {
                                        compilationUnits.put(file.getFileName(), file);
                                    }
                                }
                                catch (IOException e)
                                {
                                    // do nothing
                                }
                                compiledFiles.add(baseName + ".java");
                            }
                        }
                        numberOfClassesToCompile = compilationUnits.size();
                    }

                    // Discard all compiled classes from a compile task with
                    // any errors or from a compile task during the expansion
                    // phase. It is possible for a class that depends on a
                    // class with errors to be successfully compiled. Such a
                    // class would likely cause errors at runtime, so it is
                    // preferable to prevent it from ever being loaded. Even if
                    // this compile succeeded, classes compiled during the
                    // expansion phase may not have been processed by our
                    // annotation processors, so they need to be compiled again.
                    fileManager.clearMemoryFiles();
                    if (!compilationUnits.isEmpty())
                    {
                        warnings.delete(0, warnings.length());
                    }
                }
            }

            message.append("Compilation request ").append(compilationNumber)
                .append(" completed:");

            if (errors.length() > 0)
            {
                message.append(SINGLE_INDENT).append("Errors:").append(errors);
            }
            if (warnings.length() > 0)
            {
                message.append(SINGLE_INDENT).append("Warnings:").append(warnings);
            }

            List<MemoryFileObject> memoryFiles = fileManager.getMemoryFiles();
            if (memoryFiles.isEmpty())
            {
                message.append(SINGLE_INDENT).append("No classes compiled");
            }
            else
            {
                message.append(SINGLE_INDENT).append("Compiled classes:");
                for (MemoryFileObject memoryFile : memoryFiles)
                {
                    String baseName = memoryFile.getClassName().replace('.', '/');
                    String classFileName = baseName + ".class";
                    message.append(DOUBLE_INDENT).append(memoryFile);
                    cache.putIfAbsent(classFileName, new RebootByteFile(classFileName,
                        memoryFile.getContent()));
                    if (baseName.indexOf('$') == -1)
                    {
                        compiledFiles.add(baseName + ".java");
                    }
                }
                fileManager.clearMemoryFiles();
            }

            Reboot.info(message.toString());
        }
        finally
        {
            for (RebootFile file : files)
            {
                compiledFiles.add(file.getName());
            }
            try
            {
                fileManager.flush();
            }
            catch (IOException e)
            {
                // do nothing
            }
            finally
            {
                try
                {
                    fileManager.close();
                }
                catch (IOException e)
                {
                    // do nothing
                }
            }
        }
    }
}

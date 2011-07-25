/*
 * Copyright 2011 Josh Beitelspacher
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
            final String newLine = String.format("%n");
            String indent = newLine + "    ";
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
                        message.append(indent).append(javaFileForInput);
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

            message.append("Compilation request ").append(compilationNumber)
                .append(" completed:");
            boolean success = false;

            while (!compilationUnits.isEmpty())
            {
                int classesLeftToCompile = compilationUnits.size();

                CompilationTask compilationTask =
                    compiler.getTask(null, fileManager,
                        new DiagnosticListener<JavaFileObject>()
                        {
                            @Override
                            public void report(Diagnostic<? extends JavaFileObject> diagnostic)
                            {
                                JavaFileObject source = diagnostic.getSource();
                                if (diagnostic.getKind() == Diagnostic.Kind.ERROR
                                    && source instanceof RebootFileObject)
                                {
                                    String fileName = ((RebootFileObject) source).getFileName();
                                    compilationUnits.remove(fileName);
                                    compiledFiles.add(fileName);
                                }
                                Reboot.info("Compiler output for compilation request "
                                    + compilationNumber + ':' + newLine + diagnostic.toString());
                            }
                        }, null, null, new ArrayList<JavaFileObject>(compilationUnits.values()));

                compilationTask.call();

                List<MemoryFileObject> memoryFiles = fileManager.getMemoryFiles();
                for (MemoryFileObject memoryFile : memoryFiles)
                {
                    String baseName = memoryFile.getClassName().replace('.', '/');
                    String classFileName = baseName + ".class";
                    String sourceFileName = baseName + ".java";
                    message.append(indent).append(memoryFile);
                    cache.putIfAbsent(classFileName, new RebootByteFile(classFileName,
                        memoryFile.getContent()));
                    compilationUnits.remove(sourceFileName);
                    compiledFiles.add(sourceFileName);
                    success = true;
                }
                fileManager.clearMemoryFiles();

                // break any infinite loops here
                if (compilationUnits.size() == classesLeftToCompile)
                {
                    break;
                }
            }

            if (success)
            {
                Reboot.info(message.toString());
            }
            else
            {
                Reboot.info("No classes compiled in compilation request " + compilationNumber);
            }
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

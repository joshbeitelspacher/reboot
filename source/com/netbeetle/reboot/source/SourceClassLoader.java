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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

public class SourceClassLoader extends ClassLoader
{
    private final SortedMap<String, byte[]> cache = new ConcurrentSkipListMap<String, byte[]>();
    private final SortedSet<String> classesWithErrors = new ConcurrentSkipListSet<String>();

    public SourceClassLoader(URL sourceURL, ClassLoader parent)
    {
        super(new URLClassLoader(new URL[] {sourceURL}, parent));
    }

    @Override
    public URL getResource(String name)
    {
        if (name.endsWith(".java"))
        {
            return null;
        }

        URL url = getParent().getResource(name);
        if (url == null)
        {
            return findResource(name);
        }

        if (name.endsWith("/"))
        {
            try
            {
                return new URL(null, url.toString(), new FilteringURLStreamHandler(url));
            }
            catch (MalformedURLException e)
            {
                return null;
            }
        }

        return url;
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException
    {
        if (name.endsWith(".java"))
        {
            return Collections.enumeration(Collections.<URL> emptyList());
        }

        Enumeration<URL> enumeration = getParent().getResources(name);

        List<URL> urls = new ArrayList<URL>();

        if (name.endsWith("/"))
        {
            while (enumeration.hasMoreElements())
            {
                URL url = enumeration.nextElement();
                urls.add(new URL(null, url.toString(), new FilteringURLStreamHandler(url)));
            }
        }
        else
        {
            while (enumeration.hasMoreElements())
            {
                urls.add(enumeration.nextElement());
            }
        }

        URL url = findResource(name);
        if (url != null)
        {
            urls.add(url);
        }

        return Collections.enumeration(urls);
    }

    @Override
    protected URL findResource(final String name)
    {
        if (name.endsWith(".class"))
        {
            final byte[] bytes =
                getClassBytes(name.substring(0, name.length() - 6).replace('/', '.'));

            if (bytes == null)
            {
                return null;
            }

            try
            {
                return new URL(null, "classpath:/" + name, new URLStreamHandler()
                {
                    @Override
                    protected URLConnection openConnection(URL url) throws IOException
                    {
                        return new URLConnection(url)
                        {
                            @Override
                            public void connect() throws IOException
                            {
                                if (!connected)
                                {
                                    connected = true;
                                }
                            }

                            @Override
                            public InputStream getInputStream() throws IOException
                            {
                                connect();
                                return new ByteArrayInputStream(bytes);
                            }
                        };
                    }
                });
            }
            catch (MalformedURLException e)
            {
                return null;
            }
        }
        else if (name.endsWith("/"))
        {
            try
            {
                return new URL(null, "classpath:/" + name, new URLStreamHandler()
                {
                    @Override
                    protected URLConnection openConnection(URL url) throws IOException
                    {
                        return new URLConnection(url)
                        {
                            private boolean recursive;
                            private byte[] bytes;

                            @Override
                            public void connect() throws IOException
                            {
                                if (!connected)
                                {
                                    recursive =
                                        Boolean.parseBoolean(getRequestProperty("recursive"));
                                    bytes =
                                        getPackageBytes(name.substring(0, name.length() - 1)
                                            .replace('/', '.'), recursive);
                                    connected = true;
                                }
                            }

                            @Override
                            public InputStream getInputStream() throws IOException
                            {
                                connect();
                                return new ByteArrayInputStream(bytes);
                            }
                        };
                    }
                });
            }
            catch (MalformedURLException e)
            {
                return null;
            }
        }
        else
        {
            return null;
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException
    {
        byte[] loadedClass = getClassBytes(name);
        if (loadedClass == null)
        {
            throw new ClassNotFoundException(name);
        }

        return defineClass(name, loadedClass, 0, loadedClass.length);
    }

    private byte[] getClassBytes(String name)
    {
        byte[] cachedBytes = cache.get(name);
        if (cachedBytes != null)
        {
            return cachedBytes;
        }

        // don't try to recompile something that is known to be bad
        if (classesWithErrors.contains(name))
        {
            return null;
        }

        // inner classes are compiled with their outer classes and cannot be
        // compiled separately
        if (name.contains("$"))
        {
            return null;
        }

        compileClasses(Collections.singletonList(name));

        return cache.get(name);
    }

    private byte[] getPackageBytes(String name, boolean recursive) throws IOException
    {
        Enumeration<URL> urls = getParent().getResources(name.replace('.', '/') + '/');

        List<String> classesToCompile = new ArrayList<String>();

        while (urls.hasMoreElements())
        {
            URL url = urls.nextElement();

            URLConnection connection = url.openConnection();
            connection.setRequestProperty("recursive", Boolean.toString(recursive));
            BufferedReader reader =
                new BufferedReader(new InputStreamReader(connection.getInputStream(),
                    CharsetUtil.UTF8));
            try
            {
                for (String file = reader.readLine(); file != null; file = reader.readLine())
                {
                    if (!file.endsWith(".java"))
                    {
                        continue;
                    }

                    String className = name + '.' + file.substring(0, file.length() - 5);

                    if (!cache.containsKey(className) && !classesWithErrors.contains(className))
                    {
                        classesToCompile.add(className);
                    }
                }
                reader.close();
                reader = null;
            }
            finally
            {
                if (reader != null)
                {
                    reader.close();
                }
            }
        }

        if (!classesToCompile.isEmpty())
        {
            compileClasses(classesToCompile);
        }

        StringBuilder builder = new StringBuilder();

        Set<String> compiledClasses = cache.subMap(name + '.', name + ('.' + 1)).keySet();

        int packageNameLength = name.length() + 1;

        for (String compiledClass : compiledClasses)
        {
            String compiledClassFileName = compiledClass.substring(packageNameLength);
            if (recursive || !compiledClassFileName.contains("."))
            {
                builder.append(compiledClassFileName);
                builder.append(".class\n");
            }
        }

        return builder.toString().getBytes(CharsetUtil.UTF8);
    }

    private synchronized void compileClasses(List<String> names)
    {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        JavaFileManager standardFileManager = compiler.getStandardFileManager(null, null, null);

        List<String> compiledClasses = new ArrayList<String>();

        ClasspathFileManager fileManager =
            new ClasspathFileManager(standardFileManager, getParent());
        try
        {
            final Map<String, JavaFileObject> compilationUnits =
                new ConcurrentHashMap<String, JavaFileObject>();
            for (String name : names)
            {
                try
                {
                    JavaFileObject javaFileForInput =
                        fileManager.getJavaFileForInput(StandardLocation.SOURCE_PATH, name,
                            JavaFileObject.Kind.SOURCE);
                    if (javaFileForInput != null)
                    {
                        compilationUnits.put(name, javaFileForInput);
                    }
                }
                catch (IOException e)
                {
                    return;
                }
            }

            while (!compilationUnits.isEmpty())
            {
                int classesLeftToCompile = compilationUnits.size();

                CompilationTask compilationTask =
                    compiler
                        .getTask(null, fileManager, new DiagnosticListener<JavaFileObject>()
                        {
                            @Override
                            public void report(Diagnostic<? extends JavaFileObject> diagnostic)
                            {
                                JavaFileObject source = diagnostic.getSource();
                                if (diagnostic.getKind() == Diagnostic.Kind.ERROR
                                    && source != null)
                                {
                                    compilationUnits.remove(source.getName());
                                }
                                System.err.println(diagnostic.toString());
                            }
                        }, null, null, new ArrayList<JavaFileObject>(compilationUnits.values()));

                compilationTask.call();

                List<MemoryFileObject> memoryFiles = fileManager.getMemoryFiles();
                for (MemoryFileObject memoryFile : memoryFiles)
                {
                    String name = memoryFile.getName();
                    cache.put(name, memoryFile.getContent());
                    compiledClasses.add(name);
                    compilationUnits.remove(name);
                }
                fileManager.clearMemoryFiles();

                // break any infinite loops here
                if (compilationUnits.size() == classesLeftToCompile)
                {
                    break;
                }
            }
        }
        finally
        {
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

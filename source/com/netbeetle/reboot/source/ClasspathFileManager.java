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
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.StandardLocation;

public class ClasspathFileManager implements JavaFileManager
{
    private final JavaFileManager standardFileManager;
    private final ClassLoader classLoader;
    private final List<MemoryFileObject> memoryFiles;

    public ClasspathFileManager(JavaFileManager standardFileManager, ClassLoader classLoader)
    {
        this.standardFileManager = standardFileManager;
        this.classLoader = classLoader;
        this.memoryFiles = new ArrayList<MemoryFileObject>();
    }

    @Override
    public int isSupportedOption(String option)
    {
        return -1;
    }

    @Override
    public ClassLoader getClassLoader(Location location)
    {
        return classLoader;
    }

    @Override
    public Iterable<JavaFileObject> list(Location location, String packageName,
        Set<Kind> kinds, boolean recurse) throws IOException
    {
        if (location == StandardLocation.PLATFORM_CLASS_PATH)
        {
            return standardFileManager.list(location, packageName, kinds, recurse);
        }

        if (location == StandardLocation.CLASS_PATH || location == StandardLocation.SOURCE_PATH)
        {
            boolean needSource = false;
            boolean needClass = false;
            boolean needHTML = false;
            boolean needOther = false;
            for (Kind kind : kinds)
            {
                switch (kind)
                {
                    case SOURCE:
                        needSource = true;
                        break;
                    case CLASS:
                        needClass = true;
                        break;
                    case HTML:
                        needHTML = true;
                        break;
                    case OTHER:
                        needOther = true;
                        break;
                }
            }

            List<JavaFileObject> matchingFiles = new ArrayList<JavaFileObject>();

            String packageDirectory = packageName.replace('.', '/') + '/';
            Enumeration<URL> urls = classLoader.getResources(packageDirectory);
            while (urls.hasMoreElements())
            {
                URL url = urls.nextElement();
                URLConnection connection = url.openConnection();
                connection.setRequestProperty("recursive", Boolean.toString(recurse));
                BufferedReader reader =
                    new BufferedReader(new InputStreamReader(connection.getInputStream(),
                        Charset.forName("UTF-8")));
                try
                {
                    for (String file = reader.readLine(); file != null; file =
                        reader.readLine())
                    {
                        if (file.length() == 0)
                        {
                            continue;
                        }

                        String fileName = packageDirectory + file;

                        URI fileURI = new URL(url, file).toURI();

                        int index = file.lastIndexOf('.');
                        if (index == -1)
                        {
                            index = file.length();
                        }
                        String className =
                            packageName + '.' + file.substring(0, index).replace('/', '.');

                        if (needSource && file.endsWith(Kind.SOURCE.extension))
                        {
                            matchingFiles.add(new ClasspathFileObject(classLoader, fileName,
                                fileURI, className, Kind.SOURCE));
                        }
                        else if (needClass && file.endsWith(Kind.CLASS.extension))
                        {
                            matchingFiles.add(new ClasspathFileObject(classLoader, fileName,
                                fileURI, className, Kind.CLASS));
                        }
                        else if (needHTML && file.endsWith(Kind.HTML.extension))
                        {
                            matchingFiles.add(new ClasspathFileObject(classLoader, fileName,
                                fileURI, className, Kind.HTML));
                        }
                        else if (needOther)
                        {
                            matchingFiles.add(new ClasspathFileObject(classLoader, fileName,
                                fileURI, fileName, Kind.OTHER));
                        }
                    }
                    reader.close();
                    reader = null;
                }
                catch (URISyntaxException e)
                {
                    throw new IOException(e);
                }
                finally
                {
                    if (reader != null)
                    {
                        reader.close();
                    }
                }
            }

            return matchingFiles;
        }

        return Collections.emptyList();
    }

    @Override
    public String inferBinaryName(Location location, JavaFileObject file)
    {
        if (location == StandardLocation.PLATFORM_CLASS_PATH)
        {
            return standardFileManager.inferBinaryName(location, file);
        }

        String name = file.getName();
        int period = name.lastIndexOf('.');
        if (period != -1)
        {
            name.substring(0, period);
        }
        return name;
    }

    @Override
    public boolean isSameFile(FileObject a, FileObject b)
    {
        return a.getName().equals(b.getName());
    }

    @Override
    public boolean handleOption(String current, Iterator<String> remaining)
    {
        return false;
    }

    @Override
    public boolean hasLocation(Location location)
    {
        if (location == StandardLocation.SOURCE_OUTPUT)
        {
            return false;
        }
        return true;
    }

    @Override
    public JavaFileObject getJavaFileForInput(Location location, String className, Kind kind)
        throws IOException
    {
        if (location == StandardLocation.PLATFORM_CLASS_PATH)
        {
            return standardFileManager.getJavaFileForInput(location, className, kind);
        }

        String path = className.replace('.', '/') + kind.extension;
        URL url = classLoader.getResource(path);
        if (url == null)
        {
            return null;
        }
        try
        {
            return new ClasspathFileObject(classLoader, path, url.toURI(), className, kind);
        }
        catch (URISyntaxException e)
        {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location, String className, Kind kind,
        FileObject sibling) throws IOException
    {
        URI uri = URI.create("classpath:/" + className.replace('.', '/') + kind.extension);
        MemoryFileObject memoryFile = new MemoryFileObject(uri, className, kind);
        memoryFiles.add(memoryFile);
        return memoryFile;
    }

    @Override
    public FileObject getFileForInput(Location location, String packageName, String relativeName)
        throws IOException
    {
        if (location == StandardLocation.PLATFORM_CLASS_PATH)
        {
            return standardFileManager.getFileForInput(location, packageName, relativeName);
        }

        String path =
            URI.create("/").resolve(packageName.replace('.', '/') + '/').resolve(relativeName)
                .toString();
        URL url = classLoader.getResource(path);
        if (url == null)
        {
            return null;
        }
        try
        {
            return new ClasspathFileObject(classLoader, path, url.toURI(), path,
                JavaFileObject.Kind.OTHER);
        }
        catch (URISyntaxException e)
        {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public FileObject getFileForOutput(Location location, String packageName,
        String relativeName, FileObject sibling) throws IOException
    {
        URI uri =
            URI.create("classpath:/").resolve(packageName.replace('.', '/') + '/')
                .resolve(relativeName);
        // TODO: uri.getPath() isn't quite right
        MemoryFileObject memoryFile =
            new MemoryFileObject(uri, uri.getPath(), JavaFileObject.Kind.OTHER);
        memoryFiles.add(memoryFile);
        return memoryFile;
    }

    public List<MemoryFileObject> getMemoryFiles()
    {
        return memoryFiles;
    }

    @Override
    public void flush() throws IOException
    {
        standardFileManager.flush();
    }

    @Override
    public void close() throws IOException
    {
        standardFileManager.close();
    }

    public void clearMemoryFiles()
    {
        memoryFiles.clear();
    }
}

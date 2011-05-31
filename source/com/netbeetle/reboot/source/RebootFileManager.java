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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.StandardLocation;

import com.netbeetle.reboot.core.RebootClassLoader;
import com.netbeetle.reboot.core.RebootClassLoaderContext;
import com.netbeetle.reboot.core.RebootFile;

public class RebootFileManager implements JavaFileManager
{
    private final JavaFileManager standardFileManager;
    private final SourceClassLoader source;
    private final Set<RebootClassLoader> dependencies;
    private final List<MemoryFileObject> memoryFiles;

    public RebootFileManager(JavaFileManager standardFileManager, SourceClassLoader source,
        Set<RebootClassLoader> dependencies)
    {
        this.standardFileManager = standardFileManager;
        this.source = source;
        this.dependencies = dependencies;
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
        if (location == StandardLocation.PLATFORM_CLASS_PATH)
        {
            return standardFileManager.getClassLoader(location);
        }

        // returning the source class loader could potentially cause a deadlock
        // if a recursive compile was requested from a different thread, instead
        // create a new classloader that won't compile anything
        return new RebootClassLoader(new RebootClassLoaderContext(source.getModuleName(),
            source.getFileSystem(), dependencies, source.getParent()));
    }

    @Override
    public Iterable<JavaFileObject> list(Location location, String packageName,
        Set<Kind> kinds, boolean recurse) throws IOException
    {
        Iterable<JavaFileObject> list =
            standardFileManager.list(location, packageName, kinds, recurse);
        if (location == StandardLocation.PLATFORM_CLASS_PATH)
        {
            return list;
        }

        List<JavaFileObject> files = new ArrayList<JavaFileObject>();
        for (JavaFileObject file : list)
        {
            files.add(file);
        }

        if (location == StandardLocation.CLASS_PATH || location == StandardLocation.SOURCE_PATH)
        {
            Set<Kind> nonSourceKinds = EnumSet.copyOf(kinds);
            nonSourceKinds.remove(Kind.SOURCE);

            String directoryName = packageName.replace('.', '/');
            if (!packageName.isEmpty())
            {
                directoryName += '/';
            }

            if (!nonSourceKinds.isEmpty())
            {
                for (RebootClassLoader dependency : dependencies)
                {
                    RebootFile directory = dependency.findRebootFile(directoryName);
                    if (directory == null)
                    {
                        continue;
                    }

                    files.addAll(list(dependency.getModuleName(), directory, nonSourceKinds,
                        recurse));
                }
            }

            RebootFile directory = source.findRebootFile(directoryName, false);
            if (directory != null)
            {
                files.addAll(list(source.getModuleName(), directory, kinds, recurse));
            }
        }

        return files;
    }

    private List<JavaFileObject> list(String moduleName, RebootFile directory, Set<Kind> kinds,
        boolean recurse) throws IOException
    {
        List<JavaFileObject> list = new ArrayList<JavaFileObject>();

        for (RebootFile file : directory.list(recurse))
        {
            String fileName = file.getName();

            URI fileURI;
            try
            {
                fileURI = new URI("rbt:/" + moduleName + '/' + fileName);
            }
            catch (URISyntaxException e)
            {
                continue;
            }

            Kind matchingKind = getKind(kinds, fileName);

            if (matchingKind != null)
            {
                int index = fileName.lastIndexOf('.');
                if (index == -1)
                {
                    index = fileName.length();
                }

                String className = fileName.substring(0, index).replace('/', '.');

                list.add(new RebootFileObject(file, fileURI, className, matchingKind));
            }
        }

        return list;
    }

    @Override
    public String inferBinaryName(Location location, JavaFileObject file)
    {
        if (location == StandardLocation.PLATFORM_CLASS_PATH)
        {
            return standardFileManager.inferBinaryName(location, file);
        }

        if (file instanceof RebootFileObject)
        {
            return ((RebootFileObject) file).getClassName();
        }

        if (file instanceof MemoryFileObject)
        {
            return ((MemoryFileObject) file).getClassName();
        }

        return standardFileManager.inferBinaryName(location, file);
    }

    @Override
    public boolean isSameFile(FileObject a, FileObject b)
    {
        return a.toUri().equals(b.toUri());
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
        JavaFileObject standardFile =
            standardFileManager.getJavaFileForInput(location, className, kind);
        if (standardFile != null || location == StandardLocation.PLATFORM_CLASS_PATH)
        {
            return standardFile;
        }

        String fileName = className.replace('.', '/') + kind.extension;

        if (kind != Kind.SOURCE)
        {
            for (RebootClassLoader dependency : dependencies)
            {
                RebootFile file = dependency.findRebootFile(fileName);
                if (file == null)
                {
                    continue;
                }

                URI fileURI;
                try
                {
                    fileURI = new URI("rbt:/" + dependency.getModuleName() + '/' + fileName);
                }
                catch (URISyntaxException e)
                {
                    continue;
                }

                return new RebootFileObject(file, fileURI, className, kind);
            }
        }

        RebootFile file = source.findRebootFile(fileName, false);
        if (file == null)
        {
            return null;
        }

        URI fileURI;
        try
        {
            fileURI = new URI("rbt:/" + source.getModuleName() + '/' + fileName);
        }
        catch (URISyntaxException e)
        {
            return null;
        }

        return new RebootFileObject(file, fileURI, className, kind);
    }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location, String className, Kind kind,
        FileObject sibling) throws IOException
    {
        URI fileURI;
        try
        {
            fileURI =
                new URI("rbt:/" + source.getModuleName() + '/' + className.replace('.', '/')
                    + kind.extension);
        }
        catch (URISyntaxException e)
        {
            return null;
        }

        MemoryFileObject memoryFile = new MemoryFileObject(fileURI, className, kind);
        memoryFiles.add(memoryFile);
        return memoryFile;
    }

    @Override
    public FileObject getFileForInput(Location location, String packageName, String relativeName)
        throws IOException
    {
        FileObject standardFile =
            standardFileManager.getFileForInput(location, packageName, relativeName);
        if (standardFile != null || location == StandardLocation.PLATFORM_CLASS_PATH)
        {
            return standardFile;
        }

        return null;
    }

    @Override
    public FileObject getFileForOutput(Location location, String packageName,
        String relativeName, FileObject sibling) throws IOException
    {
        return null;
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

    private Kind getKind(Set<Kind> kinds, String fileName)
    {
        Kind matchingKind = null;
        if (fileName.endsWith(Kind.SOURCE.extension))
        {
            if (kinds.contains(Kind.SOURCE))
            {
                matchingKind = Kind.SOURCE;
            }
        }
        else if (fileName.endsWith(Kind.CLASS.extension))
        {
            if (kinds.contains(Kind.CLASS))
            {
                matchingKind = Kind.CLASS;
            }
        }
        else if (fileName.endsWith(Kind.HTML.extension))
        {
            if (kinds.contains(Kind.HTML))
            {
                matchingKind = Kind.HTML;
            }
        }
        else
        {
            if (kinds.contains(Kind.OTHER))
            {
                matchingKind = Kind.OTHER;
            }
        }
        return matchingKind;
    }
}

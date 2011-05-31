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

package com.netbeetle.reboot.core;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RebootClassLoader extends ClassLoader
{
    private static final Map<String, RebootClassLoader> REGISTERED_CLASSLOADERS =
        new ConcurrentHashMap<String, RebootClassLoader>();

    public static RebootClassLoader getClassLoader(String moduleName)
    {
        return REGISTERED_CLASSLOADERS.get(moduleName);
    }

    private final String moduleName;
    private final RebootFileSystem fileSystem;
    private final Set<RebootClassLoader> dependencies;

    public RebootClassLoader(RebootClassLoaderContext context)
    {
        super(context.getParent());
        this.moduleName = context.getModuleName();
        this.fileSystem = context.getFileSystem();
        Set<RebootClassLoader> temp = new LinkedHashSet<RebootClassLoader>();
        for (RebootClassLoader dependency : context.getDependencies())
        {
            if (dependency.getParent() != getParent())
            {
                throw new IllegalArgumentException(
                    "All dependency classloaders must use the same parent classloader.");
            }

            temp.addAll(dependency.getDependencies());

            temp.add(dependency);
        }
        this.dependencies = Collections.unmodifiableSet(temp);
    }

    public void register()
    {
        REGISTERED_CLASSLOADERS.put(moduleName, this);
    }

    public void unregister()
    {
        REGISTERED_CLASSLOADERS.remove(moduleName);
    }

    public String getModuleName()
    {
        return moduleName;
    }

    public Set<RebootClassLoader> getDependencies()
    {
        return dependencies;
    }

    public RebootFileSystem getFileSystem()
    {
        return fileSystem;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException
    {
        for (RebootClassLoader dependency : dependencies)
        {
            try
            {
                return dependency.findRebootClass(name);
            }
            catch (ClassNotFoundException e)
            {
                // do nothing
            }
        }
        return findRebootClass(name);
    }

    @Override
    protected URL findResource(String name)
    {
        for (RebootClassLoader dependency : dependencies)
        {
            try
            {
                RebootFile resource = dependency.findRebootFile(name);
                if (resource != null)
                {
                    return new URL("rbt:/" + dependency.getModuleName() + '/'
                        + resource.getName());
                }
            }
            catch (IOException e)
            {
                // do nothing
            }
        }
        try
        {
            RebootFile resource = findRebootFile(name);
            if (resource != null)
            {
                return new URL("rbt:/" + moduleName + '/' + resource.getName());
            }
        }
        catch (IOException e)
        {
            // do nothing
        }
        return null;
    }

    @Override
    protected Enumeration<URL> findResources(String name) throws IOException
    {
        Collection<URL> urls = new LinkedList<URL>();
        for (RebootClassLoader dependency : dependencies)
        {
            // All transitive dependencies are already resolved, so each
            // dependency is only allowed to provide a single URL
            try
            {
                RebootFile resource = dependency.findRebootFile(name);
                if (resource != null)
                {
                    urls.add(new URL("rbt:/" + dependency.getModuleName() + '/'
                        + resource.getName()));
                }
            }
            catch (MalformedURLException e)
            {
                // do nothing
            }
        }
        try
        {
            RebootFile resource = findRebootFile(name);
            if (resource != null)
            {
                urls.add(new URL("rbt:/" + moduleName + '/' + resource.getName()));
            }
        }
        catch (MalformedURLException e)
        {
            // do nothing
        }
        return Collections.enumeration(urls);
    }

    protected Class<?> findRebootClass(String name) throws ClassNotFoundException
    {
        Class<?> loadedClass = findLoadedClass(name);
        if (loadedClass != null)
        {
            return loadedClass;
        }

        try
        {
            String filename = name.replace('.', '/') + ".class";
            RebootFile file = findRebootFile(filename);
            if (file == null)
            {
                throw new ClassNotFoundException(name);
            }
            byte[] bytes = file.getBytes();
            return defineClass(name, bytes, 0, bytes.length);
        }
        catch (IOException e)
        {
            throw new ClassNotFoundException(name, e);
        }
    }

    public RebootFile findRebootFile(String name) throws IOException
    {
        return fileSystem.getFile(name);
    }
}

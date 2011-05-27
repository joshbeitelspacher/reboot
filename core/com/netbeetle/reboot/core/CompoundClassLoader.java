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
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public class CompoundClassLoader extends ClassLoader
{
    private final ClassLoader[] children;

    public CompoundClassLoader(ClassLoader parent, ClassLoader... children)
    {
        super(parent);
        this.children = children;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException
    {
        for (ClassLoader child : children)
        {
            try
            {
                return child.loadClass(name);
            }
            catch (ClassNotFoundException e)
            {
                // do nothing
            }
        }
        throw new ClassNotFoundException(name);
    }

    @Override
    protected URL findResource(String name)
    {
        for (ClassLoader child : children)
        {
            URL resource = child.getResource(name);
            if (resource != null)
            {
                return resource;
            }
        }
        return null;
    }

    @Override
    protected Enumeration<URL> findResources(String name) throws IOException
    {
        List<URL> resources = new ArrayList<URL>();
        for (ClassLoader child : children)
        {
            Enumeration<URL> childResources = child.getResources(name);
            while (childResources.hasMoreElements())
            {
                URL childResource = childResources.nextElement();
                resources.add(childResource);
            }
        }
        return Collections.enumeration(resources);
    }
}

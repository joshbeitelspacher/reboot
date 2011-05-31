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

import java.util.Set;

public class RebootClassLoaderContext
{
    private final String moduleName;
    private final RebootFileSystem fileSystem;
    private final Set<RebootClassLoader> dependencies;
    private final ClassLoader parent;

    public RebootClassLoaderContext(String moduleName, RebootFileSystem fileSystem,
        Set<RebootClassLoader> dependencies, ClassLoader parent)
    {
        this.moduleName = moduleName;
        this.fileSystem = fileSystem;
        this.dependencies = dependencies;
        this.parent = parent;
    }

    public String getModuleName()
    {
        return moduleName;
    }

    public RebootFileSystem getFileSystem()
    {
        return fileSystem;
    }

    public Set<RebootClassLoader> getDependencies()
    {
        return dependencies;
    }

    public ClassLoader getParent()
    {
        return parent;
    }
}

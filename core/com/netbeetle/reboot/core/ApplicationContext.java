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

import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.netbeetle.reboot.core.config.ClassLoaderConfig;
import com.netbeetle.reboot.core.config.ClassLoaderReference;
import com.netbeetle.reboot.core.config.EntryPointConfig;
import com.netbeetle.reboot.core.config.ModuleConfig;
import com.netbeetle.reboot.core.config.ModuleReference;
import com.netbeetle.reboot.core.config.RebootConfig;
import com.netbeetle.reboot.core.config.URIResolverConfig;

public class ApplicationContext
{
    private static final ClassLoaderReference SOURCE_CLASS_LOADER = new ClassLoaderReference(
        "reboot-source-classloader");

    private final URI rebootDir;
    private final RebootConfig rebootConfig;
    private final ApplicationContext parentConfigLoader;
    private final Map<ModuleConfig, ClassLoader> classLoaders =
        new HashMap<ModuleConfig, ClassLoader>();
    private final Map<URIResolverConfig, URIResolver> uriResolvers =
        new HashMap<URIResolverConfig, URIResolver>();

    public ApplicationContext(URI rebootDir, RebootConfig rebootConfig,
        ApplicationContext parentConfigLoader)
    {
        this.rebootDir = rebootDir;
        this.rebootConfig = rebootConfig;
        this.parentConfigLoader = parentConfigLoader;
    }

    private ModuleConfig lookupModuleConfig(ModuleReference moduleReference)
    {
        if (rebootConfig.getModules() == null)
        {
            return null;
        }
        for (ModuleConfig module : rebootConfig.getModules())
        {
            if (module.getId().equals(moduleReference.getId()))
            {
                return module;
            }
        }
        return null;
    }

    private ClassLoaderConfig lookupClassLoaderConfig(ClassLoaderReference classLoaderReference)
    {
        if (rebootConfig.getClassLoaders() == null)
        {
            return null;
        }
        for (ClassLoaderConfig classLoader : rebootConfig.getClassLoaders())
        {
            if (classLoader.getId().equals(classLoaderReference.getId()))
            {
                return classLoader;
            }
        }
        return null;
    }

    public ClassLoader getClassLoader(ModuleReference moduleReference)
        throws SecurityException, MalformedURLException, NoSuchMethodException,
        ClassNotFoundException, InstantiationException, IllegalAccessException,
        InvocationTargetException
    {
        ModuleConfig module = lookupModuleConfig(moduleReference);
        if (module == null)
        {
            if (parentConfigLoader == null)
            {
                throw new RuntimeException("Module not found: " + moduleReference.getId());
            }
            return parentConfigLoader.getClassLoader(moduleReference);
        }
        return getClassLoader(module);
    }

    private ClassLoader getClassLoader(ModuleConfig module) throws SecurityException,
        MalformedURLException, NoSuchMethodException, ClassNotFoundException,
        InstantiationException, IllegalAccessException, InvocationTargetException
    {
        if (classLoaders.containsKey(module))
        {
            ClassLoader classLoader = classLoaders.get(module);
            if (classLoader == null)
            {
                throw new RuntimeException("Recursive dependencies detected.");
            }
            return classLoader;
        }

        classLoaders.put(module, null);
        boolean success = false;
        try
        {
            ClassLoader dependencyClassLoader;

            if (module.getDependencies() == null || module.getDependencies().isEmpty())
            {
                dependencyClassLoader = ClassLoader.getSystemClassLoader();
            }
            else
            {
                List<ClassLoader> dependencyClassLoaders = new ArrayList<ClassLoader>();
                for (ModuleReference dependency : module.getDependencies())
                {
                    dependencyClassLoaders.add(getClassLoader(dependency));
                }
                if (dependencyClassLoaders.size() == 1)
                {
                    dependencyClassLoader = dependencyClassLoaders.get(0);
                }
                else
                {
                    dependencyClassLoader =
                        new CompoundClassLoader(ClassLoader.getSystemClassLoader(),
                            dependencyClassLoaders
                                .toArray(new ClassLoader[dependencyClassLoaders.size()]));
                }
            }

            ClassLoaderReference moduleClassLoader = module.getClassLoader();

            URL url;
            if (module.getUri() != null)
            {
                url = getURL(module.getUri());
            }
            else if (module.getSrcUri() != null)
            {
                url = getURL(module.getSrcUri());
                if (moduleClassLoader == null)
                {
                    moduleClassLoader = SOURCE_CLASS_LOADER;
                }
            }
            else
            {
                throw new RuntimeException("No URI defined for " + module.getId());
            }

            ClassLoader classLoader;
            if (moduleClassLoader == null)
            {
                classLoader = new URLClassLoader(new URL[] {url}, dependencyClassLoader);
            }
            else
            {
                classLoader = getClassLoader(moduleClassLoader, url, dependencyClassLoader);
            }

            classLoaders.put(module, classLoader);

            success = true;

            return classLoader;
        }
        finally
        {
            if (!success)
            {
                classLoaders.remove(module);
            }
        }
    }

    private URL getURL(URI uri) throws MalformedURLException, SecurityException,
        InstantiationException, IllegalAccessException, ClassNotFoundException,
        NoSuchMethodException, InvocationTargetException
    {
        URI fullURI = rebootDir.resolve(uri);

        if (fullURI.getScheme().equals("file"))
        {
            return FileURIResolver.getInstance().resolve(fullURI);
        }

        List<URIResolverConfig> uriResolverConfigs = rebootConfig.getUriResolvers();

        if (uriResolverConfigs != null)
        {
            String fullURIString = fullURI.toString();

            for (URIResolverConfig uriResolverConfig : uriResolverConfigs)
            {
                if (uriResolverConfig.getExpression().matcher(fullURIString).matches())
                {
                    URIResolver uriResolver = getURIResolver(uriResolverConfig);
                    return uriResolver.resolve(fullURI);
                }
            }
        }

        if (parentConfigLoader == null)
        {
            throw new RuntimeException("URL handler not found for URL: " + fullURI);
        }

        return parentConfigLoader.getURL(fullURI);
    }

    private URIResolver getURIResolver(URIResolverConfig uriResolverConfig)
        throws InstantiationException, IllegalAccessException, MalformedURLException,
        ClassNotFoundException, NoSuchMethodException, InvocationTargetException
    {
        URIResolver uriResolver = uriResolvers.get(uriResolverConfig);
        if (uriResolver == null)
        {
            uriResolver =
                getEntryPointClass(uriResolverConfig).asSubclass(URIResolver.class)
                    .newInstance();
            uriResolvers.put(uriResolverConfig, uriResolver);
        }
        return uriResolver;
    }

    public ClassLoader getClassLoader(ClassLoaderReference classLoaderReference, URL url,
        ClassLoader parentClassLoader) throws InstantiationException, IllegalAccessException,
        MalformedURLException, ClassNotFoundException, NoSuchMethodException,
        InvocationTargetException
    {
        ClassLoaderConfig classLoader = lookupClassLoaderConfig(classLoaderReference);
        if (classLoader == null)
        {
            if (parentConfigLoader == null)
            {
                throw new RuntimeException("ClassLoader not found: "
                    + classLoaderReference.getId());
            }
            return parentConfigLoader.getClassLoader(classLoaderReference, url,
                parentClassLoader);
        }
        return getClassLoader(classLoader, url, parentClassLoader);
    }

    private ClassLoader getClassLoader(ClassLoaderConfig classLoaderConfig, URL url,
        ClassLoader parentClassLoader) throws InstantiationException, IllegalAccessException,
        MalformedURLException, ClassNotFoundException, NoSuchMethodException,
        InvocationTargetException
    {
        return getEntryPointClass(classLoaderConfig).asSubclass(ClassLoader.class)
            .getConstructor(URL.class, ClassLoader.class).newInstance(url, parentClassLoader);
    }

    public Class<?> getEntryPointClass(EntryPointConfig entryPoint) throws SecurityException,
        MalformedURLException, ClassNotFoundException, NoSuchMethodException,
        InstantiationException, IllegalAccessException, InvocationTargetException
    {
        return getClassLoader(entryPoint.getModuleReference()).loadClass(
            entryPoint.getClassName());
    }

    public Class<?> getEntryPointClass() throws SecurityException, MalformedURLException,
        ClassNotFoundException, NoSuchMethodException, InstantiationException,
        IllegalAccessException, InvocationTargetException
    {
        return getEntryPointClass(rebootConfig.getEntryPoint());
    }
}

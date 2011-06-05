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
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.netbeetle.reboot.core.config.ActionConfig;
import com.netbeetle.reboot.core.config.ActionReference;
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
    private final Map<ModuleConfig, RebootClassLoader> classLoaders =
        new HashMap<ModuleConfig, RebootClassLoader>();
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

    private ActionConfig lookupActionConfig(ActionReference actionReference)
    {
        if (rebootConfig.getActions() == null)
        {
            return null;
        }
        for (ActionConfig action : rebootConfig.getActions())
        {
            if (action.getId().equals(actionReference.getId()))
            {
                return action;
            }
        }
        return null;
    }

    public RebootClassLoader getClassLoader(ModuleReference moduleReference)
        throws NoSuchMethodException, ClassNotFoundException, InstantiationException,
        IllegalAccessException, InvocationTargetException, RebootException
    {
        ModuleConfig module = lookupModuleConfig(moduleReference);
        if (module == null)
        {
            if (parentConfigLoader == null)
            {
                throw new RebootException("Module not found: " + moduleReference.getId());
            }
            return parentConfigLoader.getClassLoader(moduleReference);
        }
        return getClassLoader(module);
    }

    private RebootClassLoader getClassLoader(ModuleConfig module) throws NoSuchMethodException,
        ClassNotFoundException, InstantiationException, IllegalAccessException,
        InvocationTargetException, RebootException
    {
        if (classLoaders.containsKey(module))
        {
            RebootClassLoader classLoader = classLoaders.get(module);
            if (classLoader == null)
            {
                throw new RebootException("Recursive dependencies detected.");
            }
            return classLoader;
        }

        classLoaders.put(module, null);
        boolean success = false;
        try
        {
            ClassLoader parent = ClassLoader.getSystemClassLoader();

            Set<RebootClassLoader> dependencies = new LinkedHashSet<RebootClassLoader>();
            if (module.getDependencies() != null)
            {
                for (ModuleReference dependency : module.getDependencies())
                {
                    dependencies.add(getClassLoader(dependency));
                }
            }

            ClassLoaderReference moduleClassLoader = module.getClassLoader();

            RebootFileSystem fileSystem;
            if (module.getUri() != null)
            {
                fileSystem = getFileSystem(module.getUri());
            }
            else if (module.getSrcUri() != null)
            {
                fileSystem = getFileSystem(module.getSrcUri());
                if (moduleClassLoader == null)
                {
                    moduleClassLoader = SOURCE_CLASS_LOADER;
                }
            }
            else
            {
                throw new RebootException("No URI defined for " + module.getId());
            }

            RebootClassLoaderContext context =
                new RebootClassLoaderContext(module.getId(), fileSystem, dependencies, parent);

            RebootClassLoader classLoader;
            if (moduleClassLoader == null)
            {
                classLoader = new RebootClassLoader(context);
            }
            else
            {
                classLoader = getClassLoader(moduleClassLoader, context);
            }

            classLoaders.put(module, classLoader);
            classLoader.register();
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

    private RebootFileSystem getFileSystem(URI uri) throws InstantiationException,
        IllegalAccessException, ClassNotFoundException, NoSuchMethodException,
        InvocationTargetException, RebootException
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
            throw new RebootException("URL handler not found for URL: " + fullURI);
        }

        return parentConfigLoader.getFileSystem(fullURI);
    }

    private URIResolver getURIResolver(URIResolverConfig uriResolverConfig)
        throws InstantiationException, IllegalAccessException, ClassNotFoundException,
        NoSuchMethodException, InvocationTargetException, RebootException
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

    private RebootClassLoader getClassLoader(ClassLoaderReference classLoaderReference,
        RebootClassLoaderContext context) throws InstantiationException,
        IllegalAccessException, ClassNotFoundException, NoSuchMethodException,
        InvocationTargetException, RebootException
    {
        ClassLoaderConfig classLoader = lookupClassLoaderConfig(classLoaderReference);
        if (classLoader == null)
        {
            if (parentConfigLoader == null)
            {
                throw new RebootException("ClassLoader not found: "
                    + classLoaderReference.getId());
            }
            return parentConfigLoader.getClassLoader(classLoaderReference, context);
        }
        return getClassLoader(classLoader, context);
    }

    private RebootClassLoader getClassLoader(ClassLoaderConfig classLoaderConfig,
        RebootClassLoaderContext context) throws InstantiationException,
        IllegalAccessException, ClassNotFoundException, NoSuchMethodException,
        InvocationTargetException, RebootException
    {
        return getEntryPointClass(classLoaderConfig).asSubclass(RebootClassLoader.class)
            .getConstructor(RebootClassLoaderContext.class).newInstance(context);
    }

    public Class<?> getEntryPointClass(EntryPointConfig entryPoint) throws SecurityException,
        ClassNotFoundException, NoSuchMethodException, InstantiationException,
        IllegalAccessException, InvocationTargetException, RebootException
    {
        return getClassLoader(entryPoint.getModuleReference()).loadClass(
            entryPoint.getClassName());
    }

    public Class<?> getEntryPointClass() throws ClassNotFoundException, NoSuchMethodException,
        InstantiationException, IllegalAccessException, InvocationTargetException,
        RebootException
    {
        return getEntryPointClass(rebootConfig.getEntryPoint());
    }

    public RebootAction getAction(String actionName) throws RebootException,
        InstantiationException, IllegalAccessException, ClassNotFoundException,
        NoSuchMethodException, InvocationTargetException
    {
        ActionConfig actionConfig = lookupActionConfig(new ActionReference(actionName));
        if (actionConfig == null)
        {
            if (parentConfigLoader == null)
            {
                throw new RebootException("Action not found: " + actionName);
            }
            return parentConfigLoader.getAction(actionName);
        }
        return getClassLoader(actionConfig.getModuleReference())
            .loadClass(actionConfig.getClassName()).asSubclass(RebootAction.class)
            .newInstance();
    }
}

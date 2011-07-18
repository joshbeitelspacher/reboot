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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.netbeetle.reboot.core.config.ActionConfig;
import com.netbeetle.reboot.core.config.ClassLoaderConfig;
import com.netbeetle.reboot.core.config.EntryPointConfig;
import com.netbeetle.reboot.core.config.ModuleConfig;
import com.netbeetle.reboot.core.config.RebootConfig;
import com.netbeetle.reboot.core.config.URIResolverConfig;

public class ApplicationContext
{
    private static final String SOURCE_CLASS_LOADER = "reboot-source-classloader";

    private final RebootConfig rebootConfig;
    private final Map<ModuleConfig, RebootClassLoader> classLoaders =
        new HashMap<ModuleConfig, RebootClassLoader>();
    private final Map<URIResolverConfig, URIResolver> uriResolvers =
        new HashMap<URIResolverConfig, URIResolver>();

    public ApplicationContext(RebootConfig rebootConfig)
    {
        this.rebootConfig = rebootConfig;
    }

    public RebootConfig getRebootConfig()
    {
        return rebootConfig;
    }

    private ModuleConfig lookupModuleConfig(String moduleId)
    {
        if (rebootConfig.getModules() == null)
        {
            return null;
        }
        for (ModuleConfig module : rebootConfig.getModules())
        {
            if (module.getId().equals(moduleId))
            {
                return module;
            }
        }
        return null;
    }

    private ClassLoaderConfig lookupClassLoaderConfig(String classLoaderId)
    {
        if (rebootConfig.getClassLoaders() == null)
        {
            return null;
        }
        for (ClassLoaderConfig classLoader : rebootConfig.getClassLoaders())
        {
            if (classLoader.getId().equals(classLoaderId))
            {
                return classLoader;
            }
        }
        return null;
    }

    private ActionConfig lookupActionConfig(String actionId)
    {
        if (rebootConfig.getActions() == null)
        {
            return null;
        }
        for (ActionConfig action : rebootConfig.getActions())
        {
            if (action.getId().equals(actionId))
            {
                return action;
            }
        }
        return null;
    }

    public RebootClassLoader getClassLoader(String moduleId) throws NoSuchMethodException,
        ClassNotFoundException, InstantiationException, IllegalAccessException,
        InvocationTargetException, RebootException
    {
        ModuleConfig module = lookupModuleConfig(moduleId);
        if (module == null)
        {
            throw new RebootException("Module not found: " + moduleId);
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
                for (String dependency : module.getDependencies())
                {
                    dependencies.add(getClassLoader(dependency));
                }
            }

            String moduleClassLoaderId = module.getClassLoaderId();

            RebootFileSystem fileSystem;
            if (module.getUris() != null)
            {
                fileSystem = getFileSystem(module.getUris());
            }
            else if (module.getSrcUris() != null)
            {
                fileSystem = getFileSystem(module.getSrcUris());
                if (moduleClassLoaderId == null)
                {
                    moduleClassLoaderId = SOURCE_CLASS_LOADER;
                }
            }
            else
            {
                throw new RebootException("No URI defined for " + module.getId());
            }

            RebootClassLoaderContext context =
                new RebootClassLoaderContext(module.getId(), fileSystem, dependencies, parent);

            RebootClassLoader classLoader;
            if (moduleClassLoaderId == null)
            {
                classLoader = new RebootClassLoader(context);
            }
            else
            {
                classLoader = getClassLoader(moduleClassLoaderId, context);
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

    public RebootFileSystem getFileSystem(List<URI> uris) throws InstantiationException,
        IllegalAccessException, ClassNotFoundException, NoSuchMethodException,
        InvocationTargetException, RebootException
    {
        if (uris.size() == 1)
        {
            return getFileSystem(uris.get(0));
        }

        List<RebootFileSystem> fileSystems = new ArrayList<RebootFileSystem>(uris.size());
        for (URI uri : uris)
        {
            fileSystems.add(getFileSystem(uri));
        }

        return new UnionFileSystem(fileSystems);
    }

    public RebootFileSystem getFileSystem(URI uri) throws InstantiationException,
        IllegalAccessException, ClassNotFoundException, NoSuchMethodException,
        InvocationTargetException, RebootException
    {
        if (uri.getScheme().equals("file"))
        {
            return FileURIResolver.getInstance().resolve(uri);
        }

        List<URIResolverConfig> uriResolverConfigs = rebootConfig.getUriResolvers();

        if (uriResolverConfigs != null)
        {
            String fullURIString = uri.toString();

            for (URIResolverConfig uriResolverConfig : uriResolverConfigs)
            {
                if (uriResolverConfig.getExpression().matcher(fullURIString).matches())
                {
                    URIResolver uriResolver = getURIResolver(uriResolverConfig);
                    return uriResolver.resolve(uri);
                }
            }
        }

        throw new RebootException("URI resolver not found for URI: " + uri);
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

    private RebootClassLoader getClassLoader(String classLoaderId,
        RebootClassLoaderContext context) throws InstantiationException,
        IllegalAccessException, ClassNotFoundException, NoSuchMethodException,
        InvocationTargetException, RebootException
    {
        ClassLoaderConfig classLoader = lookupClassLoaderConfig(classLoaderId);
        if (classLoader == null)
        {
            throw new RebootException("ClassLoader not found: " + classLoaderId);
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
        return getClassLoader(entryPoint.getModuleId()).loadClass(entryPoint.getClassName());
    }

    public Class<?> getEntryPointClass() throws ClassNotFoundException, NoSuchMethodException,
        InstantiationException, IllegalAccessException, InvocationTargetException,
        RebootException
    {
        return getEntryPointClass(rebootConfig.getEntryPoint());
    }

    public RebootAction getAction(String actionId) throws RebootException,
        InstantiationException, IllegalAccessException, ClassNotFoundException,
        NoSuchMethodException, InvocationTargetException
    {
        ActionConfig actionConfig = lookupActionConfig(actionId);
        if (actionConfig == null)
        {
            throw new RebootException("Action not found: " + actionId);
        }
        return getClassLoader(actionConfig.getModuleId())
            .loadClass(actionConfig.getClassName()).asSubclass(RebootAction.class)
            .newInstance();
    }
}

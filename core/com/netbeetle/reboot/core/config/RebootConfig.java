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

package com.netbeetle.reboot.core.config;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "reboot")
public class RebootConfig
{
    private EntryPointConfig entryPoint;
    private List<URIRewriteRuleConfig> uriRewriteRules;
    private List<URIResolverConfig> uriResolvers;
    private List<ClassLoaderConfig> classLoaders;
    private List<ActionConfig> actions;
    private List<ModuleConfig> modules;

    public EntryPointConfig getEntryPoint()
    {
        return entryPoint;
    }

    public void setEntryPoint(EntryPointConfig entryPoint)
    {
        this.entryPoint = entryPoint;
    }

    @XmlElementWrapper
    @XmlElement(name = "uriRewriteRule")
    public List<URIRewriteRuleConfig> getUriRewriteRules()
    {
        return uriRewriteRules;
    }

    public void setUriRewriteRules(List<URIRewriteRuleConfig> uriRewriteRules)
    {
        this.uriRewriteRules = uriRewriteRules;
    }

    @XmlElementWrapper
    @XmlElement(name = "uriResolver")
    public List<URIResolverConfig> getUriResolvers()
    {
        return uriResolvers;
    }

    public void setUriResolvers(List<URIResolverConfig> uriResolvers)
    {
        this.uriResolvers = uriResolvers;
    }

    @XmlElementWrapper
    @XmlElement(name = "classLoader")
    public List<ClassLoaderConfig> getClassLoaders()
    {
        return classLoaders;
    }

    public void setClassLoaders(List<ClassLoaderConfig> classLoaders)
    {
        this.classLoaders = classLoaders;
    }

    @XmlElementWrapper
    @XmlElement(name = "action")
    public List<ActionConfig> getActions()
    {
        return actions;
    }

    public void setActions(List<ActionConfig> actions)
    {
        this.actions = actions;
    }

    @XmlElementWrapper
    @XmlElement(name = "module")
    public List<ModuleConfig> getModules()
    {
        return modules;
    }

    public void setModules(List<ModuleConfig> modules)
    {
        this.modules = modules;
    }
}

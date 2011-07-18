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

import java.net.URI;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

public class ModuleConfig
{
    private String id;
    private List<URI> uris;
    private List<URI> srcUris;
    private String classLoaderId;
    private List<String> dependencies;

    public String getId()
    {
        return id;
    }

    public void setId(String id)
    {
        this.id = id;
    }

    @XmlElement(name = "uri")
    public List<URI> getUris()
    {
        return uris;
    }

    public void setUris(List<URI> uris)
    {
        this.uris = uris;
    }

    @XmlElement(name = "srcUri")
    public List<URI> getSrcUris()
    {
        return srcUris;
    }

    public void setSrcUris(List<URI> srcUris)
    {
        this.srcUris = srcUris;
    }

    public String getClassLoaderId()
    {
        return classLoaderId;
    }

    public void setClassLoaderId(String classLoaderId)
    {
        this.classLoaderId = classLoaderId;
    }

    @XmlElementWrapper
    @XmlElement(name = "moduleId")
    public List<String> getDependencies()
    {
        return dependencies;
    }

    public void setDependencies(List<String> dependencies)
    {
        this.dependencies = dependencies;
    }
}

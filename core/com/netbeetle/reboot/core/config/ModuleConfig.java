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
    private URI uri;
    private URI srcUri;
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

    public URI getUri()
    {
        return uri;
    }

    public void setUri(URI uri)
    {
        this.uri = uri;
    }

    public URI getSrcUri()
    {
        return srcUri;
    }

    public void setSrcUri(URI srcUri)
    {
        this.srcUri = srcUri;
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

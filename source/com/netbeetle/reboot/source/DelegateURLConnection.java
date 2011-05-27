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
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.Permission;
import java.util.List;
import java.util.Map;

public abstract class DelegateURLConnection extends URLConnection
{
    private final URLConnection connection;

    protected DelegateURLConnection(URLConnection connection)
    {
        super(connection.getURL());
        this.connection = connection;
    }

    @Override
    public int hashCode()
    {
        return connection.hashCode();
    }

    @Override
    public boolean equals(Object obj)
    {
        return connection.equals(obj);
    }

    @Override
    public void connect() throws IOException
    {
        connection.connect();
    }

    @Override
    public void setConnectTimeout(int timeout)
    {
        connection.setConnectTimeout(timeout);
    }

    @Override
    public int getConnectTimeout()
    {
        return connection.getConnectTimeout();
    }

    @Override
    public void setReadTimeout(int timeout)
    {
        connection.setReadTimeout(timeout);
    }

    @Override
    public int getReadTimeout()
    {
        return connection.getReadTimeout();
    }

    @Override
    public URL getURL()
    {
        return connection.getURL();
    }

    @Override
    public int getContentLength()
    {
        return connection.getContentLength();
    }

    @Override
    public String getContentType()
    {
        return connection.getContentType();
    }

    @Override
    public String getContentEncoding()
    {
        return connection.getContentEncoding();
    }

    @Override
    public long getExpiration()
    {
        return connection.getExpiration();
    }

    @Override
    public long getDate()
    {
        return connection.getDate();
    }

    @Override
    public long getLastModified()
    {
        return connection.getLastModified();
    }

    @Override
    public String getHeaderField(String name)
    {
        return connection.getHeaderField(name);
    }

    @Override
    public Map<String, List<String>> getHeaderFields()
    {
        return connection.getHeaderFields();
    }

    @Override
    public int getHeaderFieldInt(String name, int Default)
    {
        return connection.getHeaderFieldInt(name, Default);
    }

    @Override
    public long getHeaderFieldDate(String name, long Default)
    {
        return connection.getHeaderFieldDate(name, Default);
    }

    @Override
    public String getHeaderFieldKey(int n)
    {
        return connection.getHeaderFieldKey(n);
    }

    @Override
    public String getHeaderField(int n)
    {
        return connection.getHeaderField(n);
    }

    @Override
    public Object getContent() throws IOException
    {
        return connection.getContent();
    }

    @Override
    public Object getContent(@SuppressWarnings("rawtypes") Class[] classes) throws IOException
    {
        return connection.getContent(classes);
    }

    @Override
    public Permission getPermission() throws IOException
    {
        return connection.getPermission();
    }

    @Override
    public InputStream getInputStream() throws IOException
    {
        return connection.getInputStream();
    }

    @Override
    public OutputStream getOutputStream() throws IOException
    {
        return connection.getOutputStream();
    }

    @Override
    public String toString()
    {
        return connection.toString();
    }

    @Override
    public void setDoInput(boolean doinput)
    {
        connection.setDoInput(doinput);
    }

    @Override
    public boolean getDoInput()
    {
        return connection.getDoInput();
    }

    @Override
    public void setDoOutput(boolean dooutput)
    {
        connection.setDoOutput(dooutput);
    }

    @Override
    public boolean getDoOutput()
    {
        return connection.getDoOutput();
    }

    @Override
    public void setAllowUserInteraction(boolean allowuserinteraction)
    {
        connection.setAllowUserInteraction(allowuserinteraction);
    }

    @Override
    public boolean getAllowUserInteraction()
    {
        return connection.getAllowUserInteraction();
    }

    @Override
    public void setUseCaches(boolean usecaches)
    {
        connection.setUseCaches(usecaches);
    }

    @Override
    public boolean getUseCaches()
    {
        return connection.getUseCaches();
    }

    @Override
    public void setIfModifiedSince(long ifmodifiedsince)
    {
        connection.setIfModifiedSince(ifmodifiedsince);
    }

    @Override
    public long getIfModifiedSince()
    {
        return connection.getIfModifiedSince();
    }

    @Override
    public boolean getDefaultUseCaches()
    {
        return connection.getDefaultUseCaches();
    }

    @Override
    public void setDefaultUseCaches(boolean defaultusecaches)
    {
        connection.setDefaultUseCaches(defaultusecaches);
    }

    @Override
    public void setRequestProperty(String key, String value)
    {
        connection.setRequestProperty(key, value);
    }

    @Override
    public void addRequestProperty(String key, String value)
    {
        connection.addRequestProperty(key, value);
    }

    @Override
    public String getRequestProperty(String key)
    {
        return connection.getRequestProperty(key);
    }

    @Override
    public Map<String, List<String>> getRequestProperties()
    {
        return connection.getRequestProperties();
    }
}

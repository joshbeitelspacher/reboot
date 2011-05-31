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

package com.netbeetle.reboot.core.rbt;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

import com.netbeetle.reboot.core.RebootClassLoader;
import com.netbeetle.reboot.core.RebootFile;

public class RebootURLConnection extends URLConnection
{
    private final String moduleName;
    private final String path;
    private RebootFile file;

    public RebootURLConnection(URL url, String moduleName, String path)
    {
        super(url);
        this.moduleName = moduleName;
        this.path = path;
    }

    @Override
    public void connect() throws IOException
    {
        if (!connected)
        {
            RebootClassLoader classLoader = RebootClassLoader.getClassLoader(moduleName);
            if (classLoader == null)
            {
                throw new FileNotFoundException("Module not found: " + url);
            }
            file = classLoader.findRebootFile(path);
            if (file == null)
            {
                throw new FileNotFoundException("File not found: " + url);
            }
            connected = true;
        }
    }

    @Override
    public InputStream getInputStream() throws IOException
    {
        connect();
        return file.getInputStream();
    }
}

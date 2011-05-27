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

import static java.util.Arrays.asList;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZipURLConnection extends URLConnection
{
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private static final byte[] LINE_END = {'\n'};

    private boolean recursive;
    private final ZipFile zipFile;
    private final String path;
    private ZipEntry zipEntry;

    public ZipURLConnection(URL url, ZipFile zipFile, String path)
    {
        super(url);
        this.zipFile = zipFile;
        this.path = path;
    }

    @Override
    public void connect() throws IOException
    {
        if (!connected)
        {
            recursive = Boolean.parseBoolean(getRequestProperty("recursive"));
            zipEntry = zipFile.getEntry(path);
            if (zipEntry == null)
            {
                throw new FileNotFoundException(zipFile.getName() + "(entry \"" + path + "\")");
            }
            connected = true;
        }
    }

    @Override
    public InputStream getInputStream() throws IOException
    {
        connect();
        if (zipEntry.isDirectory())
        {
            return new CallbackInputStream()
            {
                private final Enumeration<? extends ZipEntry> entries = zipFile.entries();

                @Override
                protected List<byte[]> next()
                {
                    while (entries.hasMoreElements())
                    {
                        String next = entries.nextElement().getName();
                        if (next.startsWith(path))
                        {
                            next = next.substring(path.length());
                            if (next.isEmpty())
                            {
                                continue;
                            }
                            if (!recursive)
                            {
                                int slashIndex = next.indexOf('/');
                                if (slashIndex != -1 && slashIndex != next.length() - 1)
                                {
                                    continue;
                                }
                            }

                            return asList(next.getBytes(UTF8), LINE_END);
                        }
                    }
                    return null;
                }
            };
        }

        return zipFile.getInputStream(zipEntry);
    }
}

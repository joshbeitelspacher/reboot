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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

public class FileURLConnection extends URLConnection
{
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private static class PathAndFile
    {
        final byte[] path;
        final File file;

        PathAndFile(byte[] path, File file)
        {
            this.path = path;
            this.file = file;
        }
    }

    private boolean recursive;
    private boolean isDirectory;
    private URL rawURL;
    private File file;

    public FileURLConnection(URL url)
    {
        super(url);
    }

    @Override
    public void connect() throws IOException
    {
        if (!connected)
        {
            recursive = Boolean.parseBoolean(getRequestProperty("recursive"));
            String scheme = url.getProtocol();
            if (FileURIResolver.REBOOT_FILE_URL_SCHEME.equals(scheme))
            {
                rawURL =
                    new URL("file"
                        + url.toString().substring(
                            FileURIResolver.REBOOT_FILE_URL_SCHEME.length()));
            }
            else
            {
                rawURL = url;
            }

            isDirectory = rawURL.toString().endsWith("/");

            try
            {
                file = new File(rawURL.toURI());

                if (!file.exists())
                {
                    throw new FileNotFoundException(file.getAbsolutePath());
                }

                if (isDirectory)
                {
                    if (file.isFile())
                    {
                        throw new FileNotFoundException(file.getAbsolutePath()
                            + " (extra trailing slash)");
                    }

                    if (!file.isDirectory())
                    {
                        throw new FileNotFoundException(file.getAbsolutePath());
                    }
                }
                else
                {
                    if (file.isDirectory())
                    {
                        throw new FileNotFoundException(file.getAbsolutePath()
                            + " (missing trailing slash)");
                    }

                    if (!file.isFile())
                    {
                        throw new FileNotFoundException(file.getAbsolutePath());
                    }
                }
            }
            catch (URISyntaxException e)
            {
                throw new IOException("Invalid URL: " + url);
            }
            connected = true;
        }
    }

    @Override
    public InputStream getInputStream() throws IOException
    {
        connect();
        if (isDirectory)
        {
            final Deque<PathAndFile> files = new LinkedList<PathAndFile>();

            byte[] startingPath = new byte[0];
            File[] startingFiles = file.listFiles();
            for (int i = startingFiles.length - 1; i >= 0; i--)
            {
                files.push(new PathAndFile(startingPath, startingFiles[i]));
            }

            return new CallbackInputStream()
            {
                @Override
                protected List<byte[]> next() throws IOException
                {
                    while (!files.isEmpty())
                    {
                        PathAndFile next = files.pop();

                        if (next.file.isDirectory())
                        {
                            byte[] name = (next.file.getName() + '/').getBytes(UTF8);

                            if (recursive)
                            {
                                byte[] path = new byte[next.path.length + name.length];

                                System.arraycopy(next.path, 0, path, 0, next.path.length);
                                System.arraycopy(name, 0, path, next.path.length, name.length);

                                File[] nextFiles = next.file.listFiles();
                                for (int i = nextFiles.length - 1; i >= 0; i--)
                                {
                                    files.push(new PathAndFile(path, nextFiles[i]));
                                }
                            }

                            return asList(next.path, name);
                        }

                        return asList(next.path, next.file.getName().getBytes(UTF8));
                    }

                    return null;
                }
            };
        }

        return new FileInputStream(file);
    }
}

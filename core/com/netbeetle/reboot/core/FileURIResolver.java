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

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

public class FileURIResolver implements URIResolver
{
    public static final String REBOOT_FILE_URL_SCHEME = "rbt+file";
    public static final String REBOOT_ZIP_URL_SCHEME = "rbt+zip+file";

    private static class LazyLoader
    {
        private static final FileURIResolver INSTANCE = new FileURIResolver();
    }

    public static FileURIResolver getInstance()
    {
        return LazyLoader.INSTANCE;
    }

    private final Object lock = new Object();
    private final Map<URI, ZipFile> zipFiles = new ConcurrentHashMap<URI, ZipFile>();
    private final FileURLStreamHandler fileURLHandler = new FileURLStreamHandler();

    @Override
    public URL resolve(URI uri) throws MalformedURLException
    {
        String fullPath = uri.toURL().toString();

        if (!fullPath.startsWith("file:/"))
        {
            throw new RuntimeException("Can only resolve file URLs");
        }

        // remove the "file" scheme so that we can add our own
        fullPath = fullPath.substring(4);

        if (!fullPath.endsWith("/"))
        {
            fullPath += "/";
        }

        File file = new File(uri);

        if (file.isDirectory())
        {
            return new URL(null, REBOOT_FILE_URL_SCHEME + fullPath, fileURLHandler);
        }

        if (file.isFile())
        {
            ZipFile zipFile = zipFiles.get(uri);
            if (zipFile == null)
            {
                synchronized (lock)
                {
                    zipFile = zipFiles.get(uri);
                    if (zipFile == null)
                    {
                        try
                        {
                            zipFile = new ZipFile(file);
                        }
                        catch (ZipException e)
                        {
                            throw new RuntimeException(e);
                        }
                        catch (IOException e)
                        {
                            throw new RuntimeException(e);
                        }
                        zipFiles.put(uri, zipFile);
                    }
                }
            }

            String zipFilePath = REBOOT_ZIP_URL_SCHEME + fullPath;

            return new URL(null, zipFilePath, new ZipURLStreamHandler(zipFile, zipFilePath));
        }

        throw new RuntimeException("File not found");
    }
}

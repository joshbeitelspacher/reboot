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
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

public class FileURIResolver implements URIResolver
{
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

    @Override
    public RebootFileSystem resolve(URI uri) throws RebootException
    {
        if (!"file".equals(uri.getScheme()))
        {
            throw new RebootException("Can only resolve file URLs");
        }

        File file = new File(uri);

        if (file.isDirectory())
        {
            return new StandardFileSystem(file);
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
                            throw new RebootException(e);
                        }
                        catch (IOException e)
                        {
                            throw new RebootException(e);
                        }
                        zipFiles.put(uri, zipFile);
                    }
                }
            }

            return new ZipFileSystem(zipFile);
        }

        throw new RuntimeException("File not found");
    }
}

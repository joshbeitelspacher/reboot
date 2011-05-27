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

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.zip.ZipFile;

public class ZipURLStreamHandler extends URLStreamHandler
{
    private final ZipFile zipFile;
    private final String zipFileURL;

    public ZipURLStreamHandler(ZipFile zipFile, String zipFileURL)
    {
        this.zipFile = zipFile;
        this.zipFileURL = zipFileURL;
    }

    @Override
    protected URLConnection openConnection(URL url) throws IOException
    {
        if (!url.toString().startsWith(zipFileURL))
        {
            throw new IOException("Not found");
        }

        final String path = url.toString().substring(zipFileURL.length());

        return new ZipURLConnection(url, zipFile, path);
    }
}

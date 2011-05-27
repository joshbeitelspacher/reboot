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

package com.netbeetle.reboot.http;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;

import com.netbeetle.reboot.core.FileURIResolver;
import com.netbeetle.reboot.core.URIResolver;

public class HttpURIResolver implements URIResolver
{
    private final HttpURLStreamHandler handler = new HttpURLStreamHandler(this);

    @Override
    public URL resolve(URI uri) throws MalformedURLException
    {
        URL url = uri.toURL();
        String file = url.getFile();
        if (file != null && file.endsWith("/"))
        {
            return new URL(null, url.toString(), handler);
        }

        File cachedFile = getCachedFile(url);
        if (!cachedFile.exists())
        {
            try
            {
                download(url, cachedFile);
            }
            catch (IOException e)
            {
                throw new RuntimeException("Error resolving " + uri);
            }
        }

        return FileURIResolver.getInstance().resolve(cachedFile.toURI());
    }

    private File getCachedFile(URL url)
    {
        String rebootCacheString = System.getProperty("com.netbeetle.reboot.cache");
        if (rebootCacheString == null)
        {
            rebootCacheString = System.getProperty("user.home") + "/.reboot/cache";
        }

        File rebootCacheDir = new File(rebootCacheString);

        rebootCacheDir.mkdirs();

        String urlString = url.toString();

        StringBuilder builder = new StringBuilder(urlString.length());
        for (int i = 0; i < urlString.length(); i++)
        {
            char c = urlString.charAt(i);
            if (c < ' ' || c == '"' || c == '%' || c == '*' || c == ':' || c == '<' || c == '>'
                || c == '?' || c == '\\' || c == '|' || c > '~')
            {
                builder.append('_');
            }
            else
            {
                builder.append(c);
            }
        }

        String filename = builder.toString().replaceAll("_*/_*", "/");

        return new File(rebootCacheDir, filename);
    }

    private void download(URL url, File file) throws IOException
    {
        InputStream input = url.openStream();
        try
        {
            file.getParentFile().mkdirs();
            FileChannel channel = new FileOutputStream(file).getChannel();
            try
            {
                channel.transferFrom(Channels.newChannel(input), 0, Long.MAX_VALUE);
            }
            finally
            {
                channel.close();
            }
        }
        finally
        {
            input.close();
        }
    }
}

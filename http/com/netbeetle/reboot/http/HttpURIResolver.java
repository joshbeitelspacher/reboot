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
import java.net.URI;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;

import com.netbeetle.reboot.core.FileURIResolver;
import com.netbeetle.reboot.core.Reboot;
import com.netbeetle.reboot.core.RebootException;
import com.netbeetle.reboot.core.RebootFileSystem;
import com.netbeetle.reboot.core.URIResolver;

public class HttpURIResolver implements URIResolver
{
    @Override
    public RebootFileSystem resolve(URI uri) throws RebootException
    {
        try
        {
            URL url = uri.toURL();

            File cachedFile = Reboot.getCacheLocation(uri.toString());
            if (!cachedFile.exists())
            {
                download(url, cachedFile);
            }

            return FileURIResolver.getInstance().resolve(cachedFile.toURI());
        }
        catch (IOException e)
        {
            throw new RebootException("Error resolving " + uri, e);
        }
    }

    private void download(URL url, File file) throws IOException
    {
        Reboot.info("Downloading " + url);
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
            Reboot.info("Finished downloading " + url);
        }
        finally
        {
            input.close();
        }
    }
}

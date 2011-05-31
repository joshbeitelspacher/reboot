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

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

public class Handler extends URLStreamHandler
{
    @Override
    protected URLConnection openConnection(URL url) throws IOException
    {
        if (!url.getProtocol().equals("rbt"))
        {
            throw new IOException("Protocol must be rbt");
        }

        String rawUrl = url.toString();
        if (rawUrl.charAt(4) != '/')
        {
            throw new IOException("Badly formed rbt url: " + rawUrl);
        }

        int slashIndex = rawUrl.indexOf('/', 5);
        if (slashIndex == -1)
        {
            throw new IOException("Badly formed rbt url: " + rawUrl);
        }

        String moduleName = rawUrl.substring(5, slashIndex);
        String path = rawUrl.substring(slashIndex + 1);

        return new RebootURLConnection(url, moduleName, path);
    }
}

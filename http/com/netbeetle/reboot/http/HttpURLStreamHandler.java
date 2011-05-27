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

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

import com.netbeetle.reboot.core.URIResolver;

public class HttpURLStreamHandler extends URLStreamHandler
{
    private final URIResolver resolver;

    public HttpURLStreamHandler(URIResolver resolver)
    {
        this.resolver = resolver;
    }

    @Override
    protected URLConnection openConnection(URL url) throws IOException
    {
        try
        {
            return resolver.resolve(url.toURI()).openConnection();
        }
        catch (URISyntaxException e)
        {
            throw new IOException(e);
        }
    }
}

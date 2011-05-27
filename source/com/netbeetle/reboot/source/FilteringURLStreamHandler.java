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

package com.netbeetle.reboot.source;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.Charset;
import java.util.Arrays;

import com.netbeetle.reboot.core.CallbackInputStream;

public class FilteringURLStreamHandler extends URLStreamHandler
{
    private static final byte[] NEW_LINE = new byte[] {'\n'};
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final URL originalURL;

    public FilteringURLStreamHandler(URL url)
    {
        originalURL = url;
    }

    @Override
    protected URLConnection openConnection(URL url) throws IOException
    {
        return new DelegateURLConnection(originalURL.openConnection())
        {
            @Override
            public CallbackInputStream getInputStream() throws IOException
            {
                InputStream original = super.getInputStream();
                final BufferedReader reader =
                    new BufferedReader(new InputStreamReader(original, UTF8));
                return new CallbackInputStream()
                {
                    @Override
                    protected Iterable<byte[]> next() throws IOException
                    {
                        String line = reader.readLine();
                        while (line != null)
                        {
                            if (!line.endsWith(".java"))
                            {
                                return Arrays.asList(line.getBytes(UTF8), NEW_LINE);
                            }
                            line = reader.readLine();
                        }
                        return null;
                    }

                    @Override
                    public void close() throws IOException
                    {
                        try
                        {
                            super.close();
                        }
                        finally
                        {
                            reader.close();
                        }
                    }
                };
            }
        };
    }
}

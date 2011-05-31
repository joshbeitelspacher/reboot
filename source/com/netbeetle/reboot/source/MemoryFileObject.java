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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.ByteBuffer;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.tools.JavaFileObject;

public class MemoryFileObject implements JavaFileObject
{
    private final URI uri;
    private final String className;
    private final Kind kind;
    private byte[] content;

    public MemoryFileObject(URI uri, String className, Kind kind)
    {
        this.uri = uri;
        this.className = className;
        this.kind = kind;
        this.content = null;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException
    {
        if (content == null)
        {
            return null;
        }
        return CharsetUtil.getUTF8Decoder(ignoreEncodingErrors)
            .decode(ByteBuffer.wrap(content)).toString();
    }

    @Override
    public InputStream openInputStream() throws IOException
    {
        return new ByteArrayInputStream(content);
    }

    @Override
    public Reader openReader(boolean ignoreEncodingErrors) throws IOException
    {
        return new InputStreamReader(openInputStream(),
            CharsetUtil.getUTF8Decoder(ignoreEncodingErrors));
    }

    @Override
    public OutputStream openOutputStream() throws IOException
    {
        return new ByteArrayOutputStream()
        {
            @Override
            public void close() throws IOException
            {
                content = toByteArray();
            }
        };
    }

    @Override
    public Writer openWriter() throws IOException
    {
        return new StringWriter()
        {
            @Override
            public void close() throws IOException
            {
                content = toString().getBytes(CharsetUtil.UTF8);
            }
        };
    }

    public byte[] getContent()
    {
        return content;
    }

    @Override
    public URI toUri()
    {
        return uri;
    }

    @Override
    public String getName()
    {
        return uri.toString();
    }

    @Override
    public long getLastModified()
    {
        return 0;
    }

    @Override
    public boolean delete()
    {
        return false;
    }

    @Override
    public Kind getKind()
    {
        return kind;
    }

    @Override
    public boolean isNameCompatible(String simpleName, Kind otherKind)
    {
        return (className.equals(simpleName) || className.endsWith("." + simpleName))
            && kind.equals(otherKind);
    }

    @Override
    public NestingKind getNestingKind()
    {
        return null;
    }

    @Override
    public Modifier getAccessLevel()
    {
        return null;
    }

    @Override
    public String toString()
    {
        return getName();
    }

    public String getClassName()
    {
        return className;
    }
}

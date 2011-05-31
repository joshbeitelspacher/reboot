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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.tools.JavaFileObject;

import com.netbeetle.reboot.core.RebootFile;

public class RebootFileObject implements JavaFileObject
{
    private final RebootFile file;
    private final URI uri;
    private final String className;
    private final Kind kind;

    public RebootFileObject(RebootFile file, URI uri, String className, Kind kind)
    {
        this.file = file;
        this.uri = uri;
        this.className = className;
        this.kind = kind;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException
    {
        StringBuilder builder = new StringBuilder();
        char[] buffer = new char[2048];
        Reader reader = null;
        try
        {
            reader = openReader(ignoreEncodingErrors);
            int read = reader.read(buffer);
            while (read != -1)
            {
                builder.append(buffer, 0, read);
                read = reader.read(buffer);
            }
            reader.close();
            reader = null;
            return builder.toString();
        }
        finally
        {
            if (reader != null)
            {
                try
                {
                    reader.close();
                }
                catch (IOException e)
                {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    @Override
    public InputStream openInputStream() throws IOException
    {
        return file.getInputStream();
    }

    @Override
    public Reader openReader(boolean ignoreEncodingErrors) throws IOException
    {
        return new InputStreamReader(openInputStream(),
            CharsetUtil.getUTF8Decoder(ignoreEncodingErrors));
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
    public OutputStream openOutputStream() throws IOException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Writer openWriter() throws IOException
    {
        throw new UnsupportedOperationException();
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
        return kind.equals(otherKind)
            && (className.equals(simpleName) || className.endsWith("." + simpleName));
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

    public String getFileName()
    {
        return file.getName();
    }
}

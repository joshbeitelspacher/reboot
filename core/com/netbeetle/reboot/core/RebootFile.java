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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;

public abstract class RebootFile
{
    private final String name;
    private final boolean directory;

    public RebootFile(String name, boolean directory)
    {
        if (directory)
        {
            if (!name.endsWith("/") && !name.isEmpty())
            {
                this.name = name + '/';
            }
            else
            {
                this.name = name;
            }
        }
        else
        {
            if (name.endsWith("/"))
            {
                this.name = name.substring(0, name.length() - 1);
            }
            else
            {
                this.name = name;
            }
        }
        this.directory = directory;
    }

    public String getName()
    {
        return name;
    }

    public boolean isFile()
    {
        return !directory;
    }

    public boolean isDirectory()
    {
        return directory;
    }

    public long getSize()
    {
        return -1;
    }

    public byte[] getBytes() throws IOException
    {
        long size = getSize();
        if (size == -1)
        {
            InputStream inputStream = null;
            ByteArrayOutputStream outputStream = null;
            try
            {
                inputStream = getInputStream();
                outputStream = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int bytesRead = inputStream.read(buffer);
                while (bytesRead != -1)
                {
                    outputStream.write(buffer, 0, bytesRead);
                    bytesRead = inputStream.read(buffer);
                }
                byte[] bytes = outputStream.toByteArray();
                inputStream.close();
                inputStream = null;
                outputStream.close();
                outputStream = null;
                return bytes;
            }
            finally
            {
                if (inputStream != null)
                {
                    try
                    {
                        inputStream.close();
                    }
                    catch (IOException e)
                    {
                        // do nothing
                    }
                }
                if (outputStream != null)
                {
                    try
                    {
                        outputStream.close();
                    }
                    catch (IOException e)
                    {
                        // do nothing
                    }
                }
            }
        }
        else if (size > Integer.MAX_VALUE)
        {
            throw new IOException("File too large to be loaded as bytes");
        }
        else
        {
            int intSize = (int) size;
            byte[] bytes = new byte[intSize];
            InputStream inputStream = null;
            try
            {
                inputStream = getInputStream();
                int offset = 0;
                while (offset != intSize)
                {
                    int bytesRead = inputStream.read(bytes, offset, intSize - offset);
                    if (bytesRead > 0)
                    {
                        offset += bytesRead;
                    }
                    else
                    {
                        throw new IOException("File shorter than expected");
                    }
                }
                if (inputStream.read() != -1)
                {
                    throw new IOException("File longer than expected");
                }
                inputStream.close();
                inputStream = null;
                return bytes;
            }
            finally
            {
                if (inputStream != null)
                {
                    try
                    {
                        inputStream.close();
                    }
                    catch (IOException e)
                    {
                        // do nothing
                    }
                }
            }
        }
    }

    public InputStream getInputStream() throws IOException
    {
        return new ByteArrayInputStream(getBytes());
    }

    /**
     * Lists the contents of a directory. If this method is called on a regular
     * file and empty list will be returned.
     * 
     * @param recursive
     *            true if files in subdirectories should be included in the list
     * @throws IOException
     *             if a list of files cannot be retrieved
     */
    public Collection<RebootFile> list(boolean recursive) throws IOException
    {
        return Collections.emptyList();
    }

    @Override
    public String toString()
    {
        return name;
    }
}

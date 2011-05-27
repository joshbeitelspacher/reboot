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
import java.io.InputStream;
import java.util.Collections;
import java.util.Iterator;

public abstract class CallbackInputStream extends InputStream
{
    private Iterator<byte[]> nextBuffers = Collections.<byte[]> emptyList().iterator();
    private byte[] buffer;
    private int offset;
    private boolean done;

    @Override
    public int read(byte[] b, int off, int len) throws IOException
    {
        if (done)
        {
            return -1;
        }

        if (buffer == null)
        {
            do
            {
                while (!nextBuffers.hasNext())
                {
                    Iterable<byte[]> nextIterable = next();
                    if (nextIterable == null)
                    {
                        done = true;
                        return -1;
                    }
                    nextBuffers = nextIterable.iterator();
                }

                buffer = nextBuffers.next();
            }
            while (buffer.length == 0);
        }

        int writableLen = Math.min(buffer.length - offset, len);

        System.arraycopy(buffer, offset, b, off, writableLen);

        offset += writableLen;
        if (offset == buffer.length)
        {
            buffer = null;
            offset = 0;
        }

        return writableLen;
    }

    @Override
    public int read() throws IOException
    {
        byte[] singleByte = new byte[1];
        if (read(singleByte) != -1)
        {
            return singleByte[0];
        }
        return -1;
    }

    @Override
    public void close() throws IOException
    {
        done = true;
    }

    protected abstract Iterable<byte[]> next() throws IOException;
}

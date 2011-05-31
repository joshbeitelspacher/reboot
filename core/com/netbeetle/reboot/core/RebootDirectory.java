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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collection;

public abstract class RebootDirectory extends RebootFile
{
    public RebootDirectory(String name)
    {
        super(name, true);
    }

    @Override
    public byte[] getBytes() throws IOException
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Charset utf8 = Charset.forName("UTF-8");
        int nameLength = getName().length();
        for (RebootFile file : list(false))
        {
            bytes.write(file.getName().substring(nameLength).getBytes(utf8));
            bytes.write('\n');
        }
        return bytes.toByteArray();
    }

    @Override
    public abstract Collection<RebootFile> list(boolean recursive) throws IOException;
}

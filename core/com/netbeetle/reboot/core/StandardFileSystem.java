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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

public class StandardFileSystem implements RebootFileSystem
{
    private static class StandardDirectory extends RebootDirectory
    {
        private final File file;

        public StandardDirectory(String name, File file)
        {
            super(name);
            this.file = file;
        }

        @Override
        public Collection<RebootFile> list(boolean recursive) throws IOException
        {
            Deque<File> stack = new LinkedList<File>();
            stack.addAll(Arrays.asList(file.listFiles()));
            List<RebootFile> contents = new ArrayList<RebootFile>();
            while (!stack.isEmpty())
            {
                File next = stack.pop();
                if (next.isDirectory())
                {
                    contents.add(new StandardDirectory(getName() + next.getName() + '/', next));
                    if (recursive)
                    {
                        File[] nextFiles = next.listFiles();
                        for (int i = nextFiles.length - 1; i >= 0; i--)
                        {
                            stack.push(nextFiles[i]);
                        }
                    }
                }
                else if (next.isFile())
                {
                    contents.add(new StandardFile(getName() + next.getName(), next));
                }
            }
            return contents;
        }
    }

    private static class StandardFile extends RebootStreamFile
    {
        private final File file;

        public StandardFile(String name, File file)
        {
            super(name);
            this.file = file;
        }

        @Override
        public InputStream getInputStream() throws FileNotFoundException
        {
            return new FileInputStream(file);
        }

        @Override
        public long getSize()
        {
            return file.length();
        }
    }

    private final File base;

    public StandardFileSystem(File base)
    {
        this.base = base;
    }

    @Override
    public RebootFile getFile(String name) throws IOException
    {
        if (name.isEmpty())
        {
            return new StandardDirectory(name, base);
        }

        File file = new File(base, name);
        if (file.isFile())
        {
            return new StandardFile(name, file);
        }
        else if (file.isDirectory())
        {
            return new StandardDirectory(name, file);
        }
        else
        {
            return null;
        }
    }

    @Override
    public String fingerprint() throws IOException
    {
        return null;
    }
}

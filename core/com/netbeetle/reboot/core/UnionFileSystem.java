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
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UnionFileSystem implements RebootFileSystem
{
    private static class UnionDirectory extends RebootDirectory
    {
        private final List<RebootDirectory> directories;

        public UnionDirectory(String name, List<RebootDirectory> directories)
        {
            super(name);
            this.directories = directories;
        }

        @Override
        public Collection<RebootFile> list(boolean recursive) throws IOException
        {
            Map<String, RebootFile> contents = new LinkedHashMap<String, RebootFile>();
            Map<String, List<RebootDirectory>> subDirectories =
                new HashMap<String, List<RebootDirectory>>();
            for (RebootDirectory directory : directories)
            {
                for (RebootFile file : directory.list(recursive))
                {
                    String name = file.getName();

                    // the directories could contain matching subdirectories, so
                    // directories need to be handled specially
                    if (file.isDirectory())
                    {
                        // chop off the trailing slash to ensure that we won't
                        // list both a directory and a file with the same name
                        name = name.substring(0, name.length() - 1);

                        // if no matching file or directory has been found
                        if (!contents.containsKey(name))
                        {
                            // insert the directory
                            contents.put(name, file);

                            // remember this directory
                            List<RebootDirectory> matchingDirectories =
                                new ArrayList<RebootDirectory>();
                            matchingDirectories.add((RebootDirectory) file);
                            subDirectories.put(name, matchingDirectories);
                        }

                        // otherwise if a directory has already been found
                        else if (contents.get(name).isDirectory())
                        {
                            // remember the new directory
                            List<RebootDirectory> matchingDirectories =
                                subDirectories.get(name);
                            matchingDirectories.add((RebootDirectory) file);
                        }
                    }

                    // add newly found files to the list
                    else if (!contents.containsKey(name))
                    {
                        contents.put(name, file);
                    }
                }
            }
            for (Map.Entry<String, List<RebootDirectory>> entry : subDirectories.entrySet())
            {
                if (entry.getValue().size() > 1)
                {
                    contents.put(entry.getKey(),
                        new UnionDirectory(entry.getKey() + '/', entry.getValue()));
                }
            }
            return contents.values();
        }
    }

    private final List<RebootFileSystem> fileSystems;

    public UnionFileSystem(List<RebootFileSystem> fileSystems)
    {
        this.fileSystems = fileSystems;
    }

    @Override
    public RebootFile getFile(String name) throws IOException
    {
        List<RebootDirectory> directories = new ArrayList<RebootDirectory>();
        for (RebootFileSystem fileSystem : fileSystems)
        {
            RebootFile file = fileSystem.getFile(name);
            if (file == null)
            {
                continue;
            }

            if (file.isFile())
            {
                return file;
            }

            directories.add((RebootDirectory) file);
        }

        switch (directories.size())
        {
            case 0:
                return null;
            case 1:
                return directories.get(0);
            default:
                return new UnionDirectory(name, directories);
        }
    }

    @Override
    public String fingerprint() throws IOException
    {
        StringBuilder builder = new StringBuilder();
        for (RebootFileSystem fileSystem : fileSystems)
        {
            String fingerprint = fileSystem.fingerprint();

            // if any of the filesystems can't be fingerprinted then the union
            // cannot be fingerprinted
            if (fingerprint == null)
            {
                return null;
            }

            builder.append(fingerprint);
        }

        try
        {
            return HashUtil.hash(builder.toString());
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IOException("Unable to compute hash", e);
        }
    }
}

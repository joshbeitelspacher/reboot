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

package com.netbeetle.reboot.git;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.treewalk.TreeWalk;

import com.netbeetle.reboot.core.RebootDirectory;
import com.netbeetle.reboot.core.RebootFile;
import com.netbeetle.reboot.core.RebootFileSystem;
import com.netbeetle.reboot.core.RebootStreamFile;

public class GitFileSystem implements RebootFileSystem
{
    private final CachedRepository cachedRepository;
    private final ObjectId treeId;

    private class GitDirectory extends RebootDirectory
    {
        private final ObjectId directoryTreeId;

        public GitDirectory(String name, ObjectId directoryTreeId)
        {
            super(name);
            this.directoryTreeId = directoryTreeId;
        }

        @Override
        public Collection<RebootFile> list(boolean recursive) throws IOException
        {
            List<RebootFile> contents = new ArrayList<RebootFile>();
            TreeWalk treeWalk = cachedRepository.openTree(directoryTreeId, recursive);
            try
            {
                while (treeWalk.next())
                {
                    FileMode fileMode = treeWalk.getFileMode(0);
                    if (fileMode.getObjectType() == Constants.OBJ_TREE)
                    {
                        contents.add(new GitDirectory(getName() + treeWalk.getPathString(),
                            treeWalk.getObjectId(0)));
                    }
                    else if (fileMode.getObjectType() == Constants.OBJ_BLOB
                        && (fileMode == FileMode.REGULAR_FILE || fileMode == FileMode.EXECUTABLE_FILE))
                    {
                        contents.add(new GitFile(getName() + treeWalk.getPathString(), treeWalk
                            .getObjectId(0)));
                    }
                }
            }
            finally
            {
                treeWalk.release();
            }
            return contents;
        }
    }

    private class GitFile extends RebootStreamFile
    {
        private final ObjectId blobId;

        public GitFile(String name, ObjectId blobId)
        {
            super(name);
            this.blobId = blobId;
        }

        @Override
        public InputStream getInputStream() throws IOException
        {
            return cachedRepository.open(blobId);
        }
    }

    public GitFileSystem(CachedRepository cachedRepository, ObjectId treeId)
    {
        this.cachedRepository = cachedRepository;
        this.treeId = treeId;
    }

    @Override
    public RebootFile getFile(String name) throws IOException
    {
        if (name.isEmpty())
        {
            return new GitDirectory(name, treeId);
        }

        if (name.endsWith("/"))
        {
            String path = name.substring(0, name.length() - 1);

            ObjectId directoryTreeId = cachedRepository.lookupTree(treeId, path);
            if (directoryTreeId == null)
            {
                return null;
            }

            return new GitDirectory(name, directoryTreeId);
        }

        ObjectId blobId = cachedRepository.lookupBlob(treeId, name);
        if (blobId == null)
        {
            return null;
        }

        return new GitFile(name, blobId);
    }

    @Override
    public String fingerprint()
    {
        return treeId.name();
    }
}

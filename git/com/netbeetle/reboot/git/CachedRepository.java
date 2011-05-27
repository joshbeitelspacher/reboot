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

import static java.util.Arrays.asList;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.treewalk.TreeWalk;

import com.netbeetle.reboot.core.CallbackInputStream;

public class CachedRepository
{
    private static final byte[] LINE_END = new byte[] {'\n'};
    private static final byte[] SLASH_AND_LINE_END = new byte[] {'/', '\n'};

    private final String url;
    private final Repository repository;
    private final ConcurrentNavigableMap<String, RevTree> trees =
        new ConcurrentSkipListMap<String, RevTree>();

    public CachedRepository(String url, Repository repository)
    {
        this.url = url;
        this.repository = repository;
    }

    public String getUrl()
    {
        return url;
    }

    public Repository getRepository()
    {
        return repository;
    }

    public FetchResult fetch() throws IOException, URISyntaxException, InvalidRemoteException
    {
        if (!repository.getDirectory().exists())
        {
            repository.create(true);
            StoredConfig config = repository.getConfig();
            RemoteConfig remoteConfig = new RemoteConfig(config, "origin");
            remoteConfig.addURI(new URIish(url));
            remoteConfig.setMirror(true);
            remoteConfig.addFetchRefSpec(new RefSpec().setForceUpdate(true)
                .setSourceDestination("refs/*", "refs/*"));
            remoteConfig.update(config);
            config.save();
        }

        return new Git(repository).fetch().setRemote("origin").setRemoveDeletedRefs(true)
            .setTimeout(120).call();
    }

    public ObjectId lookup(String revisionAndPath) throws IOException
    {
        TreeAndPath treeAndPath = findTreeAndPath(revisionAndPath);

        String path = treeAndPath.getPath();
        if (path.length() == 0)
        {
            throw new IOException("Path required");
        }

        boolean isDirectory;
        if (path.endsWith("/"))
        {
            path = path.substring(0, path.length() - 1);
            isDirectory = true;
        }
        else
        {
            isDirectory = false;
        }

        TreeWalk treeWalk = TreeWalk.forPath(repository, path, treeAndPath.getTree());
        if (treeWalk == null)
        {
            throw new IOException("Path not found");
        }
        try
        {
            String pathFound = treeWalk.getPathString();
            if (!path.equals(pathFound))
            {
                throw new IOException("Path found is \"" + pathFound + "\" but we wanted \""
                    + path + "\"");
            }

            FileMode fileMode = treeWalk.getFileMode(0);
            if (isDirectory)
            {
                if (fileMode.getObjectType() != Constants.OBJ_TREE)
                {
                    throw new IOException("Path \"" + path
                        + "\" does not reference a directory");
                }
            }
            else if (fileMode.getObjectType() != Constants.OBJ_BLOB
                || (fileMode != FileMode.REGULAR_FILE && fileMode != FileMode.EXECUTABLE_FILE))
            {
                throw new IOException("Path \"" + path + "\" does not reference a file");
            }

            return treeWalk.getObjectId(0);
        }
        finally
        {
            treeWalk.release();
        }
    }

    public InputStream open(ObjectId blobId) throws IOException
    {
        return repository.getObjectDatabase().open(blobId).openStream();
    }

    public InputStream openTree(ObjectId blobId, boolean recursive)
        throws MissingObjectException, IncorrectObjectTypeException, CorruptObjectException,
        IOException
    {
        final TreeWalk x = new TreeWalk(repository.getObjectDatabase().newReader());
        x.reset(blobId);
        x.setRecursive(recursive);

        return new CallbackInputStream()
        {
            @Override
            protected List<byte[]> next() throws IOException
            {
                if (x.next())
                {
                    if (x.isSubtree())
                    {
                        return asList(x.getRawPath(), SLASH_AND_LINE_END);
                    }
                    return asList(x.getRawPath(), LINE_END);
                }

                x.release();
                return null;
            }

            @Override
            public void close() throws IOException
            {
                x.release();
                super.close();
            }
        };
    }

    private TreeAndPath findTreeAndPath(String revisionAndPath) throws IOException
    {
        Map.Entry<String, RevTree> entry = trees.floorEntry(revisionAndPath);
        if (entry != null && revisionAndPath.startsWith(entry.getKey()))
        {
            return new TreeAndPath(entry.getValue(), revisionAndPath.substring(entry.getKey()
                .length() + 1));
        }

        int index = revisionAndPath.indexOf('/');

        String revision;
        ObjectId commitId = null;
        for (;;)
        {
            revision = revisionAndPath.substring(0, index);
            commitId = repository.resolve(revision);
            if (commitId != null)
            {
                break;
            }
            index = revisionAndPath.indexOf('/', index + 1);
            if (index == -1)
            {
                throw new IOException("Revision not found in repository");
            }
        }

        RevWalk revWalk = new RevWalk(repository);
        RevTree tree;
        try
        {
            RevCommit commit = revWalk.parseCommit(commitId);
            tree = commit.getTree();
        }
        finally
        {
            revWalk.release();
        }

        RevTree oldTree = trees.putIfAbsent(new String(revision), tree);
        if (oldTree != null)
        {
            tree = oldTree;
        }

        return new TreeAndPath(tree, revisionAndPath.substring(index + 1));
    }
}

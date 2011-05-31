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
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
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

public class CachedRepository
{
    private final String uri;
    private final Repository repository;
    private final ConcurrentNavigableMap<String, RevTree> revisions =
        new ConcurrentSkipListMap<String, RevTree>();

    public CachedRepository(String uri, Repository repository)
    {
        this.uri = uri;
        this.repository = repository;
    }

    public String getUri()
    {
        return uri;
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
            remoteConfig.addURI(new URIish(uri));
            remoteConfig.setMirror(true);
            remoteConfig.addFetchRefSpec(new RefSpec().setForceUpdate(true)
                .setSourceDestination("refs/*", "refs/*"));
            remoteConfig.update(config);
            config.save();
        }

        return new Git(repository).fetch().setRemote("origin").setRemoveDeletedRefs(true)
            .setTimeout(120).call();
    }

    public ObjectId lookupTree(String revisionAndPath) throws IOException
    {
        RevTree tree;
        String path;

        Map.Entry<String, RevTree> entry = revisions.floorEntry(revisionAndPath);
        if (entry != null && revisionAndPath.startsWith(entry.getKey()))
        {
            tree = entry.getValue();
            path = revisionAndPath.substring(entry.getKey().length());
        }
        else
        {
            int index = revisionAndPath.indexOf('/');

            String revision;
            ObjectId commitId;
            while (true)
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
                    return null;
                }
            }

            RevWalk revWalk = new RevWalk(repository);
            try
            {
                RevCommit commit = revWalk.parseCommit(commitId);
                tree = commit.getTree();
            }
            finally
            {
                revWalk.release();
            }

            RevTree oldTree = revisions.putIfAbsent(revision + '/', tree);
            if (oldTree != null)
            {
                tree = oldTree;
            }

            path = revisionAndPath.substring(index + 1);
        }

        return lookupTree(tree, path);
    }

    public ObjectId lookupTree(ObjectId tree, String path) throws IOException
    {
        TreeWalk treeWalk = TreeWalk.forPath(repository, path, tree);
        if (treeWalk == null)
        {
            return null;
        }
        try
        {
            FileMode fileMode = treeWalk.getFileMode(0);
            if (fileMode.getObjectType() != Constants.OBJ_TREE)
            {
                return null;
            }

            return treeWalk.getObjectId(0);
        }
        finally
        {
            treeWalk.release();
        }
    }

    public ObjectId lookupBlob(ObjectId tree, String path) throws IOException
    {
        TreeWalk treeWalk = TreeWalk.forPath(repository, path, tree);
        if (treeWalk == null)
        {
            return null;
        }
        try
        {
            FileMode fileMode = treeWalk.getFileMode(0);
            if (fileMode.getObjectType() != Constants.OBJ_BLOB
                || (fileMode != FileMode.REGULAR_FILE && fileMode != FileMode.EXECUTABLE_FILE))
            {
                return null;
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

    public TreeWalk openTree(ObjectId treeId, boolean recursive) throws IOException
    {
        TreeWalk treeWalk = new TreeWalk(repository.getObjectDatabase().newReader());
        treeWalk.reset(treeId);
        treeWalk.setRecursive(recursive);
        return treeWalk;
    }
}

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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepository;

import com.netbeetle.reboot.core.Reboot;
import com.netbeetle.reboot.core.RebootException;
import com.netbeetle.reboot.core.URIResolver;

public class GitURIResolver implements URIResolver
{
    private final Object lock = new Object();
    private final Map<String, CachedRepository> repositories =
        new ConcurrentHashMap<String, CachedRepository>();

    @Override
    public GitFileSystem resolve(URI uri) throws RebootException
    {
        String uriString = uri.toString();
        int start = uriString.startsWith("git+") ? 4 : 0;
        int end = uriString.indexOf(".git/") + 4;
        String repositoryURI = uriString.substring(start, end);

        CachedRepository cachedRepository = repositories.get(repositoryURI);
        if (cachedRepository == null)
        {
            synchronized (lock)
            {
                cachedRepository = repositories.get(repositoryURI);
                if (cachedRepository == null)
                {
                    try
                    {
                        cachedRepository = getRepository(repositoryURI);
                    }
                    catch (IOException e)
                    {
                        throw new RebootException("Unable to retrieve repository", e);
                    }
                    repositories.put(repositoryURI, cachedRepository);
                }
            }
        }

        String revisionAndPath = uriString.substring(end + 1);
        if (revisionAndPath.endsWith("/"))
        {
            revisionAndPath = revisionAndPath.substring(0, revisionAndPath.length() - 1);
        }

        try
        {
            ObjectId treeId = cachedRepository.lookupTree(revisionAndPath);
            if (treeId == null)
            {
                throw new RebootException("Unable to find " + revisionAndPath
                    + " in repository");
            }

            return new GitFileSystem(cachedRepository, treeId);
        }
        catch (IOException e)
        {
            throw new RebootException("Unable to find " + revisionAndPath + " in repository", e);
        }
    }

    private CachedRepository getRepository(String repositoryURI) throws IOException
    {
        Repository repository = new FileRepository(Reboot.getCacheLocation(repositoryURI));

        CachedRepository cachedRepository = new CachedRepository(repositoryURI, repository);

        try
        {
            Reboot.info("Fetching from " + repositoryURI);
            cachedRepository.fetch();
            Reboot.info("Finished fetching from " + repositoryURI);
        }
        catch (InvalidRemoteException e)
        {
            throw new IOException(e);
        }
        catch (URISyntaxException e)
        {
            throw new IOException(e);
        }

        return cachedRepository;
    }
}

/*
 * Copyright 2011-2012 Josh Beitelspacher
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
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepository;

import com.netbeetle.reboot.core.Reboot;
import com.netbeetle.reboot.core.RebootException;
import com.netbeetle.reboot.core.URIResolver;

public class GitURIResolver implements URIResolver
{
    private final Map<String, CachedRepository> repositories =
        new HashMap<String, CachedRepository>();

    @Override
    public synchronized GitFileSystem resolve(URI uri) throws RebootException
    {
        String uriString = uri.toString();
        int start = uriString.startsWith("git+") ? 4 : 0;
        int end = uriString.indexOf(".git/") + 4;
        int revisionAndPathStart = end + 1;
        if (end == 3)
        {
            end = uriString.indexOf("!/");
            revisionAndPathStart = end + 2;
            if (end == -1)
            {
                throw new RebootException("Unabled to determine base URL for Git repository: "
                    + uri);
            }
        }

        String repositoryURI = uriString.substring(start, end);

        CachedRepository cachedRepository = repositories.get(repositoryURI);
        if (cachedRepository == null)
        {
            try
            {
                Repository repository =
                    new FileRepository(Reboot.getCacheLocation(repositoryURI));

                cachedRepository = new CachedRepository(repositoryURI, repository);

                if (!cachedRepository.exists())
                {
                    Reboot.info("Initializing new repository " + repositoryURI);
                    cachedRepository.init();
                }
            }
            catch (URISyntaxException e)
            {
                throw new RebootException("Unable to retrieve repository", e);
            }
            catch (IOException e)
            {
                throw new RebootException("Unable to retrieve repository", e);
            }
            repositories.put(repositoryURI, cachedRepository);
        }

        String revisionAndPath = uriString.substring(revisionAndPathStart);
        if (revisionAndPath.endsWith("/"))
        {
            revisionAndPath = revisionAndPath.substring(0, revisionAndPath.length() - 1);
        }

        try
        {
            GitRevision gitRevision = cachedRepository.lookupRevision(revisionAndPath);

            if (!cachedRepository.hasFetched())
            {
                boolean needFetch;
                if (gitRevision == null)
                {
                    needFetch = true;
                }
                else
                {
                    String ref = gitRevision.getRefName();
                    if (ref == null)
                    {
                        needFetch = false;
                    }
                    else
                    {
                        needFetch = ref.startsWith("refs/heads/");
                    }
                }

                if (needFetch)
                {
                    Reboot.info("Fetching from " + repositoryURI);
                    cachedRepository.fetch();
                    Reboot.info("Finished fetching from " + repositoryURI);

                    gitRevision = cachedRepository.lookupRevision(revisionAndPath);
                }
            }

            if (gitRevision == null)
            {
                throw new RebootException("Unable to find " + revisionAndPath
                    + " in repository");
            }

            cachedRepository.useRevision(gitRevision);

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
        catch (InvalidRemoteException e)
        {
            throw new RebootException("Unable to find " + revisionAndPath + " in repository", e);
        }
    }
}

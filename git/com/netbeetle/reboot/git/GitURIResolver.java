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

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepository;

import com.netbeetle.reboot.core.URIResolver;

public class GitURIResolver implements URIResolver
{
    private final Object lock = new Object();
    private final Map<String, CachedRepository> repositories =
        new ConcurrentHashMap<String, CachedRepository>();
    private final GitURLStreamHandler handler = new GitURLStreamHandler(repositories);

    @Override
    public URL resolve(URI uri) throws MalformedURLException
    {
        URL url = new URL(null, uri.toString(), handler);

        String urlString = url.toString();
        int start = urlString.startsWith("git+") ? 4 : 0;
        int end = urlString.indexOf(".git/") + 4;
        String repositoryURL = urlString.substring(start, end);

        CachedRepository cachedRepository = repositories.get(repositoryURL);
        if (cachedRepository == null)
        {
            synchronized (lock)
            {
                cachedRepository = repositories.get(repositoryURL);
                if (cachedRepository == null)
                {
                    try
                    {
                        cachedRepository = getRepository(repositoryURL);
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                    repositories.put(repositoryURL, cachedRepository);
                }
            }
        }

        return url;
    }

    private CachedRepository getRepository(String repositoryURI) throws IOException
    {
        String rebootCacheString = System.getProperty("com.netbeetle.reboot.cache");
        if (rebootCacheString == null)
        {
            rebootCacheString = System.getProperty("user.home") + "/.reboot/cache";
        }

        File rebootCacheDir = new File(rebootCacheString);

        rebootCacheDir.mkdirs();

        StringBuilder builder = new StringBuilder(repositoryURI.length());
        for (int i = 0; i < repositoryURI.length(); i++)
        {
            char c = repositoryURI.charAt(i);
            if (c < ' ' || c == '"' || c == '%' || c == '*' || c == ':' || c == '<' || c == '>'
                || c == '?' || c == '\\' || c == '|' || c > '~')
            {
                builder.append('_');
            }
            else
            {
                builder.append(c);
            }
        }

        String filename = builder.toString().replaceAll("_*/_*", "/");

        File cacheDir = new File(rebootCacheDir, filename);

        Repository repository = new FileRepository(cacheDir);

        CachedRepository cachedRepository =
            new CachedRepository(new String(repositoryURI), repository);

        try
        {
            cachedRepository.fetch();
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

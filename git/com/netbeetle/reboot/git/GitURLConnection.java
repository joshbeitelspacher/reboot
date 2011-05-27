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
import java.net.URL;
import java.net.URLConnection;

import org.eclipse.jgit.lib.ObjectId;

public class GitURLConnection extends URLConnection
{
    private final CachedRepository cachedRepository;
    private final String revisionAndPath;

    private boolean recursive;
    private ObjectId blobId;

    public GitURLConnection(URL url, CachedRepository cachedRepository, String revisionAndPath)
    {
        super(url);
        this.cachedRepository = cachedRepository;
        this.revisionAndPath = revisionAndPath;
    }

    @Override
    public void connect() throws IOException
    {
        if (!connected)
        {
            recursive = Boolean.parseBoolean(getRequestProperty("recursive"));
            blobId = cachedRepository.lookup(revisionAndPath);
            connected = true;
        }
    }

    @Override
    public InputStream getInputStream() throws IOException
    {
        connect();
        if (revisionAndPath.endsWith("/"))
        {
            return cachedRepository.openTree(blobId, recursive);
        }
        return cachedRepository.open(blobId);
    }
}

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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashUtil
{
    public static String hash(File file) throws IOException, NoSuchAlgorithmException
    {
        MessageDigest digest = MessageDigest.getInstance("SHA1");

        hash(file, digest);

        byte[] bytes = digest.digest();

        return toHexString(bytes);
    }

    public static void hash(File file, MessageDigest digest) throws FileNotFoundException,
        IOException
    {
        FileInputStream inputStream = new FileInputStream(file);
        try
        {
            byte[] buffer = new byte[2048];

            int bytesRead = inputStream.read(buffer);
            while (bytesRead != -1)
            {
                digest.update(buffer, 0, bytesRead);
                bytesRead = inputStream.read(buffer);
            }

            inputStream.close();
            inputStream = null;
        }
        finally
        {
            if (inputStream != null)
            {
                try
                {
                    inputStream.close();
                }
                catch (IOException e)
                {
                    // discard exception
                }
            }
        }
    }

    public static String toHexString(byte[] bytes)
    {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes)
        {
            int i = b & 0xFF;
            if (i < 0x10)
            {
                builder.append('0');
            }
            builder.append(Integer.toHexString(i));
        }
        return builder.toString();
    }

    private HashUtil()
    {
        // prevent instantiation
    }
}

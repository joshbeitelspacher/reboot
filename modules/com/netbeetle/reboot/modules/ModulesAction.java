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

package com.netbeetle.reboot.modules;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.netbeetle.reboot.core.ApplicationContext;
import com.netbeetle.reboot.core.Arguments;
import com.netbeetle.reboot.core.Reboot;
import com.netbeetle.reboot.core.RebootAction;
import com.netbeetle.reboot.core.RebootException;
import com.netbeetle.reboot.core.RebootFileSystem;
import com.netbeetle.reboot.core.config.ModuleConfig;
import com.netbeetle.reboot.core.config.RebootConfig;

public class ModulesAction implements RebootAction
{
    @Override
    public void execute(ApplicationContext applicationContext, Arguments arguments)
        throws RebootException
    {
        try
        {
            Reboot.info("Getting module information");

            List<String> args = new ArrayList<String>(arguments.getActionArgs());

            String outputFile;

            int outputFileIndex = args.indexOf("--output-file");
            if (outputFileIndex != -1)
            {
                if (outputFileIndex + 1 == args.size())
                {
                    throw new RebootException("Missing filename for --output-file");
                }
                outputFile = args.get(outputFileIndex + 1);
                args.subList(outputFileIndex, outputFileIndex + 2).clear();
            }
            else
            {
                outputFile = null;
            }

            if (args.size() != 0)
            {
                throw new RebootException("Unsupported arguments: " + args);
            }

            RebootConfig config = applicationContext.getRebootConfig();

            StringBuilder output = new StringBuilder();

            List<ModuleConfig> modules = config.getModules();
            if (modules == null || modules.isEmpty())
            {
                Reboot.info("No modules defined");
            }
            else
            {
                for (ModuleConfig module : modules)
                {
                    printFingerprint(applicationContext, module, "uri", module.getUris(),
                        output);
                    printFingerprint(applicationContext, module, "srcUri", module.getSrcUris(),
                        output);
                }
            }

            if (outputFile == null)
            {
                System.out.println("Module fingerprints:");
                System.out.print(output);
            }
            else
            {
                System.out.println("Writing module fingerprints to " + outputFile);
                FileWriter writer = null;
                try
                {
                    writer = new FileWriter(outputFile);
                    writer.write(output.toString());
                    writer.close();
                    writer = null;
                }
                finally
                {
                    if (writer != null)
                    {
                        try
                        {
                            writer.close();
                        }
                        catch (IOException e)
                        {
                            // do nothing
                        }
                    }
                }
            }
        }
        catch (RebootException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new RebootException(e);
        }
    }

    public void printFingerprint(ApplicationContext applicationContext, ModuleConfig module,
        String uriType, List<URI> uris, StringBuilder output) throws InstantiationException,
        IllegalAccessException, ClassNotFoundException, NoSuchMethodException,
        InvocationTargetException, RebootException, IOException
    {
        if (uris != null)
        {
            RebootFileSystem fs = applicationContext.getFileSystem(uris);
            if (fs != null)
            {
                String fingerprint = fs.fingerprint();
                if (fingerprint != null)
                {
                    output.append(module.getId()).append(" ").append(uriType).append(" ")
                        .append(uris).append(" -> ").append(fingerprint)
                        .append(String.format("%n"));
                }
            }
        }
    }
}

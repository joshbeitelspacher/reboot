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
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.netbeetle.reboot.core.config.ConfigLoader;
import com.netbeetle.reboot.core.config.RebootConfig;

public class Reboot
{
    // not thread safe, only use from a single thread
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss.SSS");

    public static void main(final String[] args) throws Exception
    {
        info("Starting Reboot");

        String rebootHomeString = System.getProperty("com.netbeetle.reboot.home");
        if (rebootHomeString == null)
        {
            System.err.println("Reboot not found.");
            System.exit(-1);
            return;
        }

        File rebootHomeDir = new File(rebootHomeString).getAbsoluteFile();
        File masterConfigFile = new File(rebootHomeDir, "core/reboot-core.xml");
        if (!masterConfigFile.isFile())
        {
            System.err.println("Reboot not found.");
            System.exit(-1);
            return;
        }

        File applicationDir = new File("").getAbsoluteFile();
        File applicationConfigFile = new File(applicationDir, "reboot.xml");
        if (!applicationConfigFile.isFile())
        {
            System.err.println("Reboot config file not found.");
            System.exit(-1);
            return;
        }

        ConfigLoader configLoader = new ConfigLoader();

        RebootConfig masterConfig = configLoader.loadConfig(masterConfigFile);
        ApplicationContext masterContext =
            new ApplicationContext(rebootHomeDir.toURI(), masterConfig, null);

        RebootConfig applicationConfig = configLoader.loadConfig(applicationConfigFile);
        ApplicationContext applicationContext =
            new ApplicationContext(applicationDir.toURI(), applicationConfig, masterContext);

        Arguments arguments = new Arguments(args);
        
        if (!arguments.getOptions().isEmpty())
        {
            System.err.println("Unsupported options: " + arguments.getOptions());
            System.exit(-1);
            return;
        }

        RebootAction action = applicationContext.getAction(arguments.getAction());
        action.execute(applicationContext, arguments);
    }

    public synchronized static void info(String message)
    {
        System.out.println(DATE_FORMAT.format(new Date()) + ": " + message);
    }
}

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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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

        List<RebootConfig> configs = new ArrayList<RebootConfig>();

        File projectSpecificUserSettings = new File(applicationDir, "reboot-user.xml");
        if (projectSpecificUserSettings.exists())
        {
            configs.add(configLoader.loadConfig(projectSpecificUserSettings));
        }

        File userSettings = new File(System.getProperty("user.home"), "reboot-user.xml");
        if (userSettings.exists())
        {
            configs.add(configLoader.loadConfig(userSettings));
        }

        File systemUserSettings = new File(rebootHomeDir, "reboot-user.xml");
        if (systemUserSettings.exists())
        {
            configs.add(configLoader.loadConfig(systemUserSettings));
        }

        configs.add(configLoader.loadConfig(masterConfigFile));

        configs.add(configLoader.loadConfig(applicationConfigFile));

        RebootConfig config = configLoader.merge(configs);

        configLoader.rewriteURIs(config);

        ApplicationContext applicationContext = new ApplicationContext(config);

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

    public static File getCacheLocation(String uri)
    {
        String cacheString = System.getProperty("com.netbeetle.reboot.cache");
        if (cacheString == null)
        {
            cacheString = System.getProperty("user.home") + "/.reboot/cache";
        }

        File cacheDir = new File(cacheString);

        StringBuilder builder = new StringBuilder(uri.length());
        for (int i = 0; i < uri.length(); i++)
        {
            char c = uri.charAt(i);
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

        String filename = builder.toString().replaceAll("_*/[_/]*", "/");

        return new File(cacheDir, filename);
    }
}

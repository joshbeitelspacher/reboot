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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Arguments
{
    private final List<String> args;

    public Arguments(String[] args)
    {
        this.args = Arrays.asList(args);
    }

    public List<String> getOptions()
    {
        int i = 0;
        while (i < args.size() && args.get(i).startsWith("-"))
        {
            i++;
        }
        return args.subList(0, i);
    }

    public String getAction()
    {
        for (String arg : args)
        {
            if (!arg.startsWith("-"))
            {
                return arg;
            }
        }
        return "run";
    }

    public List<String> getActionArgs()
    {
        int i = 0;
        while (i < args.size() && args.get(i).startsWith("-"))
        {
            i++;
        }
        if (i == args.size())
        {
            return Collections.emptyList();
        }
        return args.subList(i + 1, args.size());
    }
}

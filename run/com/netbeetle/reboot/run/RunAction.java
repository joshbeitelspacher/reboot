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

package com.netbeetle.reboot.run;

import java.lang.reflect.Method;
import java.util.List;

import com.netbeetle.reboot.core.ApplicationContext;
import com.netbeetle.reboot.core.Arguments;
import com.netbeetle.reboot.core.Reboot;
import com.netbeetle.reboot.core.RebootAction;
import com.netbeetle.reboot.core.RebootException;

public class RunAction implements RebootAction
{
    @Override
    public void execute(ApplicationContext applicationContext, Arguments arguments)
        throws RebootException
    {
        try
        {
            Reboot.info("Loading entry point class");

            Class<?> entryPointClass = applicationContext.getEntryPointClass();

            Method mainMethod = entryPointClass.getMethod("main", String[].class);

            List<String> argsList = arguments.getActionArgs();
            String[] args = argsList.toArray(new String[argsList.size()]);

            Reboot.info("Launching application");

            mainMethod.invoke(null, (Object) args);
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
}

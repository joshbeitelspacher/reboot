package com.netbeetle.reboot.test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.internal.TextListener;
import org.junit.runner.JUnitCore;

import com.netbeetle.reboot.core.ApplicationContext;
import com.netbeetle.reboot.core.Arguments;
import com.netbeetle.reboot.core.RebootAction;
import com.netbeetle.reboot.core.RebootClassLoader;
import com.netbeetle.reboot.core.RebootException;
import com.netbeetle.reboot.core.RebootFile;
import com.netbeetle.reboot.core.RebootFileSystem;

public class TestAction implements RebootAction
{
    // use standard Maven test patterns
    private static final Pattern TEST_NAMES = Pattern
        .compile("(.*/)?((Test[^/]*)|([^/]*Test)|([^/]*TestCase))\\.java");

    // add netty specific exclusions
    private static final Pattern TEST_EXCLUDES = Pattern
        .compile("(.*/)?((Abstract[^/]*)|(TestUtil[^/]*))\\.java");

    @Override
    public void execute(ApplicationContext applicationContext, Arguments arguments)
        throws RebootException
    {
        try
        {
            for (String argument : arguments.getActionArgs())
            {
                RebootClassLoader classLoader = applicationContext.getClassLoader(argument);
                RebootFileSystem fileSystem = classLoader.getFileSystem();

                List<Class<?>> testClasses = new ArrayList<Class<?>>();

                for (RebootFile file : fileSystem.getFile("").list(true))
                {
                    // skip all directories
                    if (file.isDirectory())
                    {
                        continue;
                    }

                    String filename = file.getName();
                    if (TEST_NAMES.matcher(filename).matches()
                        && !TEST_EXCLUDES.matcher(filename).matches())
                    {
                        String className =
                            filename.substring(0, filename.length() - 5).replace('/', '.');

                        Class<?> testClass = classLoader.loadClass(className);

                        testClasses.add(testClass);
                    }
                }

                JUnitCore junit = new JUnitCore();
                junit.addListener(new TextListener(System.out));
                junit.run(testClasses.toArray(new Class<?>[testClasses.size()]));
            }
        }
        catch (Exception e)
        {
            throw new RebootException(e);
        }
    }
}

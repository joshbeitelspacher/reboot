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

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class URIRewriteRule
{
    private static final Pattern PATTERN_VARIABLE = Pattern
        .compile("\\{([A-Za-z0-9-_]+):([^}]+)\\}");
    private static final Pattern REPLACEMENT_VARIABLE = Pattern
        .compile("\\{([A-Za-z0-9-_]+)\\}");

    private final Pattern pattern;
    private final String replacement;

    public URIRewriteRule(String pattern, String replacement)
    {
        Matcher patternMatcher = PATTERN_VARIABLE.matcher(pattern);
        StringBuilder rawPattern = new StringBuilder("^");
        Map<String, Integer> patternIndexes = new HashMap<String, Integer>();
        int groupCount = 0;
        int index = 0;
        while (patternMatcher.find())
        {
            rawPattern.append(Pattern.quote(pattern.substring(index, patternMatcher.start())));

            index = patternMatcher.end();

            int thisGroupCount =
                Pattern.compile(patternMatcher.group(2)).matcher("").groupCount();

            rawPattern.append('(');
            rawPattern.append(patternMatcher.group(2));
            rawPattern.append(')');

            patternIndexes.put(patternMatcher.group(1), Integer.valueOf(groupCount + 1));

            groupCount += 1 + thisGroupCount;
        }
        rawPattern.append(Pattern.quote(pattern.substring(index)));
        if (patternIndexes.isEmpty())
        {
            rawPattern.append("(.*)");
        }
        rawPattern.append('$');
        this.pattern = Pattern.compile(rawPattern.toString());

        Matcher variableMatcher = REPLACEMENT_VARIABLE.matcher(replacement);
        StringBuilder rawReplacement = new StringBuilder();
        index = 0;
        while (variableMatcher.find())
        {
            rawReplacement.append(Matcher.quoteReplacement(replacement.substring(index,
                variableMatcher.start())));

            index = variableMatcher.end();

            Integer patternIndex = patternIndexes.get(variableMatcher.group(1));

            if (patternIndex == null)
            {
                rawReplacement.append(Matcher.quoteReplacement(variableMatcher.group()));
            }
            else
            {
                rawReplacement.append('$');
                rawReplacement.append(patternIndex);
            }
        }
        rawReplacement.append(Matcher.quoteReplacement(replacement.substring(index)));
        if (patternIndexes.isEmpty())
        {
            rawReplacement.append("$1");
        }
        this.replacement = rawReplacement.toString();
    }

    public String rewrite(String value)
    {
        Matcher matcher = pattern.matcher(value);
        if (matcher.matches())
        {
            StringBuffer buffer = new StringBuffer();
            matcher.appendReplacement(buffer, replacement);
            return buffer.toString();
        }
        return null;
    }
}

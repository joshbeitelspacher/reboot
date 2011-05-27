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

package com.netbeetle.reboot.source;

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;

public final class CharsetUtil
{
    public static final Charset UTF8 = Charset.forName("UTF-8");

    public static CharsetDecoder getUTF8Decoder(boolean ignoreEncodingErrors)
    {
        CodingErrorAction action =
            ignoreEncodingErrors ? CodingErrorAction.IGNORE : CodingErrorAction.REPORT;
        return UTF8.newDecoder().onMalformedInput(action).onUnmappableCharacter(action);
    }

    private CharsetUtil()
    {
        // prevent instantiation
    }
}

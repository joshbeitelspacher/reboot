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

package com.netbeetle.reboot.core.config;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.dom.DOMSource;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class ConfigLoader
{
    private static final Pattern VARIABLE_PATTERN = Pattern
        .compile("\\$\\{(.+?)(?::(.*?))?\\}");
    private static final Properties PROPERTIES = System.getProperties();
    private static final Map<String, String> ENVIRONMENT = System.getenv();

    private final DocumentBuilder documentBuilder;
    private final JAXBContext jaxbContext;

    public ConfigLoader() throws ParserConfigurationException, JAXBException
    {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        documentBuilder = factory.newDocumentBuilder();
        jaxbContext = JAXBContext.newInstance(RebootConfig.class);
    }

    public RebootConfig loadConfig(File rebootConfigFile) throws SAXException, IOException,
        JAXBException
    {
        Document rebootConfigDocument = documentBuilder.parse(rebootConfigFile);
        replaceVariables(rebootConfigDocument);
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        return unmarshaller.unmarshal(new DOMSource(rebootConfigDocument), RebootConfig.class)
            .getValue();
    }

    public String convertToXML(RebootConfig config) throws JAXBException
    {
        Marshaller marshaller = jaxbContext.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

        StringWriter writer = new StringWriter();
        marshaller.marshal(config, writer);
        return writer.toString();
    }

    private static void replaceVariables(Document document)
    {
        LinkedList<Node> elements = new LinkedList<Node>();
        elements.add(document.getDocumentElement());
        while (!elements.isEmpty())
        {
            Node element = elements.removeLast();
            NodeList childNodes = element.getChildNodes();
            for (int i = childNodes.getLength() - 1; i >= 0; i--)
            {
                Node childNode = childNodes.item(i);
                short childNodeType = childNode.getNodeType();
                if (childNodeType == Node.TEXT_NODE || childNodeType == Node.ATTRIBUTE_NODE)
                {
                    String content = replaceVariables(childNode.getTextContent());
                    if (content != null)
                    {
                        childNode.setTextContent(content);
                    }
                }
                else if (childNodeType == Node.ELEMENT_NODE)
                {
                    elements.addLast(childNode);
                }
            }
        }
    }

    private static String replaceVariables(String content)
    {
        Matcher matcher = VARIABLE_PATTERN.matcher(content);
        if (!matcher.find())
        {
            return null;
        }

        StringBuilder builder = new StringBuilder(content.length());
        int end;
        do
        {
            builder.append(content.substring(0, matcher.start()));
            String value = PROPERTIES.getProperty(matcher.group(1));
            if (value == null)
            {
                value = ENVIRONMENT.get(matcher.group(1));
            }
            if (value == null)
            {
                value = matcher.group(2);
                if (value == null)
                {
                    builder.append(matcher.group());
                }
                else
                {
                    builder.append(value);
                }
            }
            else
            {
                builder.append(value);
            }
            end = matcher.end();
        }
        while (matcher.find());
        builder.append(content.substring(end));
        return builder.toString();
    }
}

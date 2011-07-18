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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
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

import com.netbeetle.reboot.core.RebootException;
import com.netbeetle.reboot.core.URIRewriteRule;

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
        RebootConfig config =
            unmarshaller.unmarshal(new DOMSource(rebootConfigDocument), RebootConfig.class)
                .getValue();

        // resolve relative URIs
        URI directory = rebootConfigFile.getParentFile().toURI();
        for (ModuleConfig module : nullSafeList(config.getModules()))
        {
            module.setUris(resolveURIs(directory, module.getUris()));
            module.setSrcUris(resolveURIs(directory, module.getSrcUris()));
        }

        return config;
    }

    private List<URI> resolveURIs(URI directory, List<URI> uris)
    {
        List<URI> resolvedURIs = null;
        if (uris != null)
        {
            resolvedURIs = new ArrayList<URI>(uris.size());
            for (URI uri : uris)
            {
                resolvedURIs.add(directory.resolve(uri));
            }
        }
        return resolvedURIs;
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

    public RebootConfig merge(List<RebootConfig> configs)
    {
        Map<String, ActionConfig> mergedActions = map();
        Map<String, ClassLoaderConfig> mergedClassLoaders = map();
        Map<String, URIRewriteRuleConfig> mergedURIRewriteRules = map();
        Map<String, URIResolverConfig> mergedURIResolvers = map();
        Map<String, ModuleConfig> mergedModules = map();
        EntryPointConfig entryPoint = null;

        for (RebootConfig config : configs)
        {
            for (ActionConfig action : nullSafeList(config.getActions()))
            {
                if (!mergedActions.containsKey(action.getId()))
                {
                    mergedActions.put(action.getId(), action);
                }
            }

            for (ClassLoaderConfig classLoader : nullSafeList(config.getClassLoaders()))
            {
                if (!mergedClassLoaders.containsKey(classLoader.getId()))
                {
                    mergedClassLoaders.put(classLoader.getId(), classLoader);
                }
            }

            for (URIRewriteRuleConfig uriRewriteRule : nullSafeList(config.getUriRewriteRules()))
            {
                if (!mergedURIRewriteRules.containsKey(uriRewriteRule.getPattern()))
                {
                    mergedURIRewriteRules.put(uriRewriteRule.getPattern(), uriRewriteRule);
                }
            }

            for (URIResolverConfig uriResolver : nullSafeList(config.getUriResolvers()))
            {
                String pattern = uriResolver.getExpression().pattern();
                if (!mergedURIResolvers.containsKey(pattern))
                {
                    mergedURIResolvers.put(pattern, uriResolver);
                }
            }

            for (ModuleConfig module : nullSafeList(config.getModules()))
            {
                if (!mergedModules.containsKey(module.getId()))
                {
                    mergedModules.put(module.getId(), module);
                }
            }

            if (entryPoint == null)
            {
                entryPoint = config.getEntryPoint();
            }
        }

        RebootConfig merged = new RebootConfig();
        merged.setActions(list(mergedActions));
        merged.setClassLoaders(list(mergedClassLoaders));
        merged.setModules(list(mergedModules));
        merged.setUriRewriteRules(list(mergedURIRewriteRules));
        merged.setUriResolvers(list(mergedURIResolvers));
        merged.setEntryPoint(entryPoint);

        return merged;
    }

    public void rewriteURIs(RebootConfig config) throws RebootException
    {
        List<URIRewriteRule> rewriteRules = new ArrayList<URIRewriteRule>();
        for (URIRewriteRuleConfig rewriteRule : nullSafeList(config.getUriRewriteRules()))
        {
            rewriteRules.add(new URIRewriteRule(rewriteRule.getPattern(), rewriteRule
                .getReplacement()));
        }

        for (ModuleConfig module : nullSafeList(config.getModules()))
        {
            module.setUris(rewriteURIs(rewriteRules, module.getUris()));
            module.setSrcUris(rewriteURIs(rewriteRules, module.getSrcUris()));
        }
    }

    private List<URI> rewriteURIs(List<URIRewriteRule> rewriteRules, List<URI> uris)
        throws RebootException
    {
        List<URI> rewrittenURIs = null;
        if (uris != null)
        {
            rewrittenURIs = new ArrayList<URI>(uris.size());
            for (URI uri : uris)
            {
                rewrittenURIs.add(rewriteURI(rewriteRules, uri));
            }
        }
        return rewrittenURIs;
    }

    private URI rewriteURI(List<URIRewriteRule> rewriteRules, URI uri) throws RebootException
    {
        String value = uri.toString();
        for (int i = 0; i < 100; i++)
        {
            String newValue = rewriteURIOnce(rewriteRules, value);
            if (newValue == null)
            {
                try
                {
                    return new URI(value);
                }
                catch (URISyntaxException e)
                {
                    throw new RebootException("URI rewritten to invalid value: " + uri + " to "
                        + value);
                }
            }
            value = newValue;
        }
        throw new RebootException("URI rewrite loop exceded limit when processing " + uri);
    }

    private String rewriteURIOnce(List<URIRewriteRule> rewriteRules, String value)
    {
        for (URIRewriteRule rule : rewriteRules)
        {
            String newValue = rule.rewrite(value);
            if (newValue != null)
            {
                return newValue;
            }
        }
        return null;
    }

    private <T> List<T> nullSafeList(List<T> list)
    {
        if (list == null)
        {
            return Collections.emptyList();
        }
        return list;
    }

    private static <K, V> LinkedHashMap<K, V> map()
    {
        return new LinkedHashMap<K, V>();
    }

    private static <K, V> ArrayList<V> list(Map<K, V> map)
    {
        if (map.isEmpty())
        {
            return null;
        }
        return new ArrayList<V>(map.values());
    }
}

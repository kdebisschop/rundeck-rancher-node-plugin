/*
 * Copyright 2019 BioRAFT, Inc. (http://bioraft.com)
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
package com.bioraft.rundeck.rancher;

import com.dtolabs.rundeck.core.common.Framework;
import com.dtolabs.rundeck.core.execution.ExecutionContext;
import com.dtolabs.rundeck.core.storage.ResourceMeta;
import com.dtolabs.rundeck.core.storage.StorageTree;
import com.dtolabs.rundeck.plugins.PluginLogger;
import com.dtolabs.rundeck.plugins.step.PluginStepContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.Mock;
import org.rundeck.storage.api.Resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;

import static com.bioraft.rundeck.rancher.Constants.*;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.when;

/**
 * Tests for Nexus3OptionProvider.
 *
 * @author Karl DeBisschop <kdebisschop@gmail.com>
 * @since 2019-12-11
 */
public abstract class PluginStepTest {

    protected static final String projectEndpoint = "https://rancher.example.com/v2-beta";
    protected static final String projectAccessKey = "keys/rancher/access.key";
    protected static final String projectSecretKey = "keys/rancher/secret.key";
    protected static final String projectName = "1a10";

    @Mock
    HttpClient client;

    @Mock
    PluginStepContext ctx;

    @Mock
    PluginLogger logger;

    @Mock
    Framework framework;

    @Mock
    ExecutionContext executionContext;

    @Mock
    Resource<ResourceMeta> treeResource;

    @Mock
    ResourceMeta contents;

    @Mock
    StorageTree storageTree;

    @Mock
    Map<String, Object> cfg;

    public void setUp() {
        when(ctx.getLogger()).thenReturn(logger);
        when(ctx.getFramework()).thenReturn(framework);
        when(ctx.getFrameworkProject()).thenReturn(projectName);
        when(ctx.getExecutionContext()).thenReturn(executionContext);

        when(framework.getProperty(eq(FMWK_RANCHER_ENDPOINT))).thenReturn("https://rancher.example.com/v1");
        when(framework.getProperty(eq(FMWK_RANCHER_ACCESSKEY_PATH))).thenReturn("framework.accessKey");
        when(framework.getProperty(eq(FMWK_RANCHER_SECRETKEY_PATH))).thenReturn("framework.secretKey");

        when(executionContext.getStorageTree()).thenReturn(storageTree);
        when(storageTree.getResource(anyString())).thenReturn(treeResource);
        when(treeResource.getContents()).thenReturn(contents);
        when(cfg.get("stack")).thenReturn("testStack");
    }

    protected InputStream getResourceStream(String resource) {
        ClassLoader classLoader = getClass().getClassLoader();
        InputStream stream = classLoader.getResourceAsStream(resource);
        if (stream == null) throw new AssertionError();
        return stream;
    }

    protected JsonNode readFromInputStream(InputStream inputStream) throws IOException {
        StringBuilder resultStringBuilder = new StringBuilder();
        InputStreamReader reader = new InputStreamReader(inputStream);
        try (BufferedReader br = new BufferedReader(reader)) {
            String line;
            while ((line = br.readLine()) != null) {
                resultStringBuilder.append(line).append("\n");
            }
        }
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readTree(resultStringBuilder.toString());
    }
}
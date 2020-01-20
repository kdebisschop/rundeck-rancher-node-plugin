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
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.Call;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.rundeck.storage.api.Resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.bioraft.rundeck.rancher.RancherShared.*;
import static org.mockito.Mockito.*;

/**
 * Tests for Nexus3OptionProvider.
 *
 * @author Karl DeBisschop <kdebisschop@gmail.com>
 * @since 2019-12-11
 */
public class PluginStepTest {

	protected static final String endpoint = "https://rancher.example.com/v2-beta/";
	protected static final String accessKey = "keys/rancher/access.key";
	protected static final String secretKey = "keys/rancher/secret.key";
	protected static final String project = "1a10";

	@Mock
	HttpClient client;

	@Mock
	Call call;

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

	RancherNewStack upgrade;

	public void setUp() throws IOException {
		Map<String, String> map = Stream
				.of(new String[][]{{"services", endpoint},
						{RancherShared.CONFIG_ACCESSKEY_PATH, "keys/rancher/access.key"},
						{RancherShared.CONFIG_SECRETKEY_PATH, "keys/rancher/secret.key"},})
				.collect(Collectors.toMap(data -> data[0], data -> data[1]));
		when(ctx.getLogger()).thenReturn(logger);
		when(ctx.getFramework()).thenReturn(framework);
		when(ctx.getExecutionContext()).thenReturn(executionContext);

		when(framework.getProjectProperty(project, PROJ_RANCHER_ENDPOINT)).thenReturn(endpoint);
		when(framework.getProjectProperty(project, PROJ_RANCHER_ACCESSKEY_PATH)).thenReturn(accessKey);
		when(framework.getProjectProperty(project, PROJ_RANCHER_SECRETKEY_PATH)).thenReturn(secretKey);

		when(executionContext.getStorageTree()).thenReturn(storageTree);
		when(storageTree.getResource(anyString())).thenReturn(treeResource);
		when(treeResource.getContents()).thenReturn(contents);
		when(cfg.get("stack")).thenReturn("testStack");
	}


	private InputStream getResourceStream(String resource) {
		ClassLoader classLoader = getClass().getClassLoader();
		InputStream stream = classLoader.getResourceAsStream(resource);
		if (stream == null) throw new AssertionError();
		return stream;
	}

	private JsonNode readFromInputStream(InputStream inputStream) throws IOException {
		StringBuilder resultStringBuilder = new StringBuilder();
		InputStreamReader reader = new InputStreamReader(inputStream);
		try (BufferedReader br = new BufferedReader(reader)) {
			String line;
			while ((line = br.readLine()) != null) {
				resultStringBuilder.append(line).append("\n");
			}
		}
		ObjectMapper mapper = new ObjectMapper();
		return (ObjectNode) mapper.readTree(resultStringBuilder.toString());
	}
}
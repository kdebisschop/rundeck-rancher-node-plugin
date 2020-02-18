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

import com.dtolabs.rundeck.core.common.INodeEntry;
import com.dtolabs.rundeck.core.execution.ExecutionContext;
import com.dtolabs.rundeck.core.execution.workflow.steps.node.NodeStepException;
import com.dtolabs.rundeck.core.storage.ResourceMeta;
import com.dtolabs.rundeck.core.storage.StorageTree;
import com.dtolabs.rundeck.plugins.PluginLogger;
import com.dtolabs.rundeck.plugins.step.PluginStepContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import okhttp3.Response.Builder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.rundeck.storage.api.Resource;

import java.io.*;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for Nexus3OptionProvider.
 *
 * @author Karl DeBisschop <kdebisschop@gmail.com>
 * @since 2019-12-11
 */
@RunWith(MockitoJUnitRunner.class)
public class RancherUpgradeServiceTest {

	@Mock
	OkHttpClient client;

	@Mock
	Call call;

	@Mock
	PluginStepContext ctx;

	@Mock
	PluginLogger logger;

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

	@Mock
	INodeEntry node;

	RancherUpgradeService upgrade;

	@Before
	public void setUp() {
		Map<String, String> map = Stream
				.of(new String[][]{{"services", "https://rancher.example.com/v2-beta/"},
						{"type", "container"},
						{RancherShared.CONFIG_ACCESSKEY_PATH, "keys/rancher/access.key"},
						{RancherShared.CONFIG_SECRETKEY_PATH, "keys/rancher/secret.key"},})
				.collect(Collectors.toMap(data -> data[0], data -> data[1]));
		when(node.getAttributes()).thenReturn(map);
		when(ctx.getLogger()).thenReturn(logger);
		when(ctx.getExecutionContext()).thenReturn(executionContext);
		when(executionContext.getStorageTree()).thenReturn(storageTree);
		when(storageTree.getResource(anyString())).thenReturn(treeResource);
		when(treeResource.getContents()).thenReturn(contents);
		when(client.newCall(any())).thenReturn(call);
		cfg.put("startFirst", "true");
	}

	@Test
	public void processOneNode() throws NodeStepException, IOException {
		Response response0 = response(getResourceStream("services.json"));

		String text = readFromInputStream(getResourceStream("service.json"));
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode json1 = (ObjectNode) mapper.readTree(text);

		json1.put("state", "transtitioning");
		Response response1 = response(json1.toPrettyString());

		json1.put("state", "upgraded");
		Response response2 = response(json1.toPrettyString());

		json1.put("state", "active");
		Response response3 = response(json1.toPrettyString());

		when(call.execute()).thenReturn(response0, response1, response2, response3);

		upgrade = new RancherUpgradeService(client);
		upgrade.executeNodeStep(ctx, cfg, node);

		verify(call, times(4)).execute();
	}

	private Response response(InputStream stream) throws IOException {
		return response(readFromInputStream(stream));
	}

	private Response response(String json) {
		Request request = new Request.Builder().url("https://example.com").build();
		ResponseBody body = ResponseBody.create(MediaType.parse("text/json"), json);
		Builder builder = new Response.Builder().request(request).protocol(Protocol.HTTP_2);
		builder.body(body).code(200).message("OK");
		return builder.build();
	}

	private InputStream getResourceStream(String resource) {
		ClassLoader classLoader = getClass().getClassLoader();
		InputStream stream = classLoader.getResourceAsStream(resource);
		if (stream == null) throw new AssertionError();
		return stream;
	}

	private String readFromInputStream(InputStream inputStream) throws IOException {
		StringBuilder resultStringBuilder = new StringBuilder();
		InputStreamReader reader = new InputStreamReader(inputStream);
		try (BufferedReader br = new BufferedReader(reader)) {
			String line;
			while ((line = br.readLine()) != null) {
				resultStringBuilder.append(line).append("\n");
			}
		}
		return resultStringBuilder.toString();
	}
}
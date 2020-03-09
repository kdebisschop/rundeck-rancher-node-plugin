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
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.bioraft.rundeck.rancher.Constants.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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

	Map<String, Object> cfg;

	@Mock
	INodeEntry node;

	RancherUpgradeService upgrade;

	Map<String, String> map;

	@Before
	public void setUp() {
		cfg = new HashMap<>();
		map = Stream
				.of(new String[][]{{"services", "https://rancher.example.com/v2-beta/"},
						{"self", "https://rancher.example.com/v2-beta/"},
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
	}

	@Test
	public void validateDefaultConstructor() {
		RancherUpgradeService subject = new RancherUpgradeService();
		assertNotNull(subject);
	}

	/**
	 * @throws NodeStepException If path to CONFIG_ACCESSKEY_PATH or CONFIG_SECRETKEY_PATH is not configured.
	 */
	@Test(expected = NodeStepException.class)
	public void throwExceptionForNullKey() throws NodeStepException {
		RancherUpgradeService subject = new RancherUpgradeService(client);
		map.remove(RancherShared.CONFIG_ACCESSKEY_PATH);
		when(node.getAttributes()).thenReturn(map);
		subject.executeNodeStep(ctx, cfg, node);
		assertNotNull(subject);
	}

	@Test
	public void selectForService() throws NodeStepException, IOException {
		map.put("type", "service");
		when(node.getAttributes()).thenReturn(map);

		String text = readFromInputStream(getResourceStream("service.json"));
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode json1 = (ObjectNode) mapper.readTree(text);

		Response response0 = response(json1.toPrettyString());
		processService(json1, response0);
	}

	@Test(expected = NodeStepException.class)
	public void missingUpgradeUrl() throws NodeStepException, IOException {
		map.put("type", "service");
		when(node.getAttributes()).thenReturn(map);
		processOneNode();
	}

	@Test
	public void normalStartup() throws NodeStepException, IOException {
		processOneNode();
	}

	public void processOneNode() throws NodeStepException, IOException {
		Response response0 = response(getResourceStream("services.json"));

		String text = readFromInputStream(getResourceStream("service.json"));
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode json1 = (ObjectNode) mapper.readTree(text);

		processService(json1, response0);
	}

	public void processService(ObjectNode json1, Response response0) throws NodeStepException, IOException {
		cfg.put("sleepInterval", "1");

		json1.put("state", "transitioning");
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

	@Test
	public void processOneNodeAndCfg() throws NodeStepException, IOException {
		cfg.put(START_FIRST, "false");
		cfg.put("dockerImage", "ubuntu/xenial:16.04");
		cfg.put("environment", "{}");
		cfg.put("labels", "{}");
		cfg.put("removeEnvironment", "[]");
		cfg.put("removeLabels", "[]");
		cfg.put("dataVolumes", "[]");
		cfg.put("secrets", "1se1");
		processOneNode();
	}

	@Test
	public void processOneNodeAndCfgWithBlanks() throws NodeStepException, IOException {
		cfg.put("dockerImage", "");
		cfg.put("environment", "");
		cfg.put("labels", "");
		cfg.put("removeEnvironment", "");
		cfg.put("removeLabels", "");
		cfg.put("dataVolumes", "");
		cfg.put("secrets", "");
		processOneNode();
	}

	@Test
	public void processOneNodeAndCfgWithNulls() throws NodeStepException, IOException {
		cfg.put("dockerImage", null);
		cfg.put("environment", null);
		cfg.put("labels", null);
		cfg.put("removeEnvironment", null);
		cfg.put("removeLabels", null);
		cfg.put("dataVolumes", null);
		cfg.put("secrets", null);
		processOneNode();
	}

	@Test
	public void processNode() throws NodeStepException, IOException {
		cfg.put(START_FIRST, "false");
		cfg.put("sleepInterval", "1");
		Response response0 = response(getResourceStream("services.json"));

		String text = readFromInputStream(getResourceStream("service.json"));
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode json1 = (ObjectNode) mapper.readTree(text);

		json1.put("state", "transitioning");
		Response response1 = response(json1.toPrettyString());
		Response response1a = response(json1.toPrettyString());

		json1.put("state", "upgraded");
		Response response2 = response(json1.toPrettyString());
		Response response2a = response(json1.toPrettyString());
		Response response2b = response(json1.toPrettyString());

		json1.put("state", "active");
		Response response3 = response(json1.toPrettyString());

		when(call.execute()).thenReturn(response0, response1, response1a, response2, response2a, response2b, response3);

		upgrade = new RancherUpgradeService(client);
		upgrade.executeNodeStep(ctx, cfg, node);

		verify(call, times(7)).execute();
	}

	@Test(expected = NodeStepException.class)
	public void testStopped()  throws NodeStepException, IOException {
		String text = readFromInputStream(getResourceStream("service.json"));
		ObjectMapper mapper = new ObjectMapper();

		ObjectNode json1 = (ObjectNode) mapper.readTree(text);
		json1.put("state", "upgraded");

		Response response0 = response(json1.toPrettyString());

		when(call.execute()).thenReturn(response0);

		upgrade = new RancherUpgradeService(client);
		upgrade.executeNodeStep(ctx, cfg, node);

		verify(call, times(1)).execute();
	}

	@Test(expected = NodeStepException.class)
	public void testGetFails() throws NodeStepException, IOException {
		String text = readFromInputStream(getResourceStream("service.json"));
		ObjectMapper mapper = new ObjectMapper();

		ObjectNode json1 = (ObjectNode) mapper.readTree(text);

		Response response0 = response(json1.toPrettyString(), 301);

		when(call.execute()).thenReturn(response0);

		upgrade = new RancherUpgradeService(client);
		upgrade.executeNodeStep(ctx, cfg, node);

		verify(call, times(1)).execute();
	}

	/**
	 * Response body can be null for cacheResponse and other, but we do not expect that or
	 * have a way to respond to null body. So we fail because the service stata is unknown.
	 *
	 * @throws IOException because no service state can be inferred from empty body.
	 */
	@Test(expected = NodeStepException.class)
	public void testGetEmpty() throws NodeStepException, IOException {
		String text = readFromInputStream(getResourceStream("service.json"));
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode json1 = (ObjectNode) mapper.readTree(text);
		Response response0 = response(null, 200);
		try {
			processService(json1, response0);
		} catch (NodeStepException e) {
			assertEquals("Service state must be running, was ", e.getMessage());
			throw e;
		}
	}

	/**
	 * Fail with message if there is no upgradeUrl.
	 *
	 * @throws IOException because there is no upgrade url.
	 */
	@Test(expected = NodeStepException.class)
	public void testMissingUpgradeUrl() throws NodeStepException, IOException {
		map.put("type", "service");
		when(node.getAttributes()).thenReturn(map);
		String text = readFromInputStream(getResourceStream("service.json"));
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode json1 = (ObjectNode) mapper.readTree(text);
		ObjectNode actions = (ObjectNode) json1.get("actions");
		actions.remove("upgrade");
		Response response0 = response(json1.toPrettyString());
		try {
			processService(json1, response0);
		} catch (NodeStepException e) {
			assertEquals("No upgrade URL found", e.getMessage());
			throw e;
		}
	}

	/**
	 * Fallback to current launchConfig if there is no upgrade -> launchConfig.
	 *
	 * @throws IOException because there is no upgrade url.
	 */
	@Test
	public void testMissingUpgradeLaunchConfig() throws NodeStepException, IOException {
		map.put("type", "service");
		when(node.getAttributes()).thenReturn(map);
		String text = readFromInputStream(getResourceStream("service.json"));
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode json1 = (ObjectNode) mapper.readTree(text);
		ObjectNode upgrade = (ObjectNode) json1.get("upgrade");
		ObjectNode strategy = (ObjectNode) upgrade.get("inServiceStrategy");
		strategy.put("launchConfig", (String) null);
		Response response0 = response(json1.toPrettyString());
		try {
			processService(json1, response0);
		} catch (NodeStepException e) {
			assertEquals("No upgrade data found", e.getMessage());
			throw e;
		}
	}

	/**
	 * Fail with message if there is no launchConfig.
	 *
	 * @throws IOException because there is no upgrade url.
	 */
	@Test(expected = NodeStepException.class)
	public void testMissingLaunchConfig() throws NodeStepException, IOException {
		map.put("type", "service");
		when(node.getAttributes()).thenReturn(map);
		String text = readFromInputStream(getResourceStream("service.json"));
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode json1 = (ObjectNode) mapper.readTree(text);
		json1.remove("upgrade");
		json1.remove(LAUNCH_CONFIG);
		Response response0 = response(json1.toPrettyString());
		try {
			processService(json1, response0);
		} catch (NodeStepException e) {
			assertEquals("No upgrade data found", e.getMessage());
			throw e;
		}
	}

	/**
	 * Fail with message if there is no launchConfig.
	 *
	 * @throws IOException because there is no upgrade url.
	 */
	@Test(expected = NodeStepException.class)
	public void testNullLaunchConfig() throws NodeStepException, IOException {
		map.put("type", "service");
		when(node.getAttributes()).thenReturn(map);
		String text = readFromInputStream(getResourceStream("service.json"));
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode json1 = (ObjectNode) mapper.readTree(text);
		json1.remove("upgrade");
		json1.put(LAUNCH_CONFIG, (String) null);
		Response response0 = response(json1.toPrettyString());
		try {
			processService(json1, response0);
		} catch (NodeStepException e) {
			assertEquals("No upgrade data found", e.getMessage());
			throw e;
		}
	}

	@Test(expected = NodeStepException.class)
	public void testPostFails() throws NodeStepException, IOException {
		map.put("type", "service");
		when(node.getAttributes()).thenReturn(map);

		String text = readFromInputStream(getResourceStream("service.json"));
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode json1 = (ObjectNode) mapper.readTree(text);
		json1.put("state", "active");

		Response response0 = response(json1.toPrettyString());

		json1.put("state", "transitioning");
		Response response1 = response(json1.toPrettyString(), 401);

		when(call.execute()).thenReturn(response0, response1);

		upgrade = new RancherUpgradeService(client);
		try {
			upgrade.executeNodeStep(ctx, cfg, node);
		} catch (NodeStepException e) {
			assertEquals("API post failed OK", e.getMessage());
			throw e;
		}
		verify(call, times(2)).execute();
	}

	@Test(expected = NodeStepException.class)
	public void testPostEmpty() throws NodeStepException, IOException {
		map.put("type", "service");
		when(node.getAttributes()).thenReturn(map);

		String text = readFromInputStream(getResourceStream("service.json"));
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode json1 = (ObjectNode) mapper.readTree(text);
		json1.put("state", "active");

		Response response0 = response(json1.toPrettyString());

		Response response1 = response(null, 200);

		when(call.execute()).thenReturn(response0, response1);

		upgrade = new RancherUpgradeService(client);
		try {
			upgrade.executeNodeStep(ctx, cfg, node);
		} catch (NodeStepException e) {
			assertEquals("API POST returned incomplete data", e.getMessage());
			throw e;
		}
		verify(call, times(2)).execute();
	}

	@Test(expected = NodeStepException.class)
	public void testPostMissingLink() throws NodeStepException, IOException {
		map.put("type", "service");
		when(node.getAttributes()).thenReturn(map);

		String text = readFromInputStream(getResourceStream("service.json"));
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode json1 = (ObjectNode) mapper.readTree(text);
		json1.put("state", "active");

		Response response0 = response(json1.toPrettyString());

		json1.remove(NODE_ATT_LINKS);
		Response response1 = response(json1.toPrettyString());

		when(call.execute()).thenReturn(response0, response1);

		upgrade = new RancherUpgradeService(client);
		try {
			upgrade.executeNodeStep(ctx, cfg, node);
		} catch (NodeStepException e) {
			assertEquals("API POST returned incomplete data", e.getMessage());
			throw e;
		}
		verify(call, times(2)).execute();
	}

	private Response response(InputStream stream) throws IOException {
		return response(readFromInputStream(stream));
	}

	private Response response(String json, int code) {
		Request request = new Request.Builder().url("https://example.com").build();
		ResponseBody body;
		if (json != null) {
			body = ResponseBody.create(MediaType.parse("text/json"), json);
		} else {
			body = null;
		}
		Builder builder = new Response.Builder().request(request).protocol(Protocol.HTTP_2);
		builder.body(body).code(code).message("OK");
		return builder.build();
	}

	private Response response(String json) {
		return response(json, 200);
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
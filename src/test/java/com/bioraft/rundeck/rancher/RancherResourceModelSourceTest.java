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
import com.dtolabs.rundeck.core.common.INodeSet;
import com.dtolabs.rundeck.core.plugins.configuration.ConfigurationException;
import com.dtolabs.rundeck.core.resources.ResourceModelSourceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import okhttp3.Response.Builder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import static com.bioraft.rundeck.rancher.TestHelper.resourceToJson;
import static org.junit.Assert.*;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for Nexus3OptionProvider.
 *
 * @author Karl DeBisschop <kdebisschop@gmail.com>
 * @since 2019-12-11
 */
@RunWith(MockitoJUnitRunner.class)
public class RancherResourceModelSourceTest {

	Properties configuration;

	@Mock
	HttpClient client;

	RancherResourceModelSource source;

	private final static String environment = "myEnvironment";
	private final static String nodeType = "container";
	private final static String serviceState = "running";

	@Before
	public void setUp() throws IOException {
		configuration = new Properties();
		configuration.setProperty("project", "MyProject");
		configuration.setProperty(RancherShared.RANCHER_CONFIG_ENDPOINT, "https://example.com/v2");
		configuration.setProperty(RancherShared.CONFIG_ENVIRONMENT_IDS, "1a1");
		configuration.setProperty(RancherShared.CONFIG_ACCESSKEY, "accessKey");
		configuration.setProperty(RancherShared.CONFIG_SECRETKEY, "secretKey");
		configuration.setProperty(RancherShared.CONFIG_ACCESSKEY_PATH, "keys/rancher/access.key");
		configuration.setProperty(RancherShared.CONFIG_SECRETKEY_PATH, "keys/rancher/secret.key");
		configuration.setProperty(RancherShared.CONFIG_STACK_FILTER, "mysite-dev");
		configuration.setProperty(RancherShared.CONFIG_LIMIT_ONE_CONTAINER, "true");
		configuration.setProperty(RancherShared.CONFIG_HANDLE_STOPPED, "Exclude");
		configuration.setProperty(RancherShared.CONFIG_HANDLE_SYSTEM, "Exclude");
		configuration.setProperty(RancherShared.CONFIG_HANDLE_GLOBAL, "Exclude");
		configuration.setProperty(RancherShared.CONFIG_TAGS, "rancher");
		configuration.setProperty(RancherShared.CONFIG_LABELS_INCLUDE_ATTRIBUTES, "");
		configuration.setProperty(RancherShared.CONFIG_LABELS_INCLUDE_TAGS, "");
	}

	@Test
	public void validateDefaultConstructor() throws ConfigurationException {
		RancherResourceModelSource subject = new RancherResourceModelSource(configuration);
		assertNotNull(subject);
	}

	@Test
	public void processOneNode() throws ResourceModelSourceException, IOException, ConfigurationException {
		when(client.get(anyString())).thenReturn(env(environment), item("1"));
		configuration.setProperty(RancherShared.CONFIG_STACK_FILTER, "");
		source = new RancherResourceModelSource(configuration, client);
		INodeSet nodeList = source.getNodes();

		verify(client, times(2)).get(anyString());

		assertEquals(1, nodeList.getNodes().size());
		INodeEntry node = nodeList.iterator().next();
		assertEquals("myEnvironment_name1", node.getNodename());
		assertEquals("hostId1", node.getHostname());
		assertEquals("root", node.getUsername());
		Map<String, String> attributes = node.getAttributes();
		assertEquals("externalId1", attributes.get("externalId"));
		assertEquals(serviceState, attributes.get("state"));
	}

	@Test
	public void processServices() throws ResourceModelSourceException, IOException, ConfigurationException {
		configuration.setProperty(RancherShared.CONFIG_ENVIRONMENT_IDS, "1a10");
		configuration.setProperty(RancherShared.CONFIG_STACK_FILTER, "");
		configuration.setProperty(RancherShared.CONFIG_NODE_TYPE_INCLUDE_SERVICE, "true");
		configuration.setProperty(RancherShared.CONFIG_NODE_TYPE_INCLUDE_CONTAINER, "false");

		JsonNode jsonNode = resourceToJson("services.json");
		assertEquals(1, jsonNode.path("data").size());
		when(client.get(anyString())).thenReturn(env(environment), jsonNode);

		source = new RancherResourceModelSource(configuration, client);
		INodeSet nodeList = source.getNodes();

		verify(client, times(1)).get(matches(".*/projects/1a10$"));
		verify(client, times(1)).get(matches(".*/projects/1a10/stacks$"));
		verify(client, times(1)).get(matches(".*/projects/1a10/services$"));
//		verify(client, times(2)).get(anyString());

		assertEquals(1, nodeList.getNodes().size());
		INodeEntry node = nodeList.iterator().next();
		assertEquals("myEnvironment_null-frontend", node.getNodename());
		assertNull(node.getHostname());
		assertEquals("root", node.getUsername());
		Map<String, String> attributes = node.getAttributes();
		assertNull(attributes.get("externalId"));
		assertEquals("active", attributes.get("state"));
	}

	@Test
	public void processNodeWithLabels() throws ResourceModelSourceException, IOException, ConfigurationException {
		when(client.get(anyString())).thenReturn(env(environment), itemPlus("1"));

		source = new RancherResourceModelSource(configuration, client);
		INodeSet nodeList = source.getNodes();

		verify(client, times(2)).get(anyString());

		assertEquals(1, nodeList.getNodes().size());
		INodeEntry node = nodeList.iterator().next();
		assertEquals("myEnvironment_name1", node.getNodename());
		assertEquals("hostId1", node.getHostname());
		assertEquals("root", node.getUsername());
		Map<String, String> attributes = node.getAttributes();
		assertEquals("externalId1", attributes.get("externalId"));
		assertEquals(serviceState, attributes.get("state"));
	}

	@Test
	public void processTwoNodes() throws ResourceModelSourceException, IOException, ConfigurationException {
		when(client.get(anyString())).thenReturn(env(environment), twoItems("1", "2"));
		configuration.setProperty(RancherShared.CONFIG_HANDLE_SYSTEM, "Include");

		source = new RancherResourceModelSource(configuration, client);
		INodeSet nodeList = source.getNodes();

		verify(client, times(2)).get(anyString());

		assertEquals(2, nodeList.getNodes().size());
		Iterator<INodeEntry> iterator = nodeList.iterator();
		INodeEntry node;
		Map<String, String> attributes;

		node = iterator.next();
		assertEquals("myEnvironment_name1", node.getNodename());
		assertEquals("hostId1", node.getHostname());
		assertEquals("root", node.getUsername());
		attributes = node.getAttributes();
		assertEquals("externalId1", attributes.get("externalId"));
		assertEquals(serviceState, attributes.get("state"));

		node = iterator.next();
		assertEquals("myEnvironment_name2", node.getNodename());
		assertEquals("hostId2", node.getHostname());
		assertEquals("root", node.getUsername());
		attributes = node.getAttributes();
		assertEquals("externalId2", attributes.get("externalId"));
		assertEquals(serviceState, attributes.get("state"));
	}

	@Test
	public void processContinued() throws ResourceModelSourceException, IOException, ConfigurationException {
		String url = configuration.getProperty(RancherShared.RANCHER_CONFIG_ENDPOINT);
		when(client.get(anyString())).thenReturn(env(environment), continuedItems("1", "2", url), item("3"));

		source = new RancherResourceModelSource(configuration, client);
		INodeSet nodeList = source.getNodes();

		verify(client, times(3)).get(anyString());

		assertEquals(3, nodeList.getNodes().size());
		Iterator<INodeEntry> iterator = nodeList.iterator();
		INodeEntry node;
		Map<String, String> attributes;

		node = iterator.next();
		assertEquals("myEnvironment_name1", node.getNodename());
		assertEquals("hostId1", node.getHostname());
		assertEquals("root", node.getUsername());
		attributes = node.getAttributes();
		assertEquals("externalId1", attributes.get("externalId"));
		assertEquals(serviceState, attributes.get("state"));

		node = iterator.next();
		assertEquals("myEnvironment_name2", node.getNodename());
		assertEquals("hostId2", node.getHostname());
		assertEquals("root", node.getUsername());
		attributes = node.getAttributes();
		assertEquals("externalId2", attributes.get("externalId"));
		assertEquals(serviceState, attributes.get("state"));
	}

	@Test
	public void throwExceptionWhenEnvironmentNameQueryFails() throws ConfigurationException, IOException, ResourceModelSourceException {
		RancherResourceModelSource subject = new RancherResourceModelSource(configuration, client);
		when(client.get(anyString())).thenThrow(new IOException());
		INodeSet nodes = subject.getNodes();
		verify(client, times(2)).get(anyString());
		assertEquals(0, nodes.getNodes().size());
	}

	private Response response(String json) {
		Request request = new Request.Builder().url("https://example.com").build();
		ResponseBody body = ResponseBody.create(MediaType.parse("text/json"), json);
		Builder builder = new Response.Builder().request(request).protocol(Protocol.HTTP_2);
		builder.body(body).code(200).message("OK");
		return builder.build();
	}

	private JsonNode item(String item) throws JsonProcessingException {
		return toJson("{\"data\":[" + itemText(item) + "]}");
	}

	private JsonNode itemPlus(String item) throws JsonProcessingException {
		return toJson("{\"data\":[" + itemPlusText(item) + "]}");
	}

	private JsonNode twoItems(String item1, String item2) throws JsonProcessingException {
		return toJson("{\"data\":[" + itemText(item1) + "," + itemText(item2) + "]}");
	}

	private JsonNode continuedItems(String item1, String item2, String url) throws JsonProcessingException {
		return toJson("{\"data\":[" + itemText(item1) + "," + itemText(item2) + "],  \"pagination\": {\"next\": \"" + url +"\"}}");
	}

	private String itemText(String item) {
		String accessKeyPath = RancherShared.CONFIG_ACCESSKEY_PATH;
		String secretKeyPath = RancherShared.CONFIG_SECRETKEY_PATH;
		return "{\"state\": \"" + serviceState + "\"" + //
				",\"name\": \"name" + item + "\"" + //
				",\"hostId\": \"hostId" + item + "\"" + //
				",\"id\": \"id" + item + "\"" + //
				",\"externalId\": \"externalId" + item + "\"" + //
				",\"file-copier\": \"" + RancherShared.RANCHER_SERVICE_PROVIDER + "\"" + //
				",\"node-executor\": \"" + RancherShared.RANCHER_SERVICE_PROVIDER + "\"" + //
				",\"type\": \"" + nodeType + "\"" + //
				",\"account\": \"" + configuration.getProperty(RancherShared.CONFIG_ENVIRONMENT_IDS) + "\"" + //
				",\"environment\": \"" + environment + "\"" + //
				",\"image\": \"image" + item + "\"" + //
				",\"" + accessKeyPath + "\": \"" + accessKeyPath + item + "\"" + //
				",\"" + secretKeyPath + "\": \"" + secretKeyPath + item + "\"" + //
				"}";
	}

	private String itemPlusText(String item) {
		String accessKeyPath = RancherShared.CONFIG_ACCESSKEY_PATH;
		String secretKeyPath = RancherShared.CONFIG_SECRETKEY_PATH;
		return "{\"state\": \"" + serviceState + "\"" + //
				",\"name\": \"name" + item + "\"" + //
				",\"hostId\": \"hostId" + item + "\"" + //
				",\"id\": \"id" + item + "\"" + //
				",\"externalId\": \"externalId" + item + "\"" + //
				",\"file-copier\": \"" + RancherShared.RANCHER_SERVICE_PROVIDER + "\"" + //
				",\"node-executor\": \"" + RancherShared.RANCHER_SERVICE_PROVIDER + "\"" + //
				",\"type\": \"" + nodeType + "\"" + //
				",\"account\": \"" + configuration.getProperty(RancherShared.CONFIG_ENVIRONMENT_IDS) + "\"" + //
				",\"environment\": \"" + environment + "\"" + //
				",\"image\": \"image" + item + "\"" + //
				",\"labels\": {\n" +
				"    \"com.example.description\": \"mysite.development.example.com\",\n" +
				"    \"com.example.group\": \"dev\",\n" +
				"    \"com.example.service\": \"frontend\",\n" +
				"    \"com.example.site\": \"mysite\",\n" +
				"    \"io.rancher.cni.network\": \"ipsec\",\n" +
				"    \"io.rancher.cni.wait\": \"true\",\n" +
				"    \"io.rancher.project.name\": \"mysite-dev\",\n" +
				"    \"io.rancher.project_service.name\": \"mysite-dev/frontend\",\n" +
				"    \"io.rancher.service.deployment.unit\": \"00000000-0000-0000-0000-000000000000\",\n" +
				"    \"io.rancher.stack_service.name\": \"mysite-dev/frontend\",\n" +
				"    \"io.rancher.service.hash\": \"0123456789012345678901234567890123456789\",\n" +
				"    \"io.rancher.service.launch.config\": \"io.rancher.service.primary.launch.config\",\n" +
				"    \"io.rancher.stack.name\": \"mysite-dev\",\n" +
				"    \"io.rancher.container.ip\": \"10.0.0.11/16\",\n" +
				"    \"io.rancher.container.uuid\": \"00000000-0000-0000-0000-000000000000\",\n" +
				"    \"io.rancher.container.mac_address\": \"11:22:33:44:55:66\",\n" +
				"    \"io.rancher.container.name\": \"mysite-dev-frontend-1\"\n" +
				"}\n" + //
				",\"" + accessKeyPath + "\": \"" + accessKeyPath + item + "\"" + //
				",\"" + secretKeyPath + "\": \"" + secretKeyPath + item + "\"" + //
				"}";
	}

	private JsonNode env(String item) throws JsonProcessingException {
		String json = "{\"name\": \"" + item + "\"}";
		return toJson(json);
	}

	private JsonNode toJson(String json) {
		ObjectMapper objectMapper = new ObjectMapper();
		try {
			return objectMapper.readTree(json);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			fail();
			return null;
		}
	}
}
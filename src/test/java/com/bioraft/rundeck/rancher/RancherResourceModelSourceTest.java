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

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Properties;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.dtolabs.rundeck.core.common.Framework;
import com.dtolabs.rundeck.core.common.INodeEntry;
import com.dtolabs.rundeck.core.common.INodeSet;
import com.dtolabs.rundeck.core.resources.ResourceModelSourceException;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Response.Builder;
import okhttp3.ResponseBody;

/**
 * Tests for Nexus3OptionProvider.
 *
 * @author Karl DeBisschop <kdebisschop@gmail.com>
 * @since 2019-12-11
 */
@RunWith(MockitoJUnitRunner.class)
public class RancherResourceModelSourceTest {

	@Mock
	Framework framework;

	Properties configuration;

	@Mock
	OkHttpClient client;

	@Mock
	Call call;

	RancherResourceModelSource source;
	
	@Before
	public void setUp() {
		configuration = new Properties();
		configuration.setProperty("project", "MyProject");
		configuration.setProperty(RancherShared.CONFIG_ENDPOINT, "https://example.com/v2");
		configuration.setProperty(RancherShared.CONFIG_ENVIRONMENT_IDS, "1a1");
		configuration.setProperty(RancherShared.CONFIG_ACCESSKEY, "accessKey");
		configuration.setProperty(RancherShared.CONFIG_SECRETKEY, "secretKey");
		configuration.setProperty(RancherShared.CONFIG_ACCESSKEY_PATH, "keys/rancher/access.key");
		configuration.setProperty(RancherShared.CONFIG_SECRETKEY_PATH, "keys/rancher/secret.key");
		configuration.setProperty(RancherShared.CONFIG_STACK_FILTER, "my-container");
		configuration.setProperty(RancherShared.CONFIG_LIMIT_ONE_CONTAINER, "true");
		configuration.setProperty(RancherShared.CONFIG_HANDLE_STOPPED, "Exclude");
		configuration.setProperty(RancherShared.CONFIG_HANDLE_SYSTEM, "Exclude");
		configuration.setProperty(RancherShared.CONFIG_HANDLE_GLOBAL, "Exclude");
		configuration.setProperty(RancherShared.CONFIG_TAGS, "rancher");
		configuration.setProperty(RancherShared.CONFIG_LABELS_INCLUDE_ATTRIBUTES, "org[.]example[.]tag1");
		configuration.setProperty(RancherShared.CONFIG_LABELS_INCLUDE_TAGS, "org[.]example[.]tag2");

		when(client.newCall(any())).thenReturn(call);
	}

	@Test
	public void processOneNode() throws ResourceModelSourceException, IOException {
		String json1 = "{\"name\": \"myEnvironment\"}";
		String json2 = "{\"data\":[" + item("name1") + "]}";
		when(call.execute()).thenReturn(response(json1), response(json2), response(json2));

		source = new RancherResourceModelSource(framework, configuration, client);
		INodeSet nodeList = source.getNodes();

		verify(call, times(2)).execute();
		
		assertEquals(1, nodeList.getNodes().size());
		INodeEntry node = nodeList.iterator().next();
		assertEquals("myEnvironment_name1", node.getNodename());
	}

	private Response response(String json) {
		Request request = new Request.Builder().url("https://example.com").build();
		ResponseBody body = ResponseBody.create(MediaType.parse("text/json"), json);
		Builder builder = new Response.Builder().request(request).protocol(Protocol.HTTP_2);
		builder.body(body).code(200).message("OK");
		return builder.build();
	}

	private String item(String item) {
		return "{\"state\": \"running\", \"name\": \"" + item + "\"}";
	}

}
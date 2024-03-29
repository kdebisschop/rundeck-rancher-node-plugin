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

import com.dtolabs.rundeck.core.execution.ExecutionContext;
import com.dtolabs.rundeck.core.execution.workflow.steps.StepException;
import com.dtolabs.rundeck.plugins.step.PluginStepContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.Map;

import static com.bioraft.rundeck.rancher.Constants.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Tests for RancherAddService.
 *
 * @author Karl DeBisschop <kdebisschop@gmail.com>
 * @since 2019-12-11
 */
@RunWith(MockitoJUnitRunner.class)
public class RancherAddServiceTest extends PluginStepTest {

	RancherAddService upgrade;

	final String url = projectEndpoint + "/projects/";
	final String envIds = "1a10";
	final String imageUuid = "repo/image:tag";
	final String serviceName = "testService";
	final String stackId = "1st99";
	final String stackName = "testStack";
	Map<String, Object> postMap;

	@Mock
	PluginStepContext pluginStepContext;

	@Mock
	ExecutionContext noStorageExecutionContext;

	@Captor
	ArgumentCaptor<Map<String, Object>> captor;

	private AutoCloseable closeable;
	
	@Before
	public void implSetUp() throws Exception {
		try (AutoCloseable closeable = MockitoAnnotations.openMocks(this)) {
			this.closeable = closeable;
			setUp();
		}
	}
	
	@After
	public void closeService() throws Exception {
		this.closeable.close();
	}

	@Test
	public void validateDefaultConstructor() {
		RancherAddService subject = new RancherAddService();
		assertNotNull(subject);
	}

	@Test(expected = StepException.class)
	public void throwExceptionWhenAccessKeyIsMissing() throws StepException {
		when(cfg.getOrDefault(eq(OPT_STACK_NAME), any())).thenReturn(stackName);
		when(cfg.getOrDefault(eq(OPT_ENV_IDS), any())).thenReturn(envIds);
		when(cfg.getOrDefault(eq(OPT_SERVICE_NAME), any())).thenReturn(serviceName);
		when(cfg.getOrDefault(eq(OPT_IMAGE_UUID), any())).thenReturn(imageUuid);

		when(framework.getProperty(FMWK_RANCHER_ENDPOINT)).thenReturn("https://rancher.example.com/v1");
		when(framework.getProperty(FMWK_RANCHER_ACCESSKEY_PATH)).thenReturn(null);

		when(pluginStepContext.getFramework()).thenReturn(framework);
		when(pluginStepContext.getFrameworkProject()).thenReturn(projectName);
		when(pluginStepContext.getExecutionContext()).thenReturn(noStorageExecutionContext);

		upgrade = new RancherAddService(client);
		upgrade.executeStep(pluginStepContext, cfg);
	}

	@Test
	public void whenStackIdIsGiven() throws StepException, IOException {
		String stackIdRequest = url + envIds + "/stacks/" + stackId;
		String upgradePostUrl = url + envIds + "/services";

		// If Project Endpoint is not defined, use framework endpoint.
		stackIdRequest = stackIdRequest.replace("v2-beta", "v1");
		upgradePostUrl = upgradePostUrl.replace("v2-beta", "v1");

		when(framework.getProjectProperty(projectName, PROJ_RANCHER_ACCESSKEY_PATH)).thenReturn(projectAccessKey);
		when(framework.getProjectProperty(projectName, PROJ_RANCHER_SECRETKEY_PATH)).thenReturn(projectSecretKey);
		when(cfg.getOrDefault(eq(OPT_STACK_NAME), any())).thenReturn(stackId);
		when(cfg.getOrDefault(eq(OPT_ENV_IDS), any())).thenReturn(envIds);
		when(cfg.getOrDefault(eq(OPT_SERVICE_NAME), any())).thenReturn(serviceName);
		when(cfg.getOrDefault(eq(OPT_IMAGE_UUID), any())).thenReturn(imageUuid);

		JsonNode stack = readFromInputStream(getResourceStream("stack.json"));
		when(client.get(stackIdRequest)).thenReturn(stack);

		JsonNode service = readFromInputStream(getResourceStream("service.json"));
		when(client.post(anyString(), anyMap())).thenReturn(service);

		upgrade = new RancherAddService(client);
		upgrade.executeStep(ctx, cfg);

		verify(client, times(1)).get(stackIdRequest);
		verify(client, times(0)).get(anyString(), anyMap());
		verify(client, times(1)).post(eq(upgradePostUrl), captor.capture());
		Map<String, Object> postMap = captor.getValue();
		assertEquals(serviceName, postMap.get("name").toString());
	}

	@Test(expected = StepException.class)
	public void throwWhenPostFails() throws StepException, IOException {
		when(framework.getProjectProperty(projectName, PROJ_RANCHER_ENDPOINT)).thenReturn(projectEndpoint);
		when(framework.getProjectProperty(projectName, PROJ_RANCHER_ACCESSKEY_PATH)).thenReturn(projectAccessKey);
		when(framework.getProjectProperty(projectName, PROJ_RANCHER_SECRETKEY_PATH)).thenReturn(projectSecretKey);
		when(cfg.getOrDefault(eq(OPT_STACK_NAME), any())).thenReturn(stackId);
		when(cfg.getOrDefault(eq(OPT_ENV_IDS), any())).thenReturn(envIds);
		when(cfg.getOrDefault(eq(OPT_SERVICE_NAME), any())).thenReturn(serviceName);
		when(cfg.getOrDefault(eq(OPT_IMAGE_UUID), any())).thenReturn(imageUuid);

		JsonNode stack = readFromInputStream(getResourceStream("stack.json"));
		when(client.get(anyString())).thenReturn(stack);

		when(client.post(anyString(), anyMap())).thenThrow(new IOException());

		upgrade = new RancherAddService(client);
		upgrade.executeStep(ctx, cfg);

		verify(client, times(1)).get(anyString());
		verify(client, times(0)).get(anyString(), anyMap());
		verify(client, times(1)).post(anyString(), anyMap());
	}

	@Test
	public void whenStackNameIsGiven() throws StepException, IOException {
		when(cfg.getOrDefault(eq(OPT_STACK_NAME), any())).thenReturn(stackName);
		when(cfg.getOrDefault(eq(OPT_ENV_IDS), any())).thenReturn(envIds);
		when(cfg.getOrDefault(eq(OPT_SERVICE_NAME), any())).thenReturn(serviceName);
		when(cfg.getOrDefault(eq(OPT_IMAGE_UUID), any())).thenReturn(imageUuid);
		runSuccess();
		assertFalse(getPostMapLaunchConfig().containsKey(OPT_LABELS));
		assertFalse(getPostMapLaunchConfig().containsKey(OPT_ENV_VARS));
		assertFalse(getPostMapLaunchConfig().containsKey(OPT_DATA_VOLUMES));
	}

	@Test(expected = StepException.class)
	public void whenStackDoesNotExist() throws StepException, IOException {
		when(cfg.getOrDefault(eq(OPT_STACK_NAME), any())).thenReturn(stackName);
		when(cfg.getOrDefault(eq(OPT_ENV_IDS), any())).thenReturn(envIds);
		when(cfg.getOrDefault(eq(OPT_SERVICE_NAME), any())).thenReturn(serviceName);
		when(cfg.getOrDefault(eq(OPT_IMAGE_UUID), any())).thenReturn(imageUuid);

		JsonNode notFound = readFromInputStream(getResourceStream("not-found.json"));
		when(client.get(anyString())).thenReturn(notFound);
		
		upgrade = new RancherAddService(client);
		upgrade.executeStep(ctx, cfg);

		verify(client, times(1)).get(anyString());
		verify(client, times(1)).get(anyString(), anyMap());
		verify(client, times(0)).post(anyString(), anyMap());
	}

	@Test(expected = StepException.class)
	public void whenStackTypeIsError() throws StepException, IOException {
		when(cfg.getOrDefault(eq(OPT_STACK_NAME), any())).thenReturn(stackName);
		when(cfg.getOrDefault(eq(OPT_ENV_IDS), any())).thenReturn(envIds);
		when(cfg.getOrDefault(eq(OPT_SERVICE_NAME), any())).thenReturn(serviceName);
		when(cfg.getOrDefault(eq(OPT_IMAGE_UUID), any())).thenReturn(imageUuid);

		JsonNode notFound = readFromInputStream(getResourceStream("not-found.json"));
		when(client.get(anyString())).thenReturn(notFound);

		ObjectNode noStacks = (ObjectNode) readFromInputStream(getResourceStream("stacks.json"));
		JsonNode data = noStacks.get("data");
		ObjectNode stack = (ObjectNode) data.elements().next();
		stack.remove("id");
//		when(client.get(anyString(), anyMap())).thenReturn(noStacks);

		upgrade = new RancherAddService(client);
		upgrade.executeStep(ctx, cfg);

		verify(client, times(1)).get(anyString());
		verify(client, times(1)).get(anyString(), anyMap());
		verify(client, times(0)).post(anyString(), anyMap());
	}

	@Test(expected = StepException.class)
	public void whenStackIdIsNull() throws StepException, IOException {
		when(cfg.getOrDefault(eq(OPT_STACK_NAME), any())).thenReturn(stackName);
		when(cfg.getOrDefault(eq(OPT_ENV_IDS), any())).thenReturn(envIds);
		when(cfg.getOrDefault(eq(OPT_SERVICE_NAME), any())).thenReturn(serviceName);
		when(cfg.getOrDefault(eq(OPT_IMAGE_UUID), any())).thenReturn(imageUuid);

		ObjectNode notFound = (ObjectNode) readFromInputStream(getResourceStream("not-found.json"));
		notFound.put("type", "error");
		when(client.get(anyString())).thenThrow(new IOException());

		upgrade = new RancherAddService(client);
		upgrade.executeStep(ctx, cfg);

		verify(client, times(1)).get(anyString());
		verify(client, times(1)).get(anyString(), anyMap());
		verify(client, times(0)).post(anyString(), anyMap());
	}

	@Test(expected = StepException.class)
	public void throwExceptionWhenStackIsNotSet() throws StepException, IOException {
		when(cfg.getOrDefault(eq(OPT_STACK_NAME), any())).thenReturn("");
		runSuccess();
	}

	@Test(expected = StepException.class)
	public void throwExceptionWhenEnvironmentIdIsNotSet() throws StepException, IOException {
		when(cfg.getOrDefault(eq(OPT_STACK_NAME), any())).thenReturn(stackName);
		when(cfg.getOrDefault(eq(OPT_ENV_IDS), any())).thenReturn("");
		runSuccess();
	}

	@Test(expected = StepException.class)
	public void throwExceptionWhenServiceNameIsNotSet() throws StepException, IOException {
		when(cfg.getOrDefault(eq(OPT_STACK_NAME), any())).thenReturn(stackName);
		when(cfg.getOrDefault(eq(OPT_ENV_IDS), any())).thenReturn(envIds);
		when(cfg.getOrDefault(eq(OPT_SERVICE_NAME), any())).thenReturn("");
		runSuccess();
	}

	@Test(expected = StepException.class)
	public void throwExceptionWhenImageUuidIsNotSet() throws StepException, IOException {
		when(cfg.getOrDefault(eq(OPT_STACK_NAME), any())).thenReturn(stackName);
		when(cfg.getOrDefault(eq(OPT_ENV_IDS), any())).thenReturn(envIds);
		when(cfg.getOrDefault(eq(OPT_SERVICE_NAME), any())).thenReturn(serviceName);
		when(cfg.getOrDefault(eq(OPT_IMAGE_UUID), any())).thenReturn("");
		runSuccess();
	}

	@Test
	public void whenDataVolumesIsGiven() throws StepException, IOException {
		when(cfg.getOrDefault(eq(OPT_STACK_NAME), any())).thenReturn(stackName);
		when(cfg.getOrDefault(eq(OPT_ENV_IDS), any())).thenReturn(envIds);
		when(cfg.getOrDefault(eq(OPT_SERVICE_NAME), any())).thenReturn(serviceName);
		when(cfg.getOrDefault(eq(OPT_IMAGE_UUID), any())).thenReturn(imageUuid);
		String volume = "/volume:/mountPoint:ro";
		String volume2 = "/volume2:/mountPoint2";
		when(cfg.getOrDefault(eq(OPT_DATA_VOLUMES), any())).thenReturn("[\"" + volume + "\",\"" + volume2 + "\"]");
		runSuccess();
		assertEquals(volume, getPostMapLaunchConfig().get(OPT_DATA_VOLUMES).elements().next().asText());
	}

	@Test
	public void whenSecretsAreGiven() throws StepException, IOException {
		when(cfg.getOrDefault(eq(OPT_STACK_NAME), any())).thenReturn(stackName);
		when(cfg.getOrDefault(eq(OPT_ENV_IDS), any())).thenReturn(envIds);
		when(cfg.getOrDefault(eq(OPT_SERVICE_NAME), any())).thenReturn(serviceName);
		when(cfg.getOrDefault(eq(OPT_IMAGE_UUID), any())).thenReturn(imageUuid);
		when(cfg.getOrDefault(eq(OPT_SECRETS), any())).thenReturn("1se1,1se2");
		runSuccess();
		assertEquals("1se1", getPostMapLaunchConfig().get(OPT_SECRETS).elements().next().get("secretId").asText());
	}

	@Test
	public void whenEmptySecretAreGiven() throws StepException, IOException {
		when(cfg.getOrDefault(eq(OPT_STACK_NAME), any())).thenReturn(stackName);
		when(cfg.getOrDefault(eq(OPT_ENV_IDS), any())).thenReturn(envIds);
		when(cfg.getOrDefault(eq(OPT_SERVICE_NAME), any())).thenReturn(serviceName);
		when(cfg.getOrDefault(eq(OPT_IMAGE_UUID), any())).thenReturn(imageUuid);
		when(cfg.getOrDefault(eq(OPT_SECRETS), any())).thenReturn("");
		runSuccess();
		assertFalse(getPostMapLaunchConfig().containsKey(OPT_SECRETS));
	}

	@Test
	public void whenEnvironmentIsGiven() throws StepException, IOException {
		when(cfg.getOrDefault(eq(OPT_STACK_NAME), any())).thenReturn(stackName);
		when(cfg.getOrDefault(eq(OPT_ENV_IDS), any())).thenReturn(envIds);
		when(cfg.getOrDefault(eq(OPT_SERVICE_NAME), any())).thenReturn(serviceName);
		when(cfg.getOrDefault(eq(OPT_IMAGE_UUID), any())).thenReturn(imageUuid);
		when(cfg.getOrDefault(eq(OPT_ENV_VARS), any())).thenReturn("{\"VAR\":\"VAL\"}");
		runSuccess();
		assertEquals("VAL", getPostMapLaunchConfig().get(OPT_ENV_VARS).get("VAR").asText());
	}

	@Test
	public void whenEnvironmentIsNull() throws StepException, IOException {
		when(cfg.getOrDefault(eq(OPT_STACK_NAME), any())).thenReturn(stackName);
		when(cfg.getOrDefault(eq(OPT_ENV_IDS), any())).thenReturn(envIds);
		when(cfg.getOrDefault(eq(OPT_SERVICE_NAME), any())).thenReturn(serviceName);
		when(cfg.getOrDefault(eq(OPT_IMAGE_UUID), any())).thenReturn(imageUuid);
		when(cfg.getOrDefault(eq(OPT_ENV_VARS), any())).thenReturn("");
		runSuccess();
		assertFalse(getPostMapLaunchConfig().containsKey(OPT_ENV_VARS));
	}

	@Test(expected = StepException.class)
	public void throwExceptionWhenEnvVarsJsonIsInvalid() throws StepException, IOException {
		when(cfg.getOrDefault(eq(OPT_STACK_NAME), any())).thenReturn(stackName);
		when(cfg.getOrDefault(eq(OPT_ENV_IDS), any())).thenReturn(envIds);
		when(cfg.getOrDefault(eq(OPT_SERVICE_NAME), any())).thenReturn(serviceName);
		when(cfg.getOrDefault(eq(OPT_IMAGE_UUID), any())).thenReturn(imageUuid);
		when(cfg.getOrDefault(eq(OPT_ENV_VARS), any())).thenReturn("{\"a\":\"1\" \"b\":\"2\"}");
		runSuccess();
	}

	@Test
	public void whenLabelsIsGiven() throws StepException, IOException {
		when(cfg.getOrDefault(eq(OPT_STACK_NAME), any())).thenReturn(stackName);
		when(cfg.getOrDefault(eq(OPT_ENV_IDS), any())).thenReturn(envIds);
		when(cfg.getOrDefault(eq(OPT_SERVICE_NAME), any())).thenReturn(serviceName);
		when(cfg.getOrDefault(eq(OPT_IMAGE_UUID), any())).thenReturn(imageUuid);
		when(cfg.getOrDefault(eq(OPT_LABELS), any())).thenReturn("{\"VAR\":\"VAL\"}");
		runSuccess();
		assertEquals("VAL", getPostMapLaunchConfig().get(OPT_LABELS).get("VAR").asText());
	}

	private void runSuccess() throws IOException, StepException {
		when(framework.getProjectProperty(projectName, PROJ_RANCHER_ENDPOINT)).thenReturn(projectEndpoint);

		String stackIdRequest = url + envIds + "/stacks/" + stackName;
		String stackNameRequest = url + envIds + "/stacks?name=" + stackName;
		String upgradePostUrl = url + envIds + "/services";

		// When given a stack, first look for a matching stack ID.
		JsonNode notFound = readFromInputStream(getResourceStream("not-found.json"));
		when(client.get(stackIdRequest)).thenReturn(notFound);

		// If there is no matching ID, then look for a matching name.
		JsonNode stacks = readFromInputStream(getResourceStream("stacks.json"));
		String stackId = stacks.get("data").elements().next().get("id").asText();
		when(client.get(stackNameRequest)).thenReturn(stacks);

		JsonNode service = readFromInputStream(getResourceStream("service.json"));
		when(client.post(anyString(), anyMap())).thenReturn(service);

		upgrade = new RancherAddService(client);
		upgrade.executeStep(ctx, cfg);

		verify(client, times(1)).get(stackIdRequest);
		verify(client, times(1)).get(stackNameRequest);
		verify(client, times(1)).post(eq(upgradePostUrl), captor.capture());

		postMap = captor.getValue();
		assertEquals(serviceName, postMap.get("name").toString());
		assertEquals(stackId, postMap.get("stackId").toString());
	}

	@SuppressWarnings("unchecked")
	private Map<String, JsonNode> getPostMapLaunchConfig() {
		return (Map<String, JsonNode>) postMap.get("launchConfig");

	}
}
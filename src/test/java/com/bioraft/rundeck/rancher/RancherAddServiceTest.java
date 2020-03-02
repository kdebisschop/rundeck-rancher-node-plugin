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

import com.dtolabs.rundeck.core.execution.workflow.steps.StepException;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;

import static com.bioraft.rundeck.rancher.Constants.*;
import static com.bioraft.rundeck.rancher.RancherShared.*;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.eq;
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

	@Before
	public void implSetUp() {
		setUp();
	}

	@Test
	public void validateDefaultConstructor() {
		RancherAddService subject = new RancherAddService();
		assertNotNull(subject);
	}

	@Test
	public void whenStackIdIsGiven() throws StepException, IOException {
		when(framework.getProjectProperty(projectName, PROJ_RANCHER_ENDPOINT)).thenReturn(projectEndpoint);
		when(framework.getProjectProperty(projectName, PROJ_RANCHER_ACCESSKEY_PATH)).thenReturn(projectAccessKey);
		when(framework.getProjectProperty(projectName, PROJ_RANCHER_SECRETKEY_PATH)).thenReturn(projectSecretKey);
		when(cfg.getOrDefault(eq(OPT_STACK_NAME), any())).thenReturn("testStack");
		when(cfg.getOrDefault(eq(OPT_ENV_IDS), any())).thenReturn("1a10");
		when(cfg.getOrDefault(eq(OPT_SERVICE_NAME), any())).thenReturn("testService");
		when(cfg.getOrDefault(eq(OPT_IMAGE_UUID), any())).thenReturn("repo/image:tag");

		JsonNode stack = readFromInputStream(getResourceStream("stack.json"));
		when(client.get(anyString())).thenReturn(stack);

		JsonNode service = readFromInputStream(getResourceStream("service.json"));
		when(client.post(anyString(), anyMapOf(String.class, Object.class))).thenReturn(service);

		upgrade = new RancherAddService(client);
		upgrade.executeStep(ctx, cfg);

		verify(client, times(1)).get(anyString());
		verify(client, times(0)).get(anyString(), anyMapOf(String.class, String.class));
		verify(client, times(1)).post(anyString(), anyMapOf(String.class, Object.class));
	}

	@Test
	public void whenStackNameIsGiven() throws StepException, IOException {
		when(cfg.getOrDefault(eq(OPT_STACK_NAME), any())).thenReturn("testStack");
		when(cfg.getOrDefault(eq(OPT_ENV_IDS), any())).thenReturn("1a10");
		when(cfg.getOrDefault(eq(OPT_SERVICE_NAME), any())).thenReturn("testService");
		when(cfg.getOrDefault(eq(OPT_IMAGE_UUID), any())).thenReturn("repo/image:tag");
		runSuccess();
	}

	@Test(expected = StepException.class)
	public void whenStackDoesNotExist() throws StepException, IOException {
		when(cfg.getOrDefault(eq(OPT_STACK_NAME), any())).thenReturn("testStack");
		when(cfg.getOrDefault(eq(OPT_ENV_IDS), any())).thenReturn("1a10");
		when(cfg.getOrDefault(eq(OPT_SERVICE_NAME), any())).thenReturn("testService");
		when(cfg.getOrDefault(eq(OPT_IMAGE_UUID), any())).thenReturn("repo/image:tag");

		JsonNode notFound = readFromInputStream(getResourceStream("not-found.json"));
		when(client.get(anyString())).thenReturn(notFound);

		JsonNode noStacks = readFromInputStream(getResourceStream("no-stacks.json"));
		when(client.get(anyString(), anyMapOf(String.class, String.class))).thenReturn(noStacks);

		upgrade = new RancherAddService(client);
		upgrade.executeStep(ctx, cfg);

		verify(client, times(1)).get(anyString());
		verify(client, times(1)).get(anyString(), anyMapOf(String.class, String.class));
		verify(client, times(0)).post(anyString(), anyMapOf(String.class, Object.class));
	}

	@Test(expected = StepException.class)
	public void whenStackIsNotSet() throws StepException, IOException {
		when(cfg.getOrDefault(eq(OPT_STACK_NAME), any())).thenReturn("");
		when(cfg.getOrDefault(eq(OPT_ENV_IDS), any())).thenReturn("1a10");
		when(cfg.getOrDefault(eq(OPT_SERVICE_NAME), any())).thenReturn("testService");
		when(cfg.getOrDefault(eq(OPT_IMAGE_UUID), any())).thenReturn("repo/image:tag");
		runSuccess();
	}

	@Test(expected = StepException.class)
	public void whenEnvironmentIdIsNotSet() throws StepException, IOException {
		when(cfg.getOrDefault(eq(OPT_STACK_NAME), any())).thenReturn("testStack");
		when(cfg.getOrDefault(eq(OPT_ENV_IDS), any())).thenReturn("");
		when(cfg.getOrDefault(eq(OPT_SERVICE_NAME), any())).thenReturn("testService");
		when(cfg.getOrDefault(eq(OPT_IMAGE_UUID), any())).thenReturn("repo/image:tag");
		runSuccess();
	}

	@Test(expected = StepException.class)
	public void whenServiceNameIsNotSet() throws StepException, IOException {
		when(cfg.getOrDefault(eq(OPT_STACK_NAME), any())).thenReturn("testStack");
		when(cfg.getOrDefault(eq(OPT_ENV_IDS), any())).thenReturn("1a10");
		when(cfg.getOrDefault(eq(OPT_SERVICE_NAME), any())).thenReturn("");
		when(cfg.getOrDefault(eq(OPT_IMAGE_UUID), any())).thenReturn("repo/image:tag");
		runSuccess();
	}

	@Test(expected = StepException.class)
	public void whenImageUuidIsNotSet() throws StepException, IOException {
		when(cfg.getOrDefault(eq(OPT_STACK_NAME), any())).thenReturn("testStack");
		when(cfg.getOrDefault(eq(OPT_ENV_IDS), any())).thenReturn("1a10");
		when(cfg.getOrDefault(eq(OPT_SERVICE_NAME), any())).thenReturn("testService");
		when(cfg.getOrDefault(eq(OPT_IMAGE_UUID), any())).thenReturn("");
		runSuccess();
	}

	@Test
	public void whenDataVolumesIsGiven() throws StepException, IOException {
		when(cfg.getOrDefault(eq(OPT_STACK_NAME), any())).thenReturn("testStack");
		when(cfg.getOrDefault(eq(OPT_ENV_IDS), any())).thenReturn("1a10");
		when(cfg.getOrDefault(eq(OPT_SERVICE_NAME), any())).thenReturn("testService");
		when(cfg.getOrDefault(eq(OPT_IMAGE_UUID), any())).thenReturn("repo/image:tag");
		when(cfg.containsKey(eq(OPT_DATA_VOLUMES))).thenReturn(true);
		when(cfg.get(eq(OPT_DATA_VOLUMES))).thenReturn("[]");
		runSuccess();
	}

	@Test
	public void whenEnvironmentIsGiven() throws StepException, IOException {
		when(cfg.getOrDefault(eq(OPT_STACK_NAME), any())).thenReturn("testStack");
		when(cfg.getOrDefault(eq(OPT_ENV_IDS), any())).thenReturn("1a10");
		when(cfg.getOrDefault(eq(OPT_SERVICE_NAME), any())).thenReturn("testService");
		when(cfg.getOrDefault(eq(OPT_IMAGE_UUID), any())).thenReturn("repo/image:tag");
		when(cfg.containsKey(eq(OPT_ENV_VARS))).thenReturn(true);
		when(cfg.get(eq(OPT_ENV_VARS))).thenReturn("{}");
		runSuccess();
	}

	@Test
	public void whenLabelsIsGiven() throws StepException, IOException {
		when(cfg.getOrDefault(eq(OPT_STACK_NAME), any())).thenReturn("testStack");
		when(cfg.getOrDefault(eq(OPT_ENV_IDS), any())).thenReturn("1a10");
		when(cfg.getOrDefault(eq(OPT_SERVICE_NAME), any())).thenReturn("testService");
		when(cfg.getOrDefault(eq(OPT_IMAGE_UUID), any())).thenReturn("repo/image:tag");
		when(cfg.containsKey(eq(OPT_LABELS))).thenReturn(true);
		when(cfg.get(eq(OPT_LABELS))).thenReturn("{}");
		runSuccess();
	}

	private void runSuccess() throws IOException, StepException {
		JsonNode notFound = readFromInputStream(getResourceStream("not-found.json"));
		JsonNode stacks = readFromInputStream(getResourceStream("stacks.json"));
		when(client.get(anyString())).thenReturn(notFound, stacks);

		when(client.get(anyString(), anyMapOf(String.class, String.class))).thenReturn(stacks);

		JsonNode service = readFromInputStream(getResourceStream("service.json"));
		when(client.post(anyString(), anyMapOf(String.class, Object.class))).thenReturn(service);

		upgrade = new RancherAddService(client);
		upgrade.executeStep(ctx, cfg);

		verify(client, times(2)).get(anyString());
		verify(client, times(0)).get(anyString(), anyMapOf(String.class, String.class));
		verify(client, times(1)).post(anyString(), anyMapOf(String.class, Object.class));
	}
}
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
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;

import static com.bioraft.rundeck.rancher.Constants.*;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

/**
 * Tests for RancherNewStack.
 *
 * @author Karl DeBisschop <kdebisschop@gmail.com>
 * @since 2019-12-11
 */
@RunWith(MockitoJUnitRunner.class)
public class RancherNewStackTest extends PluginStepTest {

	RancherNewStack upgrade;

	@Before
	public void implSetUp() {
		setUp();
	}

	@Test
	public void validateDefaultConstructor() {
		RancherNewStack subject = new RancherNewStack();
		assertNotNull(subject);
	}

	/**
	 * When Stack does not already exist, we create it with API POST.
	 */
	@Test
	public void whenStackDoesNotExist() throws StepException, IOException {
		when(framework.getProjectProperty(projectName, PROJ_RANCHER_ENDPOINT)).thenReturn(projectEndpoint);
		when(framework.getProjectProperty(projectName, PROJ_RANCHER_ACCESSKEY_PATH)).thenReturn(projectAccessKey);
		when(framework.getProjectProperty(projectName, PROJ_RANCHER_SECRETKEY_PATH)).thenReturn(projectSecretKey);

		when(cfg.getOrDefault(eq(OPT_STACK_NAME), any())).thenReturn("testStack");
		when(cfg.getOrDefault(eq(OPT_ENV_IDS), any())).thenReturn("1a10");

		JsonNode stacks = readFromInputStream(getResourceStream("no-stacks.json"));
		when(client.get(anyString(), anyMap())).thenReturn(stacks);

		JsonNode stack = readFromInputStream(getResourceStream("stack.json"));
		when(client.post(anyString(), anyMap())).thenReturn(stack);

		upgrade = new RancherNewStack(client);
		upgrade.executeStep(ctx, cfg);

		verify(client, times(1)).get(anyString(), anyMap());
		verify(client, times(1)).post(anyString(), anyMap());
	}

	/**
	 * When Stack already exists, we do nothing and fail.
	 */
	@Test(expected = StepException.class)
	public void whenStackExists() throws StepException, IOException {
		when(cfg.getOrDefault(eq(OPT_STACK_NAME), any())).thenReturn("testStack");
		when(cfg.getOrDefault(eq(OPT_ENV_IDS), any())).thenReturn("1a10");

		JsonNode stacks = readFromInputStream(getResourceStream("stacks.json"));
		when(client.get(anyString(), anyMap())).thenReturn(stacks);

		upgrade = new RancherNewStack(client);
		upgrade.executeStep(ctx, cfg);

		verify(client, times(1)).get(anyString(), anyMap());
		verify(client, times(0)).post(anyString(), anyMap());
	}

	/**
	 * When Stack name is empty, do nothing and fail.
	 */
	@Test(expected = StepException.class)
	public void whenStackNameIsEmpty() throws StepException, IOException {
		when(cfg.getOrDefault(eq(OPT_STACK_NAME), any())).thenReturn("");
//		when(cfg.getOrDefault(eq(OPT_ENV_IDS), any())).thenReturn("1a10");

		upgrade = new RancherNewStack(client);
		upgrade.executeStep(ctx, cfg);

		verify(client, times(0)).get(anyString(), anyMap());
		verify(client, times(0)).post(anyString(), anyMap());
	}

	/**
	 * When Stack name is empty, do nothing and fail.
	 */
	@Test(expected = StepException.class)
	public void whenEnvironmentIsEmpty() throws StepException, IOException {
		when(cfg.getOrDefault(eq(OPT_STACK_NAME), any())).thenReturn("TestStack");
		when(cfg.getOrDefault(eq(OPT_ENV_IDS), any())).thenReturn("");

		upgrade = new RancherNewStack(client);
		upgrade.executeStep(ctx, cfg);

		verify(client, times(0)).get(anyString(), anyMap());
		verify(client, times(0)).post(anyString(), anyMap());
	}

	/**
	 * When Stack name is empty, do nothing and fail.
	 */
	@Test
	public void whenEndpointIsNull() throws StepException, IOException {
		when(cfg.getOrDefault(eq(OPT_STACK_NAME), any())).thenReturn("TestStack");
		when(cfg.getOrDefault(eq(OPT_ENV_IDS), any())).thenReturn("1a10");

		JsonNode stacks = readFromInputStream(getResourceStream("no-stacks.json"));
		when(client.get(anyString(), anyMap())).thenReturn(stacks);

		JsonNode stack = readFromInputStream(getResourceStream("stack.json"));
		when(client.post(anyString(), anyMap())).thenReturn(stack);

		upgrade = new RancherNewStack(client);
		upgrade.executeStep(ctx, cfg);

		verify(client, times(1)).get(anyString(), anyMap());
		verify(client, times(1)).post(anyString(), anyMap());
	}
}
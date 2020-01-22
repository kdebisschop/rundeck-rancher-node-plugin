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
	public void whenStackDoesNotExist() throws StepException, IOException {
		when(cfg.get("stackName")).thenReturn("testStack");
		when(cfg.get("environment")).thenReturn("1a10");

		JsonNode stacks = readFromInputStream(getResourceStream("no-stacks.json"));
		when(client.get(anyString(), anyMapOf(String.class, String.class))).thenReturn(stacks);

		JsonNode stack = readFromInputStream(getResourceStream("stack.json"));
		when(client.post(anyString(), anyMapOf(String.class, Object.class))).thenReturn(stack);

		upgrade = new RancherNewStack(client);
		upgrade.executeStep(ctx, cfg);

		verify(client, times(1)).get(anyString(), anyMapOf(String.class, String.class));
		verify(client, times(1)).post(anyString(), anyMapOf(String.class, Object.class));
	}

	@Test(expected = StepException.class)
	public void whenStackExists() throws StepException, IOException {
		when(cfg.get("stackName")).thenReturn("testStack");
		when(cfg.get("environment")).thenReturn("1a10");

		JsonNode stacks = readFromInputStream(getResourceStream("stacks.json"));
		when(client.get(anyString(), anyMapOf(String.class, String.class))).thenReturn(stacks);

		upgrade = new RancherNewStack(client);
		upgrade.executeStep(ctx, cfg);

		verify(client, times(1)).get(anyString(), anyMapOf(String.class, String.class));
		verify(client, times(0)).post(anyString(), anyMapOf(String.class, Object.class));
	}
}
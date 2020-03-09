/*
 * Copyright 2019 BioRAFT, Inc. (https://bioraft.com)
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

import com.dtolabs.rundeck.core.Constants;
import com.dtolabs.rundeck.core.common.INodeEntry;
import com.dtolabs.rundeck.core.execution.ExecutionContext;
import com.dtolabs.rundeck.core.execution.workflow.steps.node.NodeStepException;
import com.dtolabs.rundeck.core.plugins.Plugin;
import com.dtolabs.rundeck.plugins.PluginLogger;
import com.dtolabs.rundeck.plugins.ServiceNameConstants;
import com.dtolabs.rundeck.plugins.descriptions.PluginDescription;
import com.dtolabs.rundeck.plugins.descriptions.PluginProperty;
import com.dtolabs.rundeck.plugins.descriptions.SelectValues;
import com.dtolabs.rundeck.plugins.step.NodeStepPlugin;
import com.dtolabs.rundeck.plugins.step.PluginStepContext;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.Map;

import static com.bioraft.rundeck.rancher.Constants.*;
import static com.bioraft.rundeck.rancher.Errors.ErrorCause.*;

/**
 * Workflow Node Step Plug-in to manage a service associated with a node.
 *
 * @author Karl DeBisschop <kdebisschop@gmail.com>
 * @since 2019-12-20
 */
@Plugin(name = RANCHER_SERVICE_CONTROLLER, service = ServiceNameConstants.WorkflowNodeStep)
@PluginDescription(title = "Rancher - Manage Service", description = "Start/Stop/Restart the service associated with the selected node.")
public class RancherManageService implements NodeStepPlugin {

	@PluginProperty(title = "Action", description = "What action is desired", required = true)
	@SelectValues(values = {"activate", "deactivate", "restart"})
	private String action;

	final HttpClient client;

	public RancherManageService() {
		client = new HttpClient();
	}

	public RancherManageService(HttpClient client) {
		this.client = client;
	}

	@Override
	public void executeNodeStep(PluginStepContext ctx, Map<String, Object> cfg, INodeEntry node)
			throws NodeStepException {

		action = cfg.getOrDefault("action", action).toString();

		String nodeName = node.getNodename();
		ExecutionContext executionContext = ctx.getExecutionContext();
		PluginLogger logger = ctx.getLogger();

		Map<String, String> attributes = node.getAttributes();

		String accessKey;
		String secretKey;
		try {
			Storage storage = new Storage(executionContext);
			accessKey = storage.loadStoragePathData(attributes.get(CONFIG_ACCESSKEY_PATH));
			secretKey = storage.loadStoragePathData(attributes.get(CONFIG_SECRETKEY_PATH));
			client.setAccessKey(accessKey);
			client.setSecretKey(secretKey);
		} catch (IOException e) {
			throw new NodeStepException("Could not get secret storage path", e, IO_EXCEPTION, nodeName);
		}

		JsonNode service;
		try {
			if (attributes.get("type").equals("container")) {
				service = client.get(attributes.get("services")).path("data").path(0);
			} else {
				service = client.get(attributes.get("self"));
			}
		} catch (IOException e) {
			throw new NodeStepException("Could not get service definition", e, NO_SERVICE_OBJECT, nodeName);
		}
		String serviceState = service.path("state").asText();


		if (action.equals("activate")) {
			if (serviceState.equals("active")) {
				String message = "Service state is already active";
				throw new NodeStepException(message, NO_SERVICE_OBJECT, nodeName);
			}
		} else if (action.equals("deactivate") || action.equals("restart")) {
			if (!serviceState.equals("active")) {
				String message = "Service state must be running, was " + serviceState;
				throw new NodeStepException(message, SERVICE_NOT_RUNNING, nodeName);
			}
		} else {
			String message = "Invalid action: " + action;
			throw new NodeStepException(message, ACTION_NOT_SUPPORTED, nodeName);
		}

		String url = service.path("actions").path(action).asText();
		if (url.length() == 0) {
			throw new NodeStepException("No " + action + " URL found", MISSING_ACTION_URL, nodeName);
		}

		String body = "";
		try {
			client.post(url, body);
		} catch (IOException e) {
			throw new NodeStepException("Step " + action + " failed", e, ACTION_FAILED, nodeName);
		}

		logger.log(Constants.INFO_LEVEL, "Step " + action + " complete on " + nodeName);
	}
}

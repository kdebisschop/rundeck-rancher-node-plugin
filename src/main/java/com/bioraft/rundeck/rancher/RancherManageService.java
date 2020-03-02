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
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import okhttp3.Request.Builder;

import java.io.IOException;
import java.util.Map;

import static com.bioraft.rundeck.rancher.RancherShared.ErrorCause;
import static com.bioraft.rundeck.rancher.RancherShared.ErrorCause.NoServiceObject;
import static com.bioraft.rundeck.rancher.RancherShared.loadStoragePathData;

/**
 * Workflow Node Step Plug-in to upgrade a service associated with a node.
 *
 * @author Karl DeBisschop <kdebisschop@gmail.com>
 * @since 2019-12-20
 */
@Plugin(name = RancherShared.RANCHER_SERVICE_CONTROLLER, service = ServiceNameConstants.WorkflowNodeStep)
@PluginDescription(title = "Rancher - Manage Service", description = "Start/Stop/Restart the service associated with the selected node.")
public class RancherManageService implements NodeStepPlugin {

	@PluginProperty(title = "Action", description = "What action is desired", required = true)
	@SelectValues(values = {"activate", "deactivate", "restart"})
	private String action;

	private String nodeName;

	HttpClient client;

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

		this.nodeName = node.getNodename();
		ExecutionContext executionContext = ctx.getExecutionContext();
		PluginLogger logger = ctx.getLogger();

		Map<String, String> attributes = node.getAttributes();

		String accessKey;
		String secretKey;
		try {
			accessKey = loadStoragePathData(executionContext, attributes.get(RancherShared.CONFIG_ACCESSKEY_PATH));
			secretKey = loadStoragePathData(executionContext, attributes.get(RancherShared.CONFIG_SECRETKEY_PATH));
			client.setAccessKey(accessKey);
			client.setSecretKey(secretKey);
		} catch (IOException e) {
			throw new NodeStepException("Could not get secret storage path", e, ErrorCause.IOException, nodeName);
		}

		JsonNode service;
		try {
			if (attributes.get("type").equals("container")) {
				service = client.get(attributes.get("services")).path("data").path(0);
			} else {
				service = client.get(attributes.get("self"));
			}
		} catch (IOException e) {
			throw new NodeStepException("Could not get service definition", e, NoServiceObject, nodeName);
		}
		String serviceState = service.path("state").asText();


		if (action.equals("activate")) {
			if (serviceState.equals("active")) {
				String message = "Service state is already active";
				throw new NodeStepException(message, NoServiceObject, nodeName);
			}
		} else if (action.equals("deactivate") || action.equals("restart")) {
			if (!serviceState.equals("active")) {
				String message = "Service state must be running, was " + serviceState;
				throw new NodeStepException(message, ErrorCause.ServiceNotRunning, nodeName);
			}
		} else {
			String message = "Invalid action: " + action;
			throw new NodeStepException(message, ErrorCause.ActionNotSupported, nodeName);
		}

		String url = service.path("actions").path(action).asText();
		if (url.length() == 0) {
			throw new NodeStepException("No " + action + " URL found", ErrorCause.MissingActionURL, nodeName);
		}

		String body = "";
		try {
			JsonNode newService = client.post(url, body);
		} catch (IOException e) {
			throw new NodeStepException("Upgrade failed", e, ErrorCause.ActionFailed, nodeName);
		}

		logger.log(Constants.INFO_LEVEL, "Upgraded " + nodeName);
	}
}

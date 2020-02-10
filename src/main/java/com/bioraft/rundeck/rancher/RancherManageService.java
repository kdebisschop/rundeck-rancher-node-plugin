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
import static com.bioraft.rundeck.rancher.RancherShared.loadStoragePathData;

/**
 * Workflow Node Step Plug-in to upgrade a service associated with a node.
 *
 * @author Karl DeBisschop <kdebisschop@gmail.com>
 * @since 2019-12-20
 */
@Plugin(name = RancherShared.RANCHER_SERVICE_CONTROLLER, service = ServiceNameConstants.WorkflowNodeStep)
@PluginDescription(title = "Rancher - Manage Service", description = "Strart/Stop/Restart/Delete the service associated with the selected node.")
public class RancherManageService implements NodeStepPlugin {

	@PluginProperty(title = "Action", description = "What action is desired", required = true, defaultValue = "true")
	@SelectValues(values = {"activate", "deactivate", "restart"})
	private String action;

	private String nodeName;

	OkHttpClient client;

	public RancherManageService() {
		client = new OkHttpClient();
	}

	public RancherManageService(OkHttpClient client) {
		this.client = client;
	}

	@Override
	public void executeNodeStep(PluginStepContext ctx, Map<String, Object> cfg, INodeEntry node)
			throws NodeStepException {

		this.nodeName = node.getNodename();
		ExecutionContext executionContext = ctx.getExecutionContext();
		PluginLogger logger = ctx.getLogger();

		Map<String, String> attributes = node.getAttributes();

		String accessKey;
		String secretKey;
		try {
			accessKey = loadStoragePathData(executionContext, attributes.get(RancherShared.CONFIG_ACCESSKEY_PATH));
			secretKey = loadStoragePathData(executionContext, attributes.get(RancherShared.CONFIG_SECRETKEY_PATH));
		} catch (IOException e) {
			throw new NodeStepException("Could not get secret storage path", e, ErrorCause.IOException, this.nodeName);
		}

		JsonNode service = apiGet(accessKey, secretKey, attributes.get("services")).path("data").path(0);
		String serviceState = service.path("state").asText();
		String body = "";
		switch (action) {
			case "activate":
				if (serviceState.equals("active")) {
					String message = "Service state is already active";
					throw new NodeStepException(message, ErrorCause.ServiceNotRunning, node.getNodename());
				}
				break;
			case "deactivate":
			case "restart":
				if (!serviceState.equals("active")) {
					String message = "Service state must be running, was " + serviceState;
					throw new NodeStepException(message, ErrorCause.ServiceNotRunning, node.getNodename());
				}
				break;
		}
		String url = service.path("actions").path(action).asText();
		if (url.length() == 0) {
			throw new NodeStepException("No upgrade URL found", ErrorCause.MissingUpgradeURL, node.getNodename());
		}

		JsonNode newService = apiPost(accessKey, secretKey, url, body);

		logger.log(Constants.INFO_LEVEL, "Upgraded " + nodeName);
	}

	/**
	 * Gets the web socket end point and connection token for an execute request.
	 *
	 * @param accessKey Rancher access key
	 * @param secretKey Rancher secret key
	 * @param url Rancher API url
	 * @return JSON from Rancher API request body.
	 * @throws NodeStepException when there API request fails
	 */
	private JsonNode apiGet(String accessKey, String secretKey, String url) throws NodeStepException {
		try {
			Builder builder = new Builder().url(url);
			builder.addHeader("Authorization", Credentials.basic(accessKey, secretKey));
			Response response = client.newCall(builder.build()).execute();
			// Since URL comes from the Rancher server itself, assume there are no redirects.
			if (response.code() >= 300) {
				throw new IOException("API get failed" + response.message());
			}
			ObjectMapper mapper = new ObjectMapper();
			assert response.body() != null;
			return mapper.readTree(response.body().string());
		} catch (IOException e) {
			throw new NodeStepException(e.getMessage(), e, ErrorCause.NoServiceObject, nodeName);
		}
	}

	/**
	 * Gets the web socket end point and connection token for an execute request.
	 *
	 * @param accessKey Rancher access key
	 * @param secretKey Rancher secret key
	 * @param url Rancher API url
	 * @param body Document contents to POST to Rancher.
	 * @return JSON from Rancher API request body.
	 * @throws NodeStepException when there API request fails
	 */
	private JsonNode apiPost(String accessKey, String secretKey, String url, String body) throws NodeStepException {
		RequestBody postBody = RequestBody.create(MediaType.parse("application/json"), body);
		try {
			Builder builder = new Builder().url(url).post(postBody);
			builder.addHeader("Authorization", Credentials.basic(accessKey, secretKey));
			Response response = client.newCall(builder.build()).execute();
			// Since URL comes from the Rancher server itself, assume there are no redirects.
			if (response.code() >= 300) {
				throw new IOException("API post failed" + response.message());
			}
			ObjectMapper mapper = new ObjectMapper();
			assert response.body() != null;
			return mapper.readTree(response.body().string());
		} catch (IOException e) {
			throw new NodeStepException(e.getMessage(), e, ErrorCause.UpgradeFailure, nodeName);
		}
	}
}

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
import com.dtolabs.rundeck.plugins.descriptions.RenderingOption;
import com.dtolabs.rundeck.plugins.step.NodeStepPlugin;
import com.dtolabs.rundeck.plugins.step.PluginStepContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import okhttp3.*;
import okhttp3.Request.Builder;

import java.io.IOException;
import java.util.Map;

import static com.bioraft.rundeck.rancher.Constants.*;
import static com.bioraft.rundeck.rancher.Errors.ErrorCause.*;
import static com.dtolabs.rundeck.core.Constants.DEBUG_LEVEL;
import static com.dtolabs.rundeck.core.plugins.configuration.StringRenderingConstants.CODE_SYNTAX_MODE;
import static com.dtolabs.rundeck.core.plugins.configuration.StringRenderingConstants.DISPLAY_TYPE_KEY;
import static org.apache.commons.lang.StringUtils.defaultString;

/**
 * Workflow Node Step Plug-in to upgrade a service associated with a node.
 *
 * @author Karl DeBisschop <kdebisschop@gmail.com>
 * @since 2019-12-20
 */
@Plugin(name = RANCHER_SERVICE_PROVIDER, service = ServiceNameConstants.WorkflowNodeStep)
@PluginDescription(title = "Rancher - Upgrade Service/Node", description = "Upgrades the service associated with the selected node.")
public class RancherUpgradeService implements NodeStepPlugin {

	@PluginProperty(title = "Docker Image", description = "The fully specified Docker image to upgrade to.")
	private String dockerImage;

	@PluginProperty(title = "Container OS Environment", description = "JSON object of \"variable\": \"value\"")
	@RenderingOption(key = DISPLAY_TYPE_KEY, value = DISPLAY_CODE)
	@RenderingOption(key = CODE_SYNTAX_MODE, value = SYNTAX_MODE_JSON)
	private String environment;

	@PluginProperty(title = "Data Volumes", description = "JSON array Lines of \"source:mountPoint\"")
	@RenderingOption(key = DISPLAY_TYPE_KEY, value = DISPLAY_CODE)
	@RenderingOption(key = CODE_SYNTAX_MODE, value = SYNTAX_MODE_JSON)
	private String dataVolumes;

	@PluginProperty(title = "Service Labels", description = "JSON object of \"variable\": \"value\"")
	@RenderingOption(key = DISPLAY_TYPE_KEY, value = DISPLAY_CODE)
	@RenderingOption(key = CODE_SYNTAX_MODE, value = SYNTAX_MODE_JSON)
	private String labels;

	@PluginProperty(title = "Remove OS Environment", description = "JSON array of variables (quoted)")
	@RenderingOption(key = DISPLAY_TYPE_KEY, value = DISPLAY_CODE)
	@RenderingOption(key = CODE_SYNTAX_MODE, value = SYNTAX_MODE_JSON)
	private String removeEnvironment;

	@PluginProperty(title = "Remove Service Labels", description = "JSON array of labels (quoted)")
	@RenderingOption(key = DISPLAY_TYPE_KEY, value = DISPLAY_CODE)
	@RenderingOption(key = CODE_SYNTAX_MODE, value = SYNTAX_MODE_JSON)
	private String removeLabels;

	@PluginProperty(title = "Secrets", description = "Keys for secrets separated by commas or spaces")
	private String secrets;

	@PluginProperty(title = "Start before stopping", description = "Start new container(s) before stopping old", required = true, defaultValue = "true")
	private Boolean startFirst;

	private String nodeName;

	private PluginLogger logger;

	final OkHttpClient client;

	JsonNode launchConfig;

	ObjectNode launchConfigObject;

	private int sleepInterval = 5000;

	public RancherUpgradeService() {
		client = new OkHttpClient();
	}

	public RancherUpgradeService(OkHttpClient client) {
		this.client = client;
	}

	@Override
	public void executeNodeStep(PluginStepContext ctx, Map<String, Object> cfg, INodeEntry node)
			throws NodeStepException {

		this.nodeName = node.getNodename();
		ExecutionContext executionContext = ctx.getExecutionContext();
		this.logger = ctx.getLogger();

		Map<String, String> attributes = node.getAttributes();

		String accessKey;
		String secretKey;
		try {
			Storage storage = new Storage(executionContext);
			accessKey = storage.loadStoragePathData(attributes.get(CONFIG_ACCESSKEY_PATH));
			secretKey = storage.loadStoragePathData(attributes.get(CONFIG_SECRETKEY_PATH));
		} catch (IOException e) {
			throw new NodeStepException("Could not get secret storage path", e, IO_EXCEPTION, this.nodeName);
		}

		JsonNode service;
		if (attributes.get("type").equals("container")) {
			service = apiGet(accessKey, secretKey, attributes.get("services")).path("data").path(0);
		} else {
			service = apiGet(accessKey, secretKey, attributes.get("self"));
		}
		String serviceState = service.path(NODE_STATE).asText();
		if (!serviceState.equals(STATE_ACTIVE)) {
			String message = "Service state must be running, was " + serviceState;
			throw new NodeStepException(message, SERVICE_NOT_RUNNING, node.getNodename());
		}

		String upgradeUrl = service.path("actions").path("upgrade").asText("");
		if (upgradeUrl.length() == 0) {
			throw new NodeStepException("No upgrade URL found", MISSING_UPGRADE_URL, node.getNodename());
		}

		launchConfig = service.path("upgrade").path("inServiceStrategy").path(LAUNCH_CONFIG);
		if (launchConfig.isMissingNode() || launchConfig.isNull()) {
			launchConfig = service.path(LAUNCH_CONFIG);
		}
		if (launchConfig.isMissingNode() || launchConfig.isNull()) {
			throw new NodeStepException("No upgrade data found", NO_UPGRADE_DATA, node.getNodename());
		}
		launchConfigObject = (ObjectNode) launchConfig;

		RancherLaunchConfig rancherLaunchConfig = new RancherLaunchConfig(nodeName, launchConfigObject, logger);

		dockerImage = (String) cfg.getOrDefault("dockerImage", defaultString(dockerImage));
		environment = (String) cfg.getOrDefault("environment", defaultString(environment));
		dataVolumes = (String) cfg.getOrDefault("dataVolumes", defaultString(dataVolumes));
		labels = (String) cfg.getOrDefault("labels", defaultString(labels));
		secrets = (String) cfg.getOrDefault("secrets", defaultString(secrets));
		removeEnvironment = (String) cfg.getOrDefault("removeEnvironment", defaultString(removeEnvironment));
		removeLabels = (String) cfg.getOrDefault("removeLabels", defaultString(removeLabels));

		rancherLaunchConfig.setDockerImage(dockerImage);
		rancherLaunchConfig.setEnvironment(environment);
		rancherLaunchConfig.setDataVolumes(dataVolumes);
		rancherLaunchConfig.setLabels(labels);
		rancherLaunchConfig.setSecrets(secrets);
		rancherLaunchConfig.removeEnvironment(removeEnvironment);
		rancherLaunchConfig.removeLabels(removeLabels);

		if (cfg.containsKey(START_FIRST)) {
			startFirst = cfg.get(START_FIRST).equals("true");
		} else if (startFirst == null) {
			startFirst = true;
		}

		if (cfg.containsKey("sleepInterval")) {
			sleepInterval = Integer.parseInt(cfg.get("sleepInterval").toString());
		}
		doUpgrade(accessKey, secretKey, upgradeUrl, rancherLaunchConfig.update());

		logger.log(Constants.INFO_LEVEL, "Upgraded " + nodeName);
	}


	/**
	 * Performs the actual upgrade.
	 *
	 * @param accessKey Rancher access key
	 * @param secretKey Rancher secret key
	 * @param upgradeUrl Rancher API url
	 * @param launchConfig The JSON string representing the desired upgrade state.
	 * @throws NodeStepException when upgrade is interrupted.
	 */
	private void doUpgrade(String accessKey, String secretKey, String upgradeUrl, JsonNode launchConfig)
			throws NodeStepException {
		Map<String, Object> inServiceStrategy = ImmutableMap.<String, Object>builder() //
				.put("type", "inServiceUpgradeStrategy") //
				.put("batchSize", 1) //
				.put("intervalMillis", INTERVAL_MILLIS) //
				.put(START_FIRST, startFirst) //
				.put(LAUNCH_CONFIG, launchConfig) //
				.build();
		ObjectMapper mapper = new ObjectMapper();
		String upgrade;
		try {
			upgrade = "{\"type\":\"serviceUpgrade\",\"inServiceStrategy\":" + mapper.writeValueAsString(inServiceStrategy) + "}";
			logger.log(DEBUG_LEVEL, upgrade);
		} catch (JsonProcessingException e) {
			throw new NodeStepException("Failed post to " + upgradeUrl, e, INVALID_CONFIGURATION, nodeName);
		}

		JsonNode service = apiPost(accessKey, secretKey, upgradeUrl, upgrade);
		if (!service.has(NODE_STATE) || !service.path(NODE_ATT_LINKS).has("self")) {
			throw new NodeStepException("API POST returned incomplete data", NO_UPGRADE_DATA, nodeName);
		}
		String state = service.get(NODE_STATE).asText();
		String link = service.get(NODE_ATT_LINKS).get("self").asText();

		// Poll until upgraded.
		logger.log(Constants.INFO_LEVEL, "Upgrading " + service.path("name"));
		while (!state.equals(STATE_UPGRADED)) {
			try {
				Thread.sleep(sleepInterval);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new NodeStepException(e, INTERRUPTED, nodeName);
			}
			service = apiGet(accessKey, secretKey, link);
			state = service.get(NODE_STATE).asText();
			link = service.get(NODE_ATT_LINKS).get("self").asText();
		}

		// Finish the upgrade.
		logger.log(Constants.INFO_LEVEL, "Finishing upgrade " + service.path("name"));
		link = service.get("actions").get("finishupgrade").asText();
		service = apiPost(accessKey, secretKey, link, "");
		state = service.get(NODE_STATE).asText();
		link = service.get(NODE_ATT_LINKS).get("self").asText();
		while (!state.equals(STATE_ACTIVE)) {
			service = apiGet(accessKey, secretKey, link);
			state = service.get(NODE_STATE).asText();
			link = service.get(NODE_ATT_LINKS).get("self").asText();
			if (!state.equals(STATE_ACTIVE)) {
				try {
					Thread.sleep(sleepInterval);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new NodeStepException(e, INTERRUPTED, nodeName);
				}
			}
		}
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
			Builder builder = new Request.Builder().url(url);
			builder.addHeader("Authorization", Credentials.basic(accessKey, secretKey));
			Response response = client.newCall(builder.build()).execute();
			// Since URL comes from the Rancher server itself, assume there are no redirects.
			if (response.code() >= 300) {
				throw new IOException("API get failed" + response.message());
			}
			ObjectMapper mapper = new ObjectMapper();
			if (response.body() == null) {
				return mapper.readTree("");
			}
			return mapper.readTree(response.body().string());
		} catch (IOException e) {
			throw new NodeStepException(e.getMessage(), e, NO_SERVICE_OBJECT, nodeName);
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
			Builder builder = new Request.Builder().url(url).post(postBody);
			builder.addHeader("Authorization", Credentials.basic(accessKey, secretKey));
			Response response = client.newCall(builder.build()).execute();
			// Since URL comes from the Rancher server itself, assume there are no redirects.
			if (response.code() >= 300) {
				throw new IOException("API post failed " + response.message());
			}
			ObjectMapper mapper = new ObjectMapper();
			if (response.body() == null) {
				return mapper.readTree("");
			}
			return mapper.readTree(response.body().string());
		} catch (IOException e) {
			throw new NodeStepException(e.getMessage(), e, UPGRADE_FAILURE, nodeName);
		}
	}
}

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
import com.dtolabs.rundeck.plugins.descriptions.RenderingOptions;
import com.dtolabs.rundeck.plugins.step.NodeStepPlugin;
import com.dtolabs.rundeck.plugins.step.PluginStepContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import okhttp3.*;
import okhttp3.Request.Builder;

import java.io.IOException;
import java.util.Map;

import static com.bioraft.rundeck.rancher.Constants.*;
import static com.bioraft.rundeck.rancher.RancherShared.ErrorCause;
import static com.bioraft.rundeck.rancher.RancherShared.loadStoragePathData;
import static com.dtolabs.rundeck.core.Constants.DEBUG_LEVEL;
import static com.dtolabs.rundeck.core.plugins.configuration.StringRenderingConstants.CODE_SYNTAX_MODE;
import static com.dtolabs.rundeck.core.plugins.configuration.StringRenderingConstants.DISPLAY_TYPE_KEY;

/**
 * Workflow Node Step Plug-in to upgrade a service associated with a node.
 *
 * @author Karl DeBisschop <kdebisschop@gmail.com>
 * @since 2019-12-20
 */
@Plugin(name = RancherShared.RANCHER_SERVICE_PROVIDER, service = ServiceNameConstants.WorkflowNodeStep)
@PluginDescription(title = "Rancher - Upgrade Service/Node", description = "Upgrades the service associated with the selected node.")
public class RancherUpgradeService implements NodeStepPlugin {

	@PluginProperty(title = "Docker Image", description = "The fully specified Docker image to upgrade to.")
	private String dockerImage;

	@PluginProperty(title = "Container OS Environment", description = "JSON object of \"variable\": \"value\"")
	@RenderingOptions({
			@RenderingOption(key = DISPLAY_TYPE_KEY, value = "CODE"),
			@RenderingOption(key = CODE_SYNTAX_MODE, value = JSON),
	})
	private String environment;

	@PluginProperty(title = "Data Volumes", description = "JSON array Lines of \"source:mountPoint\"")
	@RenderingOptions({
			@RenderingOption(key = DISPLAY_TYPE_KEY, value = "CODE"),
			@RenderingOption(key = CODE_SYNTAX_MODE, value = JSON),
	})
	private String dataVolumes;

	@PluginProperty(title = "Service Labels", description = "JSON object of \"variable\": \"value\"")
	@RenderingOptions({
			@RenderingOption(key = DISPLAY_TYPE_KEY, value = "CODE"),
			@RenderingOption(key = CODE_SYNTAX_MODE, value = JSON),
	})
	private String labels;

	@PluginProperty(title = "Remove OS Environment", description = "JSON array of variables (quoted)")
	@RenderingOptions({
			@RenderingOption(key = DISPLAY_TYPE_KEY, value = "CODE"),
			@RenderingOption(key = CODE_SYNTAX_MODE, value = JSON),
	})
	private String removeEnvironment;

	@PluginProperty(title = "Remove Service Labels", description = "JSON array of labels (quoted)")
	@RenderingOptions({
			@RenderingOption(key = DISPLAY_TYPE_KEY, value = "CODE"),
			@RenderingOption(key = CODE_SYNTAX_MODE, value = JSON),
	})
	private String removeLabels;

	@PluginProperty(title = "Secrets", description = "Keys for secrets separated by commas or spaces")
	private String secrets;

	@PluginProperty(title = "Start before stopping", description = "Start new container(s) before stopping old", required = true, defaultValue = "true")
	private Boolean startFirst;

	private String nodeName;

	private PluginLogger logger;

	OkHttpClient client;

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
			accessKey = loadStoragePathData(executionContext, attributes.get(RancherShared.CONFIG_ACCESSKEY_PATH));
			secretKey = loadStoragePathData(executionContext, attributes.get(RancherShared.CONFIG_SECRETKEY_PATH));
		} catch (IOException e) {
			throw new NodeStepException("Could not get secret storage path", e, ErrorCause.IOException, this.nodeName);
		}

		JsonNode service;
		if (attributes.get("type").equals("container")) {
			service = apiGet(accessKey, secretKey, attributes.get("services")).path("data").path(0);
		} else {
			service = apiGet(accessKey, secretKey, attributes.get("self"));
		}
		String serviceState = service.path(STATE).asText();
		if (!serviceState.equals(STATE_ACTIVE)) {
			String message = "Service state must be running, was " + serviceState;
			throw new NodeStepException(message, ErrorCause.ServiceNotRunning, node.getNodename());
		}

		String upgradeUrl = service.path("actions").path("upgrade").asText();
		if (upgradeUrl.length() == 0) {
			throw new NodeStepException("No upgrade URL found", ErrorCause.MissingUpgradeURL, node.getNodename());
		}

		launchConfig = service.path("upgrade").path("inServiceStrategy").path(LAUNCH_CONFIG);
		if (launchConfig.isMissingNode() || launchConfig.isNull()) {
			launchConfig = service.path(LAUNCH_CONFIG);
		}
		if (launchConfig.isMissingNode() || launchConfig.isNull()) {
			throw new NodeStepException("No upgrade data found", ErrorCause.NoUpgradeData, node.getNodename());
		}
		launchConfigObject = (ObjectNode) launchConfig;

		RancherLaunchConfig rancherLaunchConfig = new RancherLaunchConfig(nodeName, launchConfigObject, logger);

		if ((dockerImage == null || dockerImage.length() == 0)  && cfg.containsKey("dockerImage")) {
			dockerImage = (String) cfg.get("dockerImage");
		}
		if (dockerImage != null && dockerImage.length() > 0) {
			rancherLaunchConfig.setDockerImage(dockerImage);
		}

		if ((environment == null || environment.isEmpty()) && cfg.containsKey("environment")) {
			environment = (String) cfg.get("environment");
		}

		if ((dataVolumes == null || dataVolumes.isEmpty()) && cfg.containsKey("dataVolumes")) {
			dataVolumes = (String) cfg.get("dataVolumes");
		}

		if ((labels == null || labels.isEmpty()) && cfg.containsKey("labels")) {
			labels = (String) cfg.get("labels");
		}

		if ((secrets == null || secrets.isEmpty()) && cfg.containsKey("secrets")) {
			secrets = (String) cfg.get("secrets");
		}

		if (cfg.containsKey("removeEnvironment")) {
			removeEnvironment = (String) cfg.get("removeEnvironment");
		}

		if (cfg.containsKey("removeLabels")) {
			removeLabels = (String) cfg.get("removeLabels");
		}

		rancherLaunchConfig.setEnvironment(environment);
		rancherLaunchConfig.setDataVolumes(dataVolumes);
		rancherLaunchConfig.setLabels(labels);
		rancherLaunchConfig.setSecrets(secrets);
		rancherLaunchConfig.removeEnvironment(removeEnvironment);
		rancherLaunchConfig.removeLabels(removeLabels);

		if (cfg.containsKey(START_FIRST)) {
			startFirst = cfg.get(START_FIRST).equals("true");
		} else {
			startFirst = startFirst != null && startFirst;
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
		} catch (IOException e) {
			throw new NodeStepException("Failed post to " + upgradeUrl, e, ErrorCause.InvalidConfiguration, nodeName);
		}

		JsonNode service = apiPost(accessKey, secretKey, upgradeUrl, upgrade);
		String state = service.get(STATE).asText();
		String link = service.get(LINKS).get("self").asText();

		// Poll until upgraded.
		logger.log(Constants.INFO_LEVEL, "Upgrading " + service.path("name"));
		while (!state.equals(STATE_UPGRADED)) {
			try {
				Thread.sleep(sleepInterval);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new NodeStepException(e, ErrorCause.Interrupted, nodeName);
			}
			service = apiGet(accessKey, secretKey, link);
			state = service.get(STATE).asText();
			link = service.get(LINKS).get("self").asText();
		}

		// Finish the upgrade.
		logger.log(Constants.INFO_LEVEL, "Finishing upgrade " + service.path("name"));
		link = service.get("actions").get("finishupgrade").asText();
		service = apiPost(accessKey, secretKey, link, "");
		state = service.get(STATE).asText();
		link = service.get(LINKS).get("self").asText();
		while (!state.equals(STATE_ACTIVE)) {
			service = apiGet(accessKey, secretKey, link);
			state = service.get(STATE).asText();
			link = service.get(LINKS).get("self").asText();
			if (!state.equals(STATE_ACTIVE)) {
				try {
					Thread.sleep(sleepInterval);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new NodeStepException(e, ErrorCause.Interrupted, nodeName);
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
			Builder builder = new Request.Builder().url(url).post(postBody);
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

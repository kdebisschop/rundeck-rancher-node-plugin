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

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.google.common.collect.ImmutableMap;
import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Request.Builder;
import okhttp3.RequestBody;
import okhttp3.Response;

import static com.bioraft.rundeck.rancher.RancherShared.*;
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

	@PluginProperty(title = "Service Labels", description = "JSON object of \"variable\": \"value\"")
	@RenderingOptions({
			@RenderingOption(key = DISPLAY_TYPE_KEY, value = "CODE"),
			@RenderingOption(key = CODE_SYNTAX_MODE, value = "json"),
	})
	private String labels;

	@PluginProperty(title = "Container OS Environment", description = "JSON object of \"variable\": \"value\"")
	@RenderingOptions({
			@RenderingOption(key = DISPLAY_TYPE_KEY, value = "CODE"),
			@RenderingOption(key = CODE_SYNTAX_MODE, value = "json"),
	})
	private String environment;

	@PluginProperty(title = "Secrets", description = "Keys for secrets separated by commas or spaces")
	private String secrets;

	@PluginProperty(title = "Start before stopping", description = "Start new container(s) before stopping old", required = true, defaultValue = "true")
	private Boolean startFirst;

	private String nodeName;

	private PluginLogger logger;

	OkHttpClient client;

	private final static int intervalMillis = 2000;

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

		JsonNode service = apiGet(accessKey, secretKey, attributes.get("services")).path("data").path(0);
		String serviceState = service.path("state").asText();
		if (!serviceState.equals("active")) {
			String message = "Service state must be running, was " + serviceState;
			throw new NodeStepException(message, ErrorCause.ServiceNotRunning, node.getNodename());
		}

		String upgradeUrl = service.path("actions").path("upgrade").asText();
		if (upgradeUrl.length() == 0) {
			throw new NodeStepException("No upgrade URL found", ErrorCause.MissingUpgradeURL, node.getNodename());
		}
		JsonNode launchConfig = service.path("upgrade").path("inServiceStrategy").path("launchConfig");
		if (launchConfig.isMissingNode() || launchConfig.isNull()) {
			launchConfig = service.path("launchConfig");
		}
		if (launchConfig.isMissingNode() || launchConfig.isNull()) {
			throw new NodeStepException("No upgrade data found", ErrorCause.NoUpgradeData, node.getNodename());
		}
		ObjectNode launchConfigObject = (ObjectNode) launchConfig;

		if ((dockerImage == null || dockerImage.length() == 0)  && cfg.containsKey("dockerImage")) {
			dockerImage = (String) cfg.get("dockerImage");
		}
		if (dockerImage != null && dockerImage.length() > 0) {
			logger.log(Constants.INFO_LEVEL, "Setting image to " + dockerImage);
			launchConfigObject.put("imageUuid","docker:" + dockerImage);
		}

		if ((environment == null || environment.isEmpty()) && cfg.containsKey("environment")) {
			environment = (String) cfg.get("environment");
		}

		if ((labels == null || labels.isEmpty()) && cfg.containsKey("labels")) {
			labels = (String) cfg.get("labels");
		}

		if ((secrets == null || secrets.isEmpty()) && cfg.containsKey("secrets")) {
			secrets = (String) cfg.get("secrets");
		}

		if (cfg.containsKey("startFirst")) {
			startFirst = cfg.get("startFirst").equals("true");
		}
		if (startFirst == null) {
			startFirst = true;
		}

		this.setEnvVars(launchConfigObject);
		this.setLabels(launchConfigObject);
		this.addSecrets(launchConfigObject);

		doUpgrade(accessKey, secretKey, upgradeUrl, launchConfigObject);

		logger.log(Constants.INFO_LEVEL, "Upgraded " + nodeName);
	}

	/**
	 * Adds/modifies environment variables.
	 * 
	 * @param launchConfig JsonNode representing the target upgraded configuration.
	 */
	private void setEnvVars(ObjectNode launchConfig) throws NodeStepException {
		if (environment != null && environment.length() > 0) {
			ObjectNode envObject = (ObjectNode) launchConfig.get("environment");
			ObjectMapper objectMapper = new ObjectMapper();
			try {
				JsonNode map = objectMapper.readTree(ensureStringIsJsonObject(environment));
				Iterator<Map.Entry<String, JsonNode>> iterator = map.fields();
				while (iterator.hasNext()) {
					Map.Entry<String, JsonNode> entry = iterator.next();
					String key = entry.getKey();
					String value = entry.getValue().asText();
					envObject.put(key, value);
					logger.log(Constants.INFO_LEVEL, "Setting environment variable " + key + " to " + value);
				}
			} catch (JsonProcessingException e) {
				throw new NodeStepException("Invalid Labels JSON", ErrorCause.InvalidJson, this.nodeName);
			}
		}
	}

	/**
	 * Adds/modifies labels based on the step's labels setting.
	 * 
	 * @param launchConfig JsonNode representing the target upgraded configuration.
	 */
	private void setLabels(ObjectNode launchConfig) throws NodeStepException {
		if (labels != null && labels.length() > 0) {
			ObjectNode labelObject = (ObjectNode) launchConfig.get("labels");
			ObjectMapper objectMapper = new ObjectMapper();
			try {
				JsonNode map = objectMapper.readTree(ensureStringIsJsonObject(labels));
				Iterator<Map.Entry<String, JsonNode>> iterator = map.fields();
				while (iterator.hasNext()) {
					Map.Entry<String, JsonNode> entry = iterator.next();
					String key = entry.getKey();
					String value = entry.getValue().asText();
					labelObject.put(key, value);
					logger.log(Constants.INFO_LEVEL, "Setting environment variable " + key + " to " + value);
				}
			} catch (JsonProcessingException e) {
				throw new NodeStepException("Invalid Labels JSON", ErrorCause.InvalidJson, this.nodeName);
			}
		}
	}

	/**
	 * Add or replace secrets.
	 * 
	 * @param launchConfig JsonNode representing the target upgraded configuration.
	 * @throws NodeStepException when secret JSON is malformed (passed up from {@see this.buildSecret()}.
	 */
	private void addSecrets(ObjectNode launchConfig) throws NodeStepException {
		if (secrets != null && secrets.length() > 0) {
			// Copy existing secrets, skipping any that we want to add or overwrite.
			Iterator<JsonNode> elements = null;
			boolean hasOldSecrets = false;
			if (launchConfig.has("secrets")) {
				hasOldSecrets = true;
				elements = launchConfig.get("secrets").elements();
			}

			ArrayNode secretsArray = launchConfig.putArray("secrets");

			// Copy existing secrets, skipping any that we want to add or overwrite.
			if (hasOldSecrets && elements != null) {
				while (elements.hasNext()) {
					JsonNode secretObject = elements.next();
					// @todo this only works for a single secret added.
					if (!secretObject.get("secretId").asText().equals(secrets)) {
						secretsArray.add(secretObject);
					}
				}
			}

			// Add in the new or replacement secrets specified in the step.
			for (String secretId : secrets.split("/[,; ]+/")) {
				secretsArray.add(buildSecret(secretId, this.nodeName));
				logger.log(Constants.INFO_LEVEL, "Adding secret map to " + secretId);
			}
		}
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
				.put("intervalMillis", intervalMillis) //
				.put("startFirst", startFirst) //
				.put("launchConfig", launchConfig) //
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
		String state = service.get("state").asText();
		String link = service.get("links").get("self").asText();

		// Poll until upgraded.
		logger.log(Constants.INFO_LEVEL, "Upgrading " + service.path("name"));
		while (!state.equals("upgraded")) {
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				throw new NodeStepException(e, ErrorCause.Interrupted, nodeName);
			}
			service = apiGet(accessKey, secretKey, link);
			state = service.get("state").asText();
			link = service.get("links").get("self").asText();
		}

		// Finish the upgrade.
		logger.log(Constants.INFO_LEVEL, "Finishing upgrade " + service.path("name"));
		link = service.get("actions").get("finishupgrade").asText();
		service = apiPost(accessKey, secretKey, link, "");
		state = service.get("state").asText();
		link = service.get("links").get("self").asText();
		while (!state.equals("active")) {
			service = apiGet(accessKey, secretKey, link);
			state = service.get("state").asText();
			link = service.get("links").get("self").asText();
			if (!state.equals("active")) {
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
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

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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import com.dtolabs.rundeck.core.Constants;
import com.dtolabs.rundeck.core.common.INodeEntry;
import com.dtolabs.rundeck.core.execution.ExecutionContext;
import com.dtolabs.rundeck.core.execution.workflow.steps.FailureReason;
import com.dtolabs.rundeck.core.execution.workflow.steps.node.NodeStepException;
import com.dtolabs.rundeck.core.plugins.Plugin;
import com.dtolabs.rundeck.plugins.PluginLogger;
import com.dtolabs.rundeck.plugins.ServiceNameConstants;
import com.dtolabs.rundeck.plugins.descriptions.PluginDescription;
import com.dtolabs.rundeck.plugins.descriptions.PluginProperty;
import com.dtolabs.rundeck.plugins.step.NodeStepPlugin;
import com.dtolabs.rundeck.plugins.step.PluginStepContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Workflow Node Step Plug-in to upgrade a service associated with a node.
 *
 * @author Karl DeBisschop <kdebisschop@gmail.com>
 * @since 2019-12-20
 */
@Plugin(name = RancherShared.SERVICE_PROVIDER_NAME, service = ServiceNameConstants.WorkflowNodeStep)
@PluginDescription(title = "Rancher Service Upgrade Node Plugin", description = "Upgrades the service associated with the selected node.")
public class RancherUpgradeService implements NodeStepPlugin {

	@PluginProperty(title = "Docker Image", description = "The fully specified Docker image to upgrade to.", required = false)
	private String dockerImage;

	@PluginProperty(title = "Labels", description = "Pairs of 'label:value' separated by semicolons", required = true)
	private String labels;

	@PluginProperty(title = "Secrets", description = "Keys for secrets separated by commas or spaces", required = false)
	private String secrets;

	@PluginProperty(title = "Start before stopping", description = "Start new container(s) before stopping old", required = true, defaultValue = "true")
	private boolean startFirst;

	private String nodeName;

	private ExecutionContext executionContext;
	
	private PluginLogger logger;

	@Override
	public void executeNodeStep(PluginStepContext ctx, Map<String, Object> cfg, INodeEntry node)
			throws NodeStepException {
		JsonNode service;

		this.nodeName = node.getNodename();
		this.executionContext = ctx.getExecutionContext();
		this.logger = ctx.getLogger();

		OkHttpClient client = new OkHttpClient();
		Map<String, String> attributes = node.getAttributes();

		String accessKey = this.loadStoragePathData(attributes.get(RancherShared.CONFIG_ACCESSKEY_PATH));
		String secretKey = this.loadStoragePathData(attributes.get(RancherShared.CONFIG_SECRETKEY_PATH));

		service = apiGet(client, accessKey, secretKey, attributes.get("services"));
		String upgradeUrl = service.get("data").get(0).get("actions").get("upgrade").asText();
		JsonNode upgrade = service.get("data").get(0).get("upgrade");

		if (dockerImage != null && dockerImage.length() > 0) {
			logger.log(Constants.INFO_LEVEL, "Setting image to " + dockerImage);
			((ObjectNode) upgrade.get("inServiceStrategy").get("launchConfig")).put("imageUuid", "docker:" + dockerImage);
		}
		((ObjectNode) upgrade.get("inServiceStrategy")).put("startFirst", startFirst);
		this.setLabels(upgrade);
		this.addSecrets(upgrade);

		service = doUpgrade(client, accessKey, secretKey, upgradeUrl, upgrade.toString());

		logger.log(Constants.INFO_LEVEL, "Upgraded " + nodeName);

		return;
	}

	/**
	 * Adds/modifies labels based on the step's labels setting.
	 * 
	 * @param upgrade JsonNode representing the target upgraded configuration.
	 */
	private void setLabels(JsonNode upgrade) {
		ObjectNode labelObject = (ObjectNode) upgrade.get("inServiceStrategy").get("launchConfig").get("labels");
		for (String keyValue : labels.split(";")) {
			String[] values = keyValue.split(":");
			labelObject.put(values[0], values[1]);
			logger.log(Constants.INFO_LEVEL, "Setting label " + values[0] + " to " + values[1]);
		}
	}

	/**
	 * Add or replace secrets.
	 * 
	 * @param upgrade JsonNode representing the target upgraded configuration.
	 * @throws NodeStepException
	 */
	private void addSecrets(JsonNode upgrade) throws NodeStepException {
		if (secrets != null && secrets.length() > 0) {
			JsonNode launchConfig = upgrade.get("inServiceStrategy").get("launchConfig");
			ObjectNode launchObject = (ObjectNode) launchConfig;
			ArrayNode secretsArray = launchObject.putArray("secrets");

			// Copy existing secrets, skipping any that we want to add or overwrite.
			if (launchConfig.has("secrets")) {
				Iterator<JsonNode> elements = launchConfig.get("secrets").elements();
				while (elements.hasNext()) {
					JsonNode secretObject = elements.next();
					if (!secretObject.get("secretId").asText().equals(secrets)) {
						secretsArray.add(secretObject);
					}
				}
			}

			// Add in the new or replacement secrets specified in the step.
			for (String secretId : secrets.split("/[,; ]+/")) {
				secretsArray.add(this.buildSecret(secretId));
				logger.log(Constants.INFO_LEVEL, "Adding secret map to " + secretId);
			}
		}
	}

	/**
	 * Builds a JsonNode object for insertion into the secrets array.
	 * 
	 * @param secretId
	 * @return
	 * @throws NodeStepException
	 */
	private JsonNode buildSecret(String secretId) throws NodeStepException {
		String json = "{ \"type\": \"secretReference\", \"gid\": \"0\", \"mode\": \"444\", \"name\": \"\", \"secretId\": \""
				+ secretId + "\", \"uid\": \"0\"}";
		try {
			return (new ObjectMapper()).readTree(json);
		} catch (JsonProcessingException e) {
			throw new NodeStepException("Failed add secret", e, UpgradeFailureReason.InvalidJson, this.nodeName);
		}
	}

	/**
	 * Performs the actual upgrade.
	 * 
	 * @param client
	 * @param accessKey
	 * @param secretKey
	 * @param upgradeUrl
	 * @param upgrade
	 * @return
	 * @throws NodeStepException
	 */
	private JsonNode doUpgrade(OkHttpClient client, String accessKey, String secretKey, String upgradeUrl,
			String upgrade) throws NodeStepException {
		JsonNode service = apiPost(client, accessKey, secretKey, upgradeUrl, upgrade.toString());
		String state = "unknown";
		String link = service.get("links").get("self").asText();

		// Poll until upgraded.
		logger.log(Constants.INFO_LEVEL, "Upgrading " + service.path("name"));
		while (!state.equals("upgraded")) {
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				throw new NodeStepException(e, UpgradeFailureReason.Interrupted, nodeName);
			}
			service = apiGet(client, accessKey, secretKey, link);
			state = service.get("state").asText();
			link = service.get("links").get("self").asText();
		}

		// Finish the upgrade.
		logger.log(Constants.INFO_LEVEL, "Finishing upgrade " + service.path("name"));
		link = service.get("actions").get("finishupgrade").asText();
		service = apiPost(client, accessKey, secretKey, link, "");
		state = service.get("state").asText();
		link = service.get("links").get("self").asText();
		while (!state.equals("active")) {
			service = apiGet(client, accessKey, secretKey, link);
			state = service.get("state").asText();
			link = service.get("links").get("self").asText();
			if (!state.equals("active")) {
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					throw new NodeStepException(e, UpgradeFailureReason.Interrupted, nodeName);
				}
			}
		}
		return service;
	}

	/**
	 * Gets the web socket end point and connection token for an execute request.
	 *
	 * @param client
	 * @param accessKey
	 * @param secretKey
	 * @param url
	 * @return
	 * @throws NodeStepException
	 */
	private JsonNode apiGet(OkHttpClient client, String accessKey, String secretKey, String url)
			throws NodeStepException {
		try {
			Request request = new Request.Builder().url(url)
					.addHeader("Authorization", Credentials.basic(accessKey, secretKey)).build();
			Response response = client.newCall(request).execute();
			if (response.code() != 200) {
				throw new IOException("API get failed" + response.message());
			}
			ObjectMapper mapper = new ObjectMapper();
			return mapper.readTree(response.body().string());
		} catch (IOException e) {
			throw new NodeStepException(e.getMessage(), e, UpgradeFailureReason.NoServiceObject, nodeName);
		}
	}

	/**
	 * Gets the web socket end point and connection token for an execute request.
	 *
	 * @param client
	 * @param accessKey
	 * @param secretKey
	 * @param url
	 * @param body
	 * @return
	 * @throws NodeStepException
	 */
	private JsonNode apiPost(OkHttpClient client, String accessKey, String secretKey, String url, String body)
			throws NodeStepException {
		RequestBody postBody = RequestBody.create(MediaType.parse("application/json"), body);
		try {
			Request request = new Request.Builder().url(url).post(postBody)
					.addHeader("Authorization", Credentials.basic(accessKey, secretKey)).build();
			Response response = client.newCall(request).execute();
			ObjectMapper mapper = new ObjectMapper();
			return mapper.readTree(response.body().string());
		} catch (IOException e) {
			throw new NodeStepException(e.getMessage(), e, UpgradeFailureReason.UpgradeFailure, nodeName);
		}
	}

	/**
	 * Get a (secret) value from password storage.
	 *
	 * @param path
	 * @return
	 * @throws NodeStepException
	 * @throws IOException
	 */
	private String loadStoragePathData(final String path) throws NodeStepException {
		if (null == path) {
			return null;
		}
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		try {
			executionContext.getStorageTree().getResource(path).getContents().writeContent(stream);
		} catch (Exception e) {
			throw new NodeStepException(e, UpgradeFailureReason.NoKeyStorage, nodeName);
		}
		return new String(stream.toByteArray());
	}

	private enum UpgradeFailureReason implements FailureReason {
		/**
		 * Could not access key storage
		 */
		NoKeyStorage,
		/**
		 * Could not get service object
		 */
		NoServiceObject,
		/**
		 * Upgrade failed
		 */
		UpgradeFailure,
		/**
		 * Interrupted
		 */
		Interrupted, Failed, InvalidJson
	}

}
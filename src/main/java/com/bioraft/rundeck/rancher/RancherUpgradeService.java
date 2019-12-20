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
import com.dtolabs.rundeck.core.storage.ResourceMeta;
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
 * Workflow Node Step Plug-in to choose one of two values to uplift into a step
 * variable.
 *
 * @author Karl DeBisschop <kdebisschop@gmail.com>
 * @since 2019-12-11
 */
@Plugin(name = RancherShared.SERVICE_PROVIDER_NAME, service = ServiceNameConstants.WorkflowNodeStep)
@PluginDescription(title = "Rancher Service Upgrade Node Plugin", description = "Upgrades the service associated with the selected nodes.")
public class RancherUpgradeService implements NodeStepPlugin {

	@PluginProperty(name = "dockerImage", title = "Docker Image", description = "The fully specified Docker image to upgrade to.", required = true)
	private String group;

	@PluginProperty(name = "labels", title = "Labels", description = "Pairs of 'label:value' separated by semicolons", required = true)
	private String labels;

	@PluginProperty(name = "secrets", title = "Secrets", description = "Keys for secrets separated by semicolons", required = false)
	private String secrets;

	@Override
	public void executeNodeStep(PluginStepContext context, Map<String, Object> configuration, INodeEntry node)
			throws NodeStepException {
		String accessKey;
		String secretKey;
		JsonNode service;
		// avoid creating several instances, should be singleton
		OkHttpClient client = new OkHttpClient();

		Map<String, String> attributes = node.getAttributes();
		String servicesLink = attributes.get("services");
		ExecutionContext executionContext = context.getExecutionContext();
		try {
			accessKey = this.loadStoragePathData(executionContext, attributes.get(RancherShared.CONFIG_ACCESSKEY_PATH));
			secretKey = this.loadStoragePathData(executionContext, attributes.get(RancherShared.CONFIG_SECRETKEY_PATH));
		} catch (IOException e) {
			throw new NodeStepException(e, UpgradeFailureReason.NoKeyStorage, node.getNodename());
		}

		for (String key : executionContext.getDataContext().get("option").keySet()) {
			System.out.println(key);
		}
		try {
			service = apiGet(client, accessKey, secretKey, servicesLink);
		} catch (IOException e) {
			throw new NodeStepException("IOException", UpgradeFailureReason.NoServiceObject, node.getNodename());
		} catch (Exception e) {
			throw new NodeStepException("Status Exception", UpgradeFailureReason.NoServiceObject, node.getNodename());
		}

		String upgradeUrl = service.get("data").get(0).get("actions").get("upgrade").asText();
		JsonNode upgrade = service.get("data").get(0).get("upgrade");
		
		ObjectNode labelObject = (ObjectNode) upgrade.get("inServiceStrategy").get("launchConfig").get("labels");
		for (String keyValue : labels.split(";")) {
			String[] values = keyValue.split(":");
			labelObject.put(values[0], values[1]);
		}
		
		((ObjectNode) upgrade.get("inServiceStrategy")).put("startFirst", true);

		// Add new secrets if so configured.
		if (secrets != null && secrets.length() > 0) {
			ObjectNode launchObject = (ObjectNode) upgrade.get("inServiceStrategy").get("launchConfig");
			ArrayNode secretsArray = launchObject.putArray("secrets");
			if (upgrade.get("inServiceStrategy").get("launchConfig").has("secrets")) {
				
				Iterator<JsonNode> elements = upgrade.get("inServiceStrategy").get("launchConfig").get("secrets")
						.elements();
				while (elements.hasNext()) {
					JsonNode secretObject = elements.next();
					System.out.println(secretObject.toString());
					if (!secretObject.get("secretId").asText().equals(secrets)) {
						secretsArray.add(secretObject);
					}
				}
			}
			try {
				secretsArray.add(this.buildSecret(secrets));
			} catch (JsonProcessingException e) {
				throw new NodeStepException("Failed mapping new secret", e, UpgradeFailureReason.InvalidJson,
						node.getNodename());
			}
		}

		try {
			service = doUpgrade(client, accessKey, secretKey, upgradeUrl, upgrade.toString());
		} catch (InterruptedException e) {
			throw new NodeStepException(e, UpgradeFailureReason.Interrupted, node.getNodename());
		} catch (IOException e) {
			throw new NodeStepException(e, UpgradeFailureReason.UpgradeFailure, node.getNodename());
		}

		PluginLogger logger = context.getLogger();
		logger.log(Constants.INFO_LEVEL, service.toString());

		return;
	}

	private JsonNode buildSecret(String secretId) throws JsonProcessingException {
		String json = "{ \"type\": \"secretReference\", \"gid\": \"0\", \"mode\": \"444\", \"name\": \"\", \"secretId\": \""
				+ secretId + "\", \"uid\": \"0\"}";
		return (new ObjectMapper()).readTree(json);
	}

	private JsonNode doUpgrade(OkHttpClient client, String accessKey, String secretKey, String upgradeUrl, String upgrade) throws IOException, InterruptedException {
		JsonNode service = apiPost(client, accessKey, secretKey, upgradeUrl, upgrade.toString());
		String state = "unknown";
		String link = service.get("links").get("self").asText();

		// Poll until upgraded.
		while (!state.equals("upgraded")) {
			Thread.sleep(5000);
			service = apiGet(client, accessKey, secretKey, link);
			state = service.get("state").asText();
			link = service.get("links").get("self").asText();
			System.out.println(state);
		}

		// Finish the upgrade.
		link = service.get("actions").get("finishupgrade").asText();
		service = apiPost(client, accessKey, secretKey, link, "");
		state = service.get("state").asText();
		System.out.println(state);
		link = service.get("links").get("self").asText();
		while (!state.equals("active")) {
			service = apiGet(client, accessKey, secretKey, link);
			state = service.get("state").asText();
			link = service.get("links").get("self").asText();
			System.out.println(state);
			if (!state.equals("active")) {
				Thread.sleep(5000);
			}
		}
		return service;
	}
	
	/**
	 * Gets the web socket end point and connection token for an execute request.
	 *
	 * @return
	 * @throws Exception
	 */
	private JsonNode apiGet(OkHttpClient client, String accessKey, String secretKey, String url) throws IOException {
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
			System.out.println(e.getMessage());
			throw e;
		}
	}

	/**
	 * Gets the web socket end point and connection token for an execute request.
	 *
	 * @return
	 * @throws IOException
	 */
	private JsonNode apiPost(OkHttpClient client, String accessKey, String secretKey, String url, String body)
			throws IOException {
		RequestBody postBody = RequestBody.create(MediaType.parse("application/json"), body);
		try {
			Request request = new Request.Builder().url(url).post(postBody)
					.addHeader("Authorization", Credentials.basic(accessKey, secretKey)).build();
			Response response = client.newCall(request).execute();
			ObjectMapper mapper = new ObjectMapper();
			return mapper.readTree(response.body().string());
		} catch (IOException e) {
			System.out.println(e.getMessage());
			throw e;
		}
	}

	/**
	 * Get a (secret) value from password storage.
	 *
	 * @param context
	 * @param passwordStoragePath
	 * @return
	 * @throws IOException
	 */
	private String loadStoragePathData(final ExecutionContext context, final String passwordStoragePath)
			throws IOException {
		if (null == passwordStoragePath) {
			return null;
		}
		ResourceMeta contents = context.getStorageTree().getResource(passwordStoragePath).getContents();
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		contents.writeContent(byteArrayOutputStream);
		return new String(byteArrayOutputStream.toByteArray());
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
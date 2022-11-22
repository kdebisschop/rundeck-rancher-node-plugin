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
import com.dtolabs.rundeck.core.execution.workflow.steps.node.NodeStepException;
import com.dtolabs.rundeck.plugins.PluginLogger;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static com.bioraft.rundeck.rancher.Constants.*;
import static com.bioraft.rundeck.rancher.Errors.ErrorCause.*;

/**
 * Workflow Node Step Plug-in to upgrade a service associated with a node.
 *
 * @author Karl DeBisschop <kdebisschop@gmail.com>
 * @since 2019-12-20
 */
public class RancherLaunchConfig {

	private String environment = "";

	private String dataVolumes = "";

	private String labels = "";

	private String removeEnvironment = "";

	private String removeLabels = "";

	private String secrets = "";

	private Map<String, String> secretMap;

	private Map<String, String> removeSecretMap;

	private final String nodeName;

	private final PluginLogger logger;

	final ObjectNode launchConfigObject;

	public RancherLaunchConfig(String nodeName, ObjectNode launchConfigObject, PluginLogger logger) {
		this.nodeName = nodeName;
		this.launchConfigObject = launchConfigObject;
		this.logger = logger;
	}

	public ObjectNode update() throws NodeStepException {
		setField("environment", environment);
		removeField("environment", removeEnvironment);

		setField("labels", labels);
		removeField("labels", removeLabels);

		addSecrets(launchConfigObject);

		setMountArray(dataVolumes);

		return launchConfigObject;
	}

	public void setDockerImage(String dockerImage) {
		if (dockerImage != null && !dockerImage.isEmpty()) {
			logger.log(Constants.INFO_LEVEL, "Setting image to " + dockerImage);
			launchConfigObject.put("imageUuid", "docker:" + dockerImage);
		}
	}

	public void setEnvironment(String environment) {
		this.environment = environment;
	}

	public void setDataVolumes(String dataVolumes) {
		this.dataVolumes = dataVolumes;
	}

	public void setLabels(String labels) {
		this.labels = labels;
	}

	public void removeEnvironment(String removeEnvironment) {
		this.removeEnvironment = removeEnvironment;
	}

	public void removeLabels(String removeLabels) {
		this.removeLabels = removeLabels;
	}

	public void setSecrets(String secrets, String remove) {
		this.secrets = secrets;
		secretMap = new HashMap<>();
		removeSecretMap = new HashMap<>();
		if (secrets != null && secrets.trim().length() > 0) {
			// Add in the new or replacement secrets specified in the step.
			for (String secretId : secrets.split(PERMISSIVE_WHITESPACE_REGEX)) {
				secretMap.put(secretId, secretId);
			}
		}
		if (remove != null && remove.trim().length() > 0) {
			// Add in the new or replacement secrets specified in the step.
			for (String secretId : remove.split(PERMISSIVE_WHITESPACE_REGEX)) {
				removeSecretMap.put(secretId, secretId);
			}
		}
	}

	/**
	 * Adds/modifies values in a launchConfig field.
	 *
	 * @param field The field to update.
	 * @param newData JSON Object representing the new name-value pairs.
	 */
	public void setField(String field, String newData) throws NodeStepException {
		if (newData == null || newData.length() == 0) {
			return;
		}
		ObjectNode objectNode;
		JsonNode jsonNode = launchConfigObject.path(field);
		boolean originalNodeIsEmpty = jsonNode.isMissingNode() || jsonNode.isNull();
		ObjectMapper objectMapper = new ObjectMapper();
		try {
			if (originalNodeIsEmpty) {
				objectNode = (ObjectNode) objectMapper.readTree("{}");
			} else {
				objectNode = (ObjectNode) jsonNode;
			}
			JsonNode map = objectMapper.readTree((new Strings()).ensureStringIsJsonObject(newData));
			Iterator<Map.Entry<String, JsonNode>> iterator = map.fields();
			while (iterator.hasNext()) {
				Map.Entry<String, JsonNode> entry = iterator.next();
				String key = entry.getKey();
				String value = entry.getValue().asText();
				objectNode.put(key, value);
				logger.log(Constants.INFO_LEVEL, "Setting " + field + ":" + key + " to " + value);
			}
		} catch (JsonProcessingException e) {
			throw new NodeStepException("Invalid " + field + " JSON data", INVALID_JSON, this.nodeName);
		}
		if (originalNodeIsEmpty) {
			launchConfigObject.replace(field, objectNode);
		}
	}

	/**
	 * Adds/modifies environment variables.
	 *
	 * @param field Name of the object to remove from.
	 * @param remove String representation of fields to be removed (JSON array).
	 */
	public void removeField(String field, String remove) throws NodeStepException {
		if (remove == null || remove.length() == 0) {
			return;
		}
		if (!launchConfigObject.has(field) || launchConfigObject.get(field).isNull()) {
			return;
		}

		ObjectNode objectNode = (ObjectNode) launchConfigObject.get(field);
		ObjectMapper objectMapper = new ObjectMapper();
		try {
			JsonNode map = objectMapper.readTree((new Strings()).ensureStringIsJsonArray(remove));
			Iterator<JsonNode> iterator = map.elements();
			while (iterator.hasNext()) {
				String entry = iterator.next().asText();
				logger.log(Constants.INFO_LEVEL, "Removing " + entry + " from " + field);
				objectNode.remove(entry);
			}
		} catch (JsonProcessingException e) {
			throw new NodeStepException("Invalid " + field + " array", INVALID_JSON, this.nodeName);
		}
	}

	/**
	 * Add or replace secrets.
	 *
	 * @param launchConfig JsonNode representing the target upgraded configuration.
	 */
	private void addSecrets(ObjectNode launchConfig) {
		if (secrets != null && secrets.length() > 0) {
			// Copy existing secrets, skipping any that we want to add or overwrite.
			Iterator<JsonNode> elements = null;
			boolean hasOldSecrets = false;
			if (launchConfig.has(OPT_SECRETS) && !launchConfig.get(OPT_SECRETS).isNull()) {
				elements = launchConfig.get(OPT_SECRETS).elements();
				hasOldSecrets = elements.hasNext();
			}

			ArrayNode secretsArray = launchConfig.putArray(OPT_SECRETS);

			// Copy existing secrets, skipping any that we want to add or overwrite.
			if (hasOldSecrets) {
				copyOldSecrets(elements, secretsArray);
			}

			// Add in the new or replacement secrets specified in the step.
			for (String secretId : secrets.split(PERMISSIVE_WHITESPACE_REGEX)) {
				if (!removeSecretMap.containsKey(secretId)) {
					secretsArray.add((new Strings()).buildSecret(secretId));
					logger.log(Constants.INFO_LEVEL, "Adding secret map to " + secretId);
				}
			}
		}
	}

	private void copyOldSecrets(Iterator<JsonNode> elements, ArrayNode secretsArray) {
		while (elements.hasNext()) {
			JsonNode secretObject = elements.next();
			String key = secretObject.path("secretId").asText();
			if (!secretMap.containsKey(key) && !removeSecretMap.containsKey(key)) {
				secretsArray.add(secretObject);
			}
		}
	}

	/**
	 * Add or replace secrets.
	 *
	 * @throws NodeStepException when secret JSON is malformed (passed up from {@see this.buildSecret()}).
	 */
	private void setMountArray(String newData) throws NodeStepException {
		if (newData != null && newData.length() > 0) {
			HashMap<String, String> hashMap = new HashMap<>();

			// Copy existing mounts into hash keyed by mount point.
			if (launchConfigObject.has(OPT_DATA_VOLUMES) && !launchConfigObject.get(OPT_DATA_VOLUMES).isNull()) {
				Iterator<JsonNode> elements = launchConfigObject.get(OPT_DATA_VOLUMES).elements();
				while (elements.hasNext()) {
					String element = elements.next().asText();
					hashMap.put((new Strings()).mountPoint(element), element);
				}
			}

			// Copy new mounts into hash, possibly overwriting some vales.
			ObjectMapper objectMapper = new ObjectMapper();
			try {
				JsonNode map = objectMapper.readTree((new Strings()).ensureStringIsJsonArray(newData));
				Iterator<JsonNode> mounts = map.elements();
				mounts.forEachRemaining(spec -> hashMap.put((new Strings()).mountPoint(spec.asText()), spec.asText()));

			} catch (JsonProcessingException e) {
				throw new NodeStepException("Could not parse JSON for " + OPT_DATA_VOLUMES + "\n" + newData, e, INVALID_CONFIGURATION, nodeName);
			}

			ArrayNode updatedArray = launchConfigObject.putArray(OPT_DATA_VOLUMES);

			// Copy the merged array.
			hashMap.forEach((k, v) -> updatedArray.add(v));
		}
	}
}

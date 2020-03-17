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

import com.dtolabs.rundeck.core.common.FrameworkBase;
import com.dtolabs.rundeck.core.common.INodeSet;
import com.dtolabs.rundeck.core.common.NodeEntryImpl;
import com.dtolabs.rundeck.core.common.NodeSetImpl;
import com.dtolabs.rundeck.core.plugins.configuration.ConfigurationException;
import com.dtolabs.rundeck.core.resources.ResourceModelSource;
import com.dtolabs.rundeck.core.resources.ResourceModelSourceException;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.*;

import static com.bioraft.rundeck.rancher.Constants.*;
import static org.apache.commons.lang.StringUtils.defaultString;

/**
 * RancherResourceModelSource collects nodes from one or more Rancher
 * environments.
 *
 * @author Karl DeBisschop <kdebisschop@gmail.com>
 * @since 2014-09-03
 */
public class RancherResourceModelSource implements ResourceModelSource {

	private Properties configuration;

	// URL to Rancher API.
	private String url;

	// HTTP client is shared among methods.
	private HttpClient client;

	// Tags that will be applied to all nodes (comma-separated).
	private String tags;

	// Regular expression for labels to include as node attributes.
	private String attributeInclude;

	// Regular expression for labels to include as tags.
	private String tagInclude;

	// Regular expression for stacks to include in result set.
	private String stackInclude;

	// The set of nodes that will be returned by getNodes().
	private NodeSetImpl iNodeEntries;

	// Track how many times each stack_service has been seen.
	Map<String, Integer> seen;

	// Map stack IDs to names once to reduce API calls.
	HashMap<String, String> stackNames = new HashMap<>();

	/**
	 * The required object constructor.
	 *
	 * @param configuration Configuration variables set in RancherResourceModelSourceFactory
	 */
	public RancherResourceModelSource(Properties configuration) throws ConfigurationException {
		this.init(configuration, new HttpClient());
	}

	/**
	 * Constructor for unit testing.
	 *
	 * @param configuration Configuration variables set in RancherResourceModelSourceFactory
	 * @param client HTTP client used for unit testing.
	 */
	public RancherResourceModelSource(Properties configuration, HttpClient client) throws ConfigurationException {
		this.init(configuration, client);
	}

	private void init(Properties configuration, HttpClient client) throws ConfigurationException {
		this.configuration = configuration;
		String accessKey = configuration.getProperty(CONFIG_ACCESSKEY);
		String secretKey = configuration.getProperty(CONFIG_SECRETKEY);

		this.client = client;
		client.setAccessKey(accessKey);
		client.setSecretKey(secretKey);

		tags = configuration.getProperty("tags");
		url = configuration.getProperty(RANCHER_CONFIG_ENDPOINT);
		if (defaultString(url).isEmpty()) {
			throw new ConfigurationException("Endpoint URL cannot be empty");
		}
		attributeInclude = configuration.getProperty(CONFIG_LABELS_INCLUDE_ATTRIBUTES, "");
		tagInclude = configuration.getProperty(CONFIG_LABELS_INCLUDE_TAGS, "");
		stackInclude = configuration.getProperty(CONFIG_STACK_FILTER, "");
	}

	@SuppressWarnings("RedundantThrows")
	@Override
	public INodeSet getNodes() throws ResourceModelSourceException {
		iNodeEntries = new NodeSetImpl();
		String environmentIds = configuration.getProperty(CONFIG_ENVIRONMENT_IDS);
		for (String environmentId : environmentIds.split("[ ,]+")) {
			seen = new HashMap<>();
			getNodesForEnvironment(environmentId);
		}
		return iNodeEntries;
	}

	/**
	 * Adds nodes for specified environment to the NodeSetImpl.
	 *
	 * @param environmentId Rancher account ID for the desired environment.
	 */
	private void getNodesForEnvironment(String environmentId) {
		ArrayList<JsonNode> data;
		String environmentName;
		Logger logger = FrameworkBase.logger;
		try {
			environmentName = this.getEnvironmentName(environmentId);
		} catch (IOException e) {
			environmentName = environmentId;
			logger.log(Level.WARN, "Failed getting environment name");
			logger.log(Level.WARN, e.getMessage());
		}

		if (configuration.getProperty(CONFIG_NODE_TYPE_INCLUDE_CONTAINER, "true").equals("true")) {
			try {
				data = this.getContainers(environmentId);
			} catch (IOException e) {
				logger.log(Level.WARN, e.getMessage());
				return;
			}
			for (JsonNode node : data) {
				addContainerNode(node, environmentName, logger);
			}
		}

		if (configuration.getProperty(CONFIG_NODE_TYPE_INCLUDE_SERVICE, "false").equals("true")) {
			try {
				stackNames = new HashMap<>();
				for (JsonNode node : this.getStacks(environmentId)) {
					stackNames.put(node.get(NODE_ID).asText(), node.get(NODE_NAME).asText());
				}
				for (JsonNode node : this.getServices(environmentId)) {
					addServiceNode(node, environmentName, logger);
				}
			} catch (IOException e) {
				logger.log(Level.WARN, e.getMessage());
			}
		}
	}

	private void addContainerNode(JsonNode node, String environmentName, Logger logger) {
		if (!node.get(NODE_STATE).asText().equals("running")) {
			return;
		}

		int count = 0;
		if (node.hasNonNull(OPT_LABELS)) {
			count = countProcessableByLabel(node.get(OPT_LABELS));
			if (count == 0) {
				return;
			}
		}

		RancherContainerNode rancherNode = new RancherContainerNode();
		try {
			NodeEntryImpl nodeEntry = rancherNode.getNodeEntry(environmentName, node);
			if (count != 0) {
				nodeEntry.setAttribute("seen", Integer.toString(count));
			}
			iNodeEntries.putNode(nodeEntry);
		} catch (IllegalArgumentException | NullPointerException e) {
			logger.log(Level.WARN, e.getMessage());
		}
	}

	private void addServiceNode(JsonNode node, String environmentName, Logger logger) {
		RancherServiceNode rancherNode = new RancherServiceNode();
		try {
			NodeEntryImpl nodeEntry = rancherNode.getNodeEntry(environmentName, node);
			iNodeEntries.putNode(nodeEntry);
		} catch (IllegalArgumentException | NullPointerException e) {
			logger.log(Level.WARN, e.getMessage());
		}
	}

	private Integer countProcessableByLabel(JsonNode labels) {
		if (skipThisLabel(CONFIG_HANDLE_SYSTEM, labels) || skipThisLabel(CONFIG_HANDLE_GLOBAL, labels)) {
			return 0;
		}

		if (!stackInclude.equals("") && labels.hasNonNull(NODE_LABEL_STACK_NAME)) {
			String stack = labels.get(NODE_LABEL_STACK_NAME).textValue();
			if (stack != null && !stack.matches(stackInclude)) {
				return 0;
			}
		}

		if (labels.hasNonNull(NODE_LABEL_STACK_SERVICE_NAME)) {
			if (configuration.getProperty(CONFIG_LIMIT_ONE_CONTAINER, "false").equals("true")) {
				String stackService = labels.get(NODE_LABEL_STACK_SERVICE_NAME).textValue();
				if (stackService != null && seen.containsKey(stackService)) {
					return 0;
				}
			}
			return this.countTimesSeen(labels.get(NODE_LABEL_STACK_SERVICE_NAME).asText());
		}

		return 1;
	}

	private boolean skipThisLabel(String label, JsonNode labels) {
		return (this.isExclude(label) && labels.hasNonNull(label.replace("-", ".")));
	}

	/**
	 * Start processing a new node.
	 */
	private class RancherNode {
		// The node being built.
		protected final NodeEntryImpl nodeEntry;

		// Tag set for the node being built.
		protected final HashSet<String> tagSet;

		// Labels read from the node.
		protected JsonNode labels;

		public RancherNode() {
			nodeEntry = new NodeEntryImpl();
			if (tags == null) {
				tagSet = new HashSet<>();
			} else {
				tagSet = new HashSet<>(Arrays.asList(tags.split("\\s*,\\s*")));
			}
		}

		/**
		 * Adds attributes and tags from labels array.
		 *
		 * @param node The node we are building.
		 */
		protected void processLabels(JsonNode node) {
			if (labels.hasNonNull(NODE_LABEL_STACK_SERVICE_NAME)) {
				String stackService = labels.get(NODE_LABEL_STACK_SERVICE_NAME).asText();
				String[] parts = stackService.split("/");
				nodeEntry.setAttribute("stack", parts[0]);
				nodeEntry.setAttribute("service", parts[1]);
				tagSet.add(parts[1]);
			}

			if (labels.hasNonNull(NODE_LABEL_STACK_NAME)) {
				nodeEntry.setAttribute("stack", labels.get(NODE_LABEL_STACK_NAME).asText());
			}

			setAttributeForLabel(NODE_LABEL_STACK_NAME);
			setAttributeForLabel("io.rancher.container.start_once");
			setAttributeForLabel("io.rancher.container.system");
			setAttributeForLabel("io.rancher.scheduler.global");

			Iterator<Map.Entry<String, JsonNode>> iter = labels.fields();
			while (iter.hasNext()) {
				Map.Entry<String, JsonNode> entry = iter.next();
				String label = entry.getKey();
				String value = entry.getValue().asText();
				this.setAttributeForLabel(label, value);
				this.setTagForLabel(label, value);
			}
			if (node.hasNonNull(NODE_IMAGE_UUID)) {
				tagSet.add(node.get(NODE_IMAGE_UUID).asText().replaceFirst("^[^/]+/", ""));
			}
			nodeEntry.setTags(tagSet);
		}

		/**
		 * Sets an attribute from a label with a name defined by the text after the last
		 * dot in the label name.
		 *
		 * @param label The name of the label we are considering.
		 */
		private void setAttributeForLabel(String label) {
			String attribute = this.last(label);
			if (labels.hasNonNull(label)) {
				if (attribute.equals("description")) {
					nodeEntry.setDescription(labels.get(label).asText());
				} else {
					nodeEntry.setAttribute(attribute, labels.get(label).asText());
				}
			}
		}

		/**
		 * Determine whether an attribute should be set for a given label.
		 *
		 * @param label The label to set.
		 * @param value The value to assign to the label.
		 */
		private void setAttributeForLabel(String label, String value) {
			if (attributeInclude.length() > 0 && label.matches(attributeInclude)) {
				String attribute = this.last(label);
				if (attribute.equals("description")) {
					nodeEntry.setDescription(value);
				} else {
					nodeEntry.setAttribute(this.last(attribute), value);
				}
			}
		}

		/**
		 * Sets a tag from a label with a name defined by the text after the last dot in
		 * the label name.
		 *
		 * @param label The name of the label we are considering.
		 */
		private void setTagForLabel(String label, String value) {
			if (tagInclude.length() > 0 && label.matches(tagInclude)) {
				tagSet.add(value);
			}
		}

		/**
		 * Gets the part of a string after the last occurrence of pattern.
		 *
		 * @param string  The string we are splitting.
		 * @return The part of the string after the last separator.
		 */
		private String last(String string) {
			String[] keyParts = string.split("[.]");
			return keyParts[keyParts.length - 1];
		}
	}

	private class RancherContainerNode extends RancherNode {
		public NodeEntryImpl getNodeEntry(String environmentName, JsonNode node) {
			String name = environmentName + "_" + node.get(NODE_NAME).asText();
			nodeEntry.setNodename(name);
			nodeEntry.setHostname(node.path("hostId").asText());
			nodeEntry.setUsername("root");
			nodeEntry.setAttribute(NODE_ATT_ID, node.path(NODE_ID).asText());
			nodeEntry.setAttribute(NODE_ATT_EXTERNAL_ID, node.path("externalId").asText());
			nodeEntry.setAttribute(NODE_ATT_FILE_COPIER, RANCHER_SERVICE_PROVIDER);
			nodeEntry.setAttribute(NODE_ATT_NODE_EXECUTOR, RANCHER_SERVICE_PROVIDER);
			nodeEntry.setAttribute(NODE_ATT_TYPE, node.path("kind").asText());
			nodeEntry.setAttribute(NODE_ATT_STATE, node.path(NODE_STATE).asText());
			nodeEntry.setAttribute(NODE_ATT_ACCOUNT, node.path(NODE_ACCOUNT_ID).asText());
			nodeEntry.setAttribute(NODE_ATT_ENVIRONMENT, environmentName);
			nodeEntry.setAttribute(NODE_ATT_IMAGE, node.path(NODE_IMAGE_UUID).asText());
			// Storage path for Rancher API access key.
			String accessKeyPath = CONFIG_ACCESSKEY_PATH;
			nodeEntry.setAttribute(accessKeyPath, configuration.getProperty(accessKeyPath));
			// Storage path for Rancher API secret key.
			String secretKeyPath = CONFIG_SECRETKEY_PATH;
			nodeEntry.setAttribute(secretKeyPath, configuration.getProperty(secretKeyPath));

			JsonNode actions = node.path(NODE_ATT_ACTIONS);
			if (actions.hasNonNull(NODE_ACTION_EXECUTE)) {
				nodeEntry.setAttribute(NODE_ACTION_EXECUTE, actions.get(NODE_ACTION_EXECUTE).asText());
			}
			nodeEntry.setAttribute(NODE_LINK_SERVICES, node.path(NODE_ATT_LINKS).path(NODE_LINK_SERVICES).asText());
			nodeEntry.setAttribute(NODE_ATT_SELF, node.path(NODE_ATT_LINKS).path(NODE_ATT_SELF).asText());

			if (node.hasNonNull(OPT_LABELS)) {
				labels = node.get(OPT_LABELS);
				this.processLabels(node);
			}

			return nodeEntry;
		}
	}

	private class RancherServiceNode extends RancherNode {
		public NodeEntryImpl getNodeEntry(String environmentName, JsonNode node) {
			String name = environmentName + "_" + getStackName(node) + "-" + node.get(NODE_NAME).asText();
			nodeEntry.setNodename(name);
			nodeEntry.setUsername("root");
			nodeEntry.setAttribute(NODE_ATT_ID, node.path(NODE_ID).asText());
			nodeEntry.setAttribute(NODE_ATT_TYPE, node.path("kind").asText());
			nodeEntry.setAttribute(NODE_ATT_STATE, node.path(NODE_STATE).asText());
			nodeEntry.setAttribute(NODE_ATT_ACCOUNT, node.path(NODE_ACCOUNT_ID).asText());
			nodeEntry.setAttribute(NODE_ATT_ENVIRONMENT, environmentName);
			nodeEntry.setAttribute(NODE_ATT_IMAGE, node.path(LAUNCH_CONFIG).path(NODE_IMAGE_UUID).asText());
			// Storage path for Rancher API access key.
			String accessKeyPath = CONFIG_ACCESSKEY_PATH;
			nodeEntry.setAttribute(accessKeyPath, configuration.getProperty(accessKeyPath));
			// Storage path for Rancher API secret key.
			String secretKeyPath = CONFIG_SECRETKEY_PATH;
			nodeEntry.setAttribute(secretKeyPath, configuration.getProperty(secretKeyPath));
			StringBuilder instanceIds = new StringBuilder();
			node.path("instanceIds").elements()
					.forEachRemaining(instance -> instanceIds.append(instance.asText()).append(","));
			nodeEntry.setAttribute("instanceIds", StringUtils.chomp(instanceIds.toString(), ","));
			nodeEntry.setAttribute(NODE_ATT_SELF, node.path(NODE_ATT_LINKS).path(NODE_ATT_SELF).asText());
			return nodeEntry;
		}

		private String getStackName(JsonNode node) {
			return stackNames.get(node.get("stackId").asText());
		}
	}

	/**
	 * Returns true if property is set to exclude relevant nodes.
	 *
	 * @param property The name of the configuration value we are examining.
	 * @return True if the property value is "Exclude"
	 */
	private boolean isExclude(String property) {
		return configuration.getProperty(property, OPT_EXCLUDE).contentEquals(OPT_EXCLUDE);
	}

	/**
	 * Count the number of containers are in each service for each stack.
	 *
	 * By constructing a node filter of "seen:1" we can run on only one container in
	 * a service even when we are not limiting the project node set to the one
	 * container per service.
	 *
	 * @param name The composite stack and service.
	 */
	private Integer countTimesSeen(String name) {
		int count;
		if (seen.get(name) == null) {
			count = 1;
		} else {
			count = 1 + seen.get(name);
		}
		seen.put(name, count);
		return count;
	}

	/**
	 * Makes the underlying API call to get the list of nodes for the environment.
	 *
	 * @param environment The Rancher accountId for the environment.
	 * @return An array of JsonNodes representing the containers in the environment.
	 * @throws IOException when API request fails.
	 */
	private ArrayList<JsonNode> getContainers(String environment) throws IOException {
		String path = url + PATH_PROJECTS + environment + "/containers";
		return getCollection(path);
	}

	/**
	 * Makes the underlying API call to get the list of nodes for the environment.
	 *
	 * @param environment The Rancher accountId for the environment.
	 * @return An array of JsonNodes representing the containers in the environment.
	 * @throws IOException when API request fails.
	 */
	private ArrayList<JsonNode> getServices(String environment) throws IOException {
		String path = url + PATH_PROJECTS + environment + "/services";
		return getCollection(path);
	}

	/**
	 * Makes the underlying API call to get the list of nodes for the environment.
	 *
	 * @param environment The Rancher accountId for the environment.
	 * @return An array of JsonNodes representing the containers in the environment.
	 * @throws IOException when API request fails.
	 */
	private ArrayList<JsonNode> getStacks(String environment) throws IOException {
		String path = url + PATH_PROJECTS + environment + "/stacks";
		return getCollection(path);
	}

	/**
	 * Makes the underlying API call to get the list of nodes for the environment.
	 *
	 * @param path The Rancher accountId for the environment.
	 * @return An array of JsonNodes representing the containers in the environment.
	 * @throws IOException when API request fails.
	 */
	private ArrayList<JsonNode> getCollection(String path) throws IOException {
		ArrayList<JsonNode> data = new ArrayList<>();
		while (!path.equals("null")) {
			JsonNode root = client.get(path);
			Iterator<JsonNode> instances = root.get("data").elements();
			while (instances.hasNext()) {
				data.add(instances.next());
			}
			if (root.has(JSON_PAGINATION) && root.get(JSON_PAGINATION).has("next")) {
				path = root.get(JSON_PAGINATION).get("next").asText();
			} else {
				path = "null";
			}
		}
		return data;
	}

	/**
	 * Gets the environment name for the specified environment ID.
	 *
	 * @param environment The Rancher accountId for the environment.
	 * @return The name of the indicated environment.
	 * @throws IOException when API request fails.
	 */
	private String getEnvironmentName(String environment) throws IOException {
		String path = url + PATH_PROJECTS + environment;
		JsonNode jsonNode = client.get(path);
		return jsonNode.path(NODE_NAME).asText(environment);
	}
}

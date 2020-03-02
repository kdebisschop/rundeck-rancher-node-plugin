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

import java.io.IOException;
import java.util.*;

import com.dtolabs.rundeck.core.plugins.configuration.ConfigurationException;
import org.apache.log4j.Level;

import com.dtolabs.rundeck.core.common.Framework;
import com.dtolabs.rundeck.core.common.INodeSet;
import com.dtolabs.rundeck.core.common.NodeEntryImpl;
import com.dtolabs.rundeck.core.common.NodeSetImpl;
import com.dtolabs.rundeck.core.resources.ResourceModelSource;
import com.dtolabs.rundeck.core.resources.ResourceModelSourceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.Call;
import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Request.Builder;
import okhttp3.Response;

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
	private OkHttpClient client;

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
		this.init(configuration, new OkHttpClient());
	}

	/**
	 * Constructor for unit testing.
	 *
	 * @param configuration Configuration variables set in RancherResourceModelSourceFactory
	 * @param client HTTP client used for unit testing.
	 */
	public RancherResourceModelSource(Properties configuration, OkHttpClient client) throws ConfigurationException {
		this.init(configuration, client);
	}

	private void init(Properties configuration, OkHttpClient client) throws ConfigurationException {
		this.client = client;
		this.configuration = configuration;
		tags = configuration.getProperty("tags");
		url = configuration.getProperty(RancherShared.RANCHER_CONFIG_ENDPOINT);
		attributeInclude = configuration.getProperty(RancherShared.CONFIG_LABELS_INCLUDE_ATTRIBUTES, "");
		tagInclude = configuration.getProperty(RancherShared.CONFIG_LABELS_INCLUDE_TAGS, "");
		stackInclude = configuration.getProperty(RancherShared.CONFIG_STACK_FILTER, "");
	}

	@SuppressWarnings("RedundantThrows")
	@Override
	public INodeSet getNodes() throws ResourceModelSourceException {
		iNodeEntries = new NodeSetImpl();
		String environmentIds = configuration.getProperty(RancherShared.CONFIG_ENVIRONMENT_IDS);
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
		try {
			environmentName = this.getEnvironmentName(environmentId);
		} catch (IOException e) {
			environmentName = environmentId;
			Framework.logger.log(Level.WARN, "Failed getting environment name");
			Framework.logger.log(Level.WARN, e.getMessage());
		}

		if (configuration.getProperty(RancherShared.CONFIG_NODE_TYPE_INCLUDE_CONTAINER, "true").equals("true")) {
			try {
				data = this.getContainers(environmentId);
			} catch (IOException e) {
				Framework.logger.log(Level.WARN, e.getMessage());
				return;
			}
			for (JsonNode node : data) {
				if (isExclude(RancherShared.CONFIG_HANDLE_STOPPED) && !node.get("state").asText().equals("running")) {
					continue;
				}

				int count = 0;
				if (node.hasNonNull("labels")) {
					count = countProcessableByLabel(node.get("labels"));
					if (count == 0) {
						continue;
					}
				}

				RancherContainerNode rancherNode = new RancherContainerNode();
				try {
					NodeEntryImpl nodeEntry = rancherNode.getNodeEntry(environmentName, node);

					if (count != 0) {
						nodeEntry.setAttribute("seen", Integer.toString(count));
					}

					if (nodeEntry.getNodename() == null) {
						String name = node.get("name").asText() + "(" + node.get("id").asText() + ")";
						String self = node.get("links").get("self").asText();
						Framework.logger.log(Level.WARN, name + " " + node.get("accountId").asText() + " " + self);
					} else {
						iNodeEntries.putNode(nodeEntry);
					}
				} catch (IllegalArgumentException | NullPointerException e) {
					Framework.logger.log(Level.WARN, e.getMessage());
				}
			}
		}

		if (configuration.getProperty(RancherShared.CONFIG_NODE_TYPE_INCLUDE_SERVICE, "false").equals("true")) {
			try {
				stackNames = new HashMap<>();
				for (JsonNode node : this.getStacks(environmentId)) {
					stackNames.put(node.get("id").asText(), node.get("name").asText());
				}
				for (JsonNode node : this.getServices(environmentId)) {
					RancherServiceNode rancherNode = new RancherServiceNode();
					try {
						NodeEntryImpl nodeEntry = rancherNode.getNodeEntry(environmentName, node);
						if (nodeEntry.getNodename() == null) {
							String name = node.get("name").asText() + "(" + node.get("id").asText() + ")";
							String self = node.get("links").get("self").asText();
							String message = name + " " + node.get("accountId").asText() + " " + self;
							Framework.logger.log(Level.WARN, message);
						} else {
							iNodeEntries.putNode(nodeEntry);
						}
					} catch (IllegalArgumentException | NullPointerException e) {
						Framework.logger.log(Level.WARN, e.getMessage());
					}
				}
			} catch (IOException e) {
				Framework.logger.log(Level.WARN, e.getMessage());
			}
		}
	}

	private Integer countProcessableByLabel(JsonNode labels) {
		String label;
		label = RancherShared.CONFIG_HANDLE_SYSTEM;
		if (this.isExclude(label) && labels.hasNonNull(label.replaceAll("-", "."))) {
			return 0;
		}
		label = RancherShared.CONFIG_HANDLE_GLOBAL;
		if (this.isExclude(label) && labels.hasNonNull(label.replaceAll("-", "."))) {
			return 0;
		}

		if (!stackInclude.equals("")) {
			if (labels.hasNonNull("io.rancher.stack.name")) {
				String stack = labels.get("io.rancher.stack.name").textValue();
				if (stack != null && !stack.matches(stackInclude)) {
					return 0;
				}
			}
		}

		if (labels.hasNonNull("io.rancher.stack_service.name")) {
			if (configuration.getProperty(RancherShared.CONFIG_LIMIT_ONE_CONTAINER, "false").equals("true")) {
				String stackService = labels.get("io.rancher.stack_service.name").textValue();
				if (stackService != null && seen.containsKey(stackService)) {
					return 0;
				}
			}
			return this.countTimesSeen(labels.get("io.rancher.stack_service.name").asText());
		}

		return 1;
	}

	private String getStackName(JsonNode node) {
		return stackNames.get(node.get("stackId").asText());
	}

	/**
	 * Start processing a new node.
	 */
	private class RancherNode {
		// The node being built.
		protected NodeEntryImpl nodeEntry;

		// Tag set for the node being built.
		protected HashSet<String> tagset;

		// Labels read from the node.
		protected JsonNode labels;

		public RancherNode() {
			nodeEntry = new NodeEntryImpl();
			if (tags == null) {
				tagset = new HashSet<>();
			} else {
				tagset = new HashSet<>(Arrays.asList(tags.split("\\s*,\\s*")));
			}
		}

		/**
		 * Adds attributes and tags from labels array.
		 *
		 * @param node The node we are building.
		 */
		protected void processLabels(JsonNode node) {
			if (labels.hasNonNull("io.rancher.stack_service.name")) {
				String stackService = labels.get("io.rancher.stack_service.name").asText();
				String[] parts = stackService.split("/");
				nodeEntry.setAttribute("stack", parts[0]);
				nodeEntry.setAttribute("service", parts[1]);
				tagset.add(parts[1]);
			}

			if (labels.hasNonNull("io.rancher.stack.name")) {
				nodeEntry.setAttribute("stack", labels.get("io.rancher.stack.name").asText());
			}

			setAttributeForLabel("io.rancher.stack.name");
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
			tagset.add(node.get("imageUuid").asText().replaceFirst("^[^/]+/", ""));
			nodeEntry.setTags(tagset);
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
				tagset.add(value);
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
		public NodeEntryImpl getNodeEntry(String environmentName, JsonNode node) throws NullPointerException {
			String name = environmentName + "_" + node.get("name").asText();
			nodeEntry.setNodename(name);
			nodeEntry.setHostname(node.path("hostId").asText());
			nodeEntry.setUsername("root");
			nodeEntry.setAttribute("id", node.path("id").asText());
			nodeEntry.setAttribute("externalId", node.path("externalId").asText());
			nodeEntry.setAttribute("file-copier", RancherShared.RANCHER_SERVICE_PROVIDER);
			nodeEntry.setAttribute("node-executor", RancherShared.RANCHER_SERVICE_PROVIDER);
			nodeEntry.setAttribute("type", node.path("kind").asText());
			nodeEntry.setAttribute("state", node.path("state").asText());
			nodeEntry.setAttribute("account", node.path("accountId").asText());
			nodeEntry.setAttribute("environment", environmentName);
			nodeEntry.setAttribute("image", node.path("imageUuid").asText());
			// Storage path for Rancher API access key.
			String accessKeyPath = RancherShared.CONFIG_ACCESSKEY_PATH;
			nodeEntry.setAttribute(accessKeyPath, configuration.getProperty(accessKeyPath));
			// Storage path for Rancher API secret key.
			String secretKeyPath = RancherShared.CONFIG_SECRETKEY_PATH;
			nodeEntry.setAttribute(secretKeyPath, configuration.getProperty(secretKeyPath));

			JsonNode actions = node.path("actions");
			if (actions.hasNonNull("execute")) {
				nodeEntry.setAttribute("execute", actions.get("execute").asText());
			}
			nodeEntry.setAttribute("services", node.path("links").path("services").asText());
			nodeEntry.setAttribute("self", node.path("links").path("self").asText());

			if (node.hasNonNull("labels")) {
				labels = node.get("labels");
				this.processLabels(node);
			}

			return nodeEntry;
		}
	}

	private class RancherServiceNode extends RancherNode {
		public NodeEntryImpl getNodeEntry(String environmentName, JsonNode node) throws NullPointerException {
			String name = environmentName + "_" + getStackName(node) + "-" + node.get("name").asText();
			nodeEntry.setNodename(name);
			nodeEntry.setUsername("root");
			nodeEntry.setAttribute("id", node.path("id").asText());
			nodeEntry.setAttribute("type", node.path("kind").asText());
			nodeEntry.setAttribute("state", node.path("state").asText());
			nodeEntry.setAttribute("account", node.path("accountId").asText());
			nodeEntry.setAttribute("environment", environmentName);
			nodeEntry.setAttribute("image", node.path("launchConfig").path("imageUuid").asText());
			// Storage path for Rancher API access key.
			String accessKeyPath = RancherShared.CONFIG_ACCESSKEY_PATH;
			nodeEntry.setAttribute(accessKeyPath, configuration.getProperty(accessKeyPath));
			// Storage path for Rancher API secret key.
			String secretKeyPath = RancherShared.CONFIG_SECRETKEY_PATH;
			nodeEntry.setAttribute(secretKeyPath, configuration.getProperty(secretKeyPath));
			nodeEntry.setAttribute("self", node.path("links").path("self").asText());
			return nodeEntry;
		}
	}

	/**
	 * Returns true if property is set to exclude relevant nodes.
	 *
	 * @param property The name of the configuration value we are examining.
	 * @return True if the property value is "Exclude"
	 */
	private boolean isExclude(String property) {
		return configuration.getProperty(property, "Exclude").contentEquals("Exclude");
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
		String path = url + "/projects/" + environment + "/containers";
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
		String path = url + "/projects/" + environment + "/services";
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
		String path = url + "/projects/" + environment + "/stacks";
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
		ObjectMapper objectMapper = new ObjectMapper();
		String accessKey = configuration.getProperty(RancherShared.CONFIG_ACCESSKEY);
		String secretKey = configuration.getProperty(RancherShared.CONFIG_SECRETKEY);

		ArrayList<JsonNode> data = new ArrayList<>();
		while (!path.equals("null")) {
			Builder requestBuilder = new Request.Builder().url(path);
			requestBuilder.addHeader("Authorization", Credentials.basic(accessKey, secretKey));
			Response response = client.newCall(requestBuilder.build()).execute();
			assert response.body() != null;
			String json = response.body().string();
			JsonNode root = objectMapper.readTree(json);
			Iterator<JsonNode> instances = root.get("data").elements();
			while (instances.hasNext()) {
				data.add(instances.next());
			}
			if (root.has("pagination") && root.get("pagination").has("next")) {
				path = root.get("pagination").get("next").asText();
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
		String accessKey = configuration.getProperty(RancherShared.CONFIG_ACCESSKEY);
		String secretKey = configuration.getProperty(RancherShared.CONFIG_SECRETKEY);

		HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(url + "/projects/" + environment)).newBuilder();
		String path = urlBuilder.build().toString();

		Builder requestBuilder = new Request.Builder().url(path);
		requestBuilder.addHeader("Authorization", Credentials.basic(accessKey, secretKey));

		Call call = client.newCall(requestBuilder.build());
		Response response = call.execute();
		assert response.body() != null;
		String json = response.body().string();

		ObjectMapper objectMapper = new ObjectMapper();
		return objectMapper.readTree(json).path("name").asText(environment);
	}
}

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Level;

import com.dtolabs.rundeck.core.common.Framework;
import com.dtolabs.rundeck.core.common.INodeSet;
import com.dtolabs.rundeck.core.common.NodeEntryImpl;
import com.dtolabs.rundeck.core.common.NodeSetImpl;
import com.dtolabs.rundeck.core.resources.ResourceModelSource;
import com.dtolabs.rundeck.core.resources.ResourceModelSourceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
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

	// Storage path for Rancher API access key.
	private String accessKeyPath = RancherShared.CONFIG_ACCESSKEY_PATH;

	// Storage path for Rancher API secret key.
	private String secretKeyPath = RancherShared.CONFIG_SECRETKEY_PATH;

	/**
	 * The required object constructor.
	 *
	 * @param configuration
	 */
	public RancherResourceModelSource(Framework framework, Properties configuration) {
		this.configuration = configuration;
		tags = configuration.getProperty("tags");
		url = configuration.getProperty(RancherShared.CONFIG_ENDPOINT, "");
		attributeInclude = configuration.getProperty(RancherShared.CONFIG_LABELS_INCLUDE_ATTRIBUTES, "");
		tagInclude = configuration.getProperty(RancherShared.CONFIG_LABELS_INCLUDE_TAGS, "");
		stackInclude = configuration.getProperty(RancherShared.CONFIG_STACK_FILTER, "");

		// avoid creating several instances, should be singleton
		client = new OkHttpClient();
	}

	@Override
	public INodeSet getNodes() throws ResourceModelSourceException {
		iNodeEntries = new NodeSetImpl();
		String environmentIds = configuration.getProperty(RancherShared.CONFIG_ENVIRONMENT_IDS);
		for (String environmentId : environmentIds.split("[ ,]+")) {
			seen = new HashMap<String, Integer>();
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
			Framework.logger.log(Level.WARN, e.getCause().getMessage());
		}

		try {
			data = this.getContainers(environmentId);
		} catch (IOException e) {
			Framework.logger.log(Level.WARN, e.getMessage());
			Framework.logger.log(Level.WARN, e.getCause().getMessage());
			return;
		}

		for (JsonNode node : data) {
			if (isExclude(RancherShared.CONFIG_HANDLE_STOPPED) && !node.get("state").asText().equals("running")) {
				continue;
			}

			Integer count = 0;
			if (node.hasNonNull("labels")) {
				count = countProcessableByLabel(node.get("labels"));
				if (count == 0) {
					continue;
				}
			}

			try {
				RancherNode rancherNode = new RancherNode(environmentName, node);
				NodeEntryImpl nodeEntry = rancherNode.getNodeEntry();

				if (count != 0) {
					nodeEntry.setAttribute("seen", count.toString());
				}

				if (nodeEntry.getNodename() == null) {
					String name = node.get("name").asText() + "(" + node.get("id").asText() + ")";
					String self = node.get("links").get("self").asText();
					Framework.logger.log(Level.WARN, name + " " + node.get("accountId").asText() + " " + self);
				} else {
					iNodeEntries.putNode(nodeEntry);
				}
			} catch (IllegalArgumentException e) {
				Framework.logger.log(Level.WARN, e.getMessage());
				Framework.logger.log(Level.WARN, e.getCause().getMessage());
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
			if (configuration.getProperty(RancherShared.CONFIG_LIMIT_ONE_CONTAINER) != null) {
				String stackService = labels.get("io.rancher.stack_service.name").textValue();
				if (stackService != null && seen.containsKey(stackService)) {
					return 0;
				}
			}
			return this.countTimesSeen(labels.get("io.rancher.stack_service.name").asText());
		}

		return 1;
	}

	/**
	 * Start processing a new node.
	 */
	private class RancherNode {
		// The node being built.
		private NodeEntryImpl nodeEntry;

		// Tag set for the node being built.
		private HashSet<String> tagset;

		// Labels read from the node.
		private JsonNode labels;

		public RancherNode(String environmentName, JsonNode node) {
			nodeEntry = new NodeEntryImpl();
			if (tags == null) {
				tagset = new HashSet<String>();
			} else {
				tagset = new HashSet<String>(Arrays.asList(tags.split("\\s*,\\s*")));
			}

			String name = environmentName + "_" + node.get("name").asText();
			nodeEntry.setNodename(name);
			nodeEntry.setHostname(node.get("hostId").asText());
			nodeEntry.setUsername("root");
			nodeEntry.setAttribute("id", node.get("id").asText());
			nodeEntry.setAttribute("externalId", node.get("externalId").asText());
			nodeEntry.setAttribute("hostId", node.get("hostId").asText());
			nodeEntry.setAttribute("file-copier", RancherShared.SERVICE_PROVIDER_NAME);
			nodeEntry.setAttribute("node-executor", RancherShared.SERVICE_PROVIDER_NAME);
			nodeEntry.setAttribute("type", node.get("kind").asText());
			nodeEntry.setAttribute("state", node.get("state").asText());
			nodeEntry.setAttribute("account", node.get("accountId").asText());
			nodeEntry.setAttribute("environment", environmentName);
			nodeEntry.setAttribute("image", node.get("imageUuid").asText());
			nodeEntry.setAttribute(accessKeyPath, configuration.getProperty(accessKeyPath));
			nodeEntry.setAttribute(secretKeyPath, configuration.getProperty(secretKeyPath));

			JsonNode actions = node.get("actions");
			if (actions.hasNonNull("execute")) {
				nodeEntry.setAttribute("execute", actions.get("execute").asText());
			}
			if (actions.hasNonNull("upgrade")) {
				nodeEntry.setAttribute("upgrade", actions.get("upgrade").asText());
			}
			nodeEntry.setAttribute("services", node.get("links").get("services").asText());
			nodeEntry.setAttribute("self", node.get("links").get("self").asText());

			if (node.hasNonNull("labels")) {
				labels = node.get("labels");
				this.processLabels(node);
			}
		}

		public NodeEntryImpl getNodeEntry() {
			return nodeEntry;
		}

		/**
		 * Adds attributes and tags from labels array.
		 *
		 * @param node The node we are building.
		 */
		private void processLabels(JsonNode node) {
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
			String attribute = this.last(label, "[.]");
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
		 * @param label
		 * @param value
		 */
		private void setAttributeForLabel(String label, String value) {
			if (attributeInclude.length() > 0 && label.matches(attributeInclude)) {
				String attribute = this.last(label, "[.]");
				if (attribute.equals("description")) {
					nodeEntry.setDescription(value);
				} else {
					nodeEntry.setAttribute(this.last(attribute, "[.]"), value);
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
		 * @param pattern The pattern the split with.
		 * @return The part of the string after the last separator.
		 */
		private String last(String string, String pattern) {
			String[] keyParts = string.split("[.]");
			return keyParts[keyParts.length - 1];
		}
	}

	/**
	 * Returns true if property is set to exclude relevant nodes.
	 *
	 * @param property The name of the configuration value we are examining.
	 * @return
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
	 * @param name
	 */
	private Integer countTimesSeen(String name) {
		Integer count;
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
	 * @param environment
	 * @return An array of JsonNodes representing the containers in the environment.
	 * @throws IOException
	 */
	private ArrayList<JsonNode> getContainers(String environment) throws IOException {
		ObjectMapper objectMapper = new ObjectMapper();
		String accessKey = configuration.getProperty(RancherShared.CONFIG_ACCESSKEY);
		String secretKey = configuration.getProperty(RancherShared.CONFIG_SECRETKEY);
		String path = url + "/projects/" + environment + "/containers";

		ArrayList<JsonNode> data = new ArrayList<>();
		while (!path.equals("null")) {
			Request request = new Request.Builder().url(path)
					.addHeader("Authorization", Credentials.basic(accessKey, secretKey)).build();
			Response response = client.newCall(request).execute();
			String json = response.body().string();
			JsonNode root = objectMapper.readTree(json);
			path = root.get("pagination").get("next").asText();
			Iterator<JsonNode> instances = root.get("data").elements();
			while (instances.hasNext()) {
				data.add(instances.next());
			}
		}
		return data;
	}

	/**
	 * Gets the environment name for the specified environment ID.
	 *
	 * @param environment The Rancher accointId for the environment.
	 * @return The name of the indicated environment.
	 * @throws IOException
	 */
	private String getEnvironmentName(String environment) throws IOException {
		String accessKey = configuration.getProperty(RancherShared.CONFIG_ACCESSKEY);
		String secretKey = configuration.getProperty(RancherShared.CONFIG_SECRETKEY);

		HttpUrl.Builder urlBuilder = HttpUrl.parse(url + "/projects/" + environment).newBuilder();
		String path = urlBuilder.build().toString();

		Request request = new Request.Builder().url(path)
				.addHeader("Authorization", Credentials.basic(accessKey, secretKey)).build();
		Response response = client.newCall(request).execute();
		String json = response.body().string();

		ObjectMapper objectMapper = new ObjectMapper();
		return objectMapper.readTree(json).path("name").asText(environment);
	}
}

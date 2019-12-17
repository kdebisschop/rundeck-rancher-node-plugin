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

import com.bioraft.rundeck.rancher.RancherShared;

import com.dtolabs.rundeck.core.plugins.Plugin;
import com.dtolabs.rundeck.core.plugins.configuration.*;
import com.dtolabs.rundeck.core.resources.ResourceModelSource;
import com.dtolabs.rundeck.core.resources.ResourceModelSourceFactory;
import com.dtolabs.rundeck.plugins.ServiceNameConstants;
import com.dtolabs.rundeck.plugins.util.DescriptionBuilder;

import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;

/**
 * RancherResourceModelSourceFactory establishes parameters for Rancher node
 * resources.
 *
 * @author Karl DeBisschop <kdebisschop@gmail.com>
 * @since 2019-12-08
 */
@Plugin(name = RancherShared.SERVICE_PROVIDER_NAME, service = ServiceNameConstants.ResourceModelSource)
public class RancherResourceModelSourceFactory implements ResourceModelSourceFactory, Describable {
	@Override
	public ResourceModelSource createResourceModelSource(Properties configuration) throws ConfigurationException {
		return new RancherResourceModelSource(configuration);
	}

	static final Description DESC;

	static {
		DescriptionBuilder builder = DescriptionBuilder.builder();
		builder.name(RancherShared.SERVICE_PROVIDER_NAME);
		builder.title("Rancher Node Executor");
		builder.description("Executes a command on a remote rancher node.");

		builder.property(PropertyUtil.string(RancherShared.CONFIG_ENDPOINT, "API EndPoint",
				"URL of API endpoint (e.g., https://my,rancher.host/v2-beta)", true, null));

		builder.property(PropertyUtil.string(RancherShared.CONFIG_ENVIRONMENT_IDS, "Environment IDs",
				"List of environments to include, comma-separated", true, null));

		builder.property(PropertyUtil.string(RancherShared.CONFIG_ACCESSKEY, "Access Key",
				"The Rancher API Access Key", true, null, null, null,
				Collections.singletonMap("displayType", (Object) StringRenderingConstants.DisplayType.PASSWORD)));

		builder.property(PropertyUtil.string(RancherShared.CONFIG_SECRETKEY, "Secret Key",
				"The Rancher API Secret Key", true, null, null, null,
				Collections.singletonMap("displayType", (Object) StringRenderingConstants.DisplayType.PASSWORD)));

		builder.property(PropertyUtil.string(RancherShared.CONFIG_ACCESSKEY_PATH, "Access Key Storage Path",
				"Path in Rundeck Storage for the Rancher API Access Key (e.g. keys/rancher/access.key)", true, "keys/rancher/access.key"));

		builder.property(PropertyUtil.string(RancherShared.CONFIG_SECRETKEY_PATH, "Secret Key Storage Path",
				"Path in Rundeck Storage for the Rancher API Secret Key (e.g. keys/rancher/secret.key)", true, "keys/rancher/secret.key"));

		builder.property(PropertyUtil.string(RancherShared.CONFIG_STACK_FILTER, "Stack Filter",
				"A regular expression for stacks to be included", true, "^.*$"));

		builder.property(PropertyUtil.bool(RancherShared.CONFIG_LIMIT_ONE_CONTAINER, "Limit to One Container",
				"Only run on one container for each service", true, "false"));

		builder.property(PropertyUtil.select(RancherShared.CONFIG_HANDLE_STOPPED, "Handle Stopped Containers",
				"Exclude stopped containers", true, "Exclude", Arrays.asList(new String[] { "Exclude", "Include" })));

		builder.property(PropertyUtil.select(RancherShared.CONFIG_HANDLE_SYSTEM, "Handle System Containers",
				"Exclude system containers", true, "Exclude", Arrays.asList(new String[] { "Exclude", "Include" })));

		builder.property(PropertyUtil.select(RancherShared.CONFIG_HANDLE_GLOBAL, "Handle Global Containers",
				"Exclude global containers", true, "Exclude", Arrays.asList(new String[] { "Exclude", "Include" })));

		builder.property(PropertyUtil.string(RancherShared.CONFIG_TAGS, "Tags",
				"Tags to apply to all nodes in this set", false, "rancher"));

		builder.property(PropertyUtil.string(RancherShared.CONFIG_LABELS_INCLUDE_ATTRIBUTES,
				"Labels made into attributes",
				"A regular expression for labels whose values will be used as a node attribute (name will be last part of label ID)",
				false, ""));

		builder.property(PropertyUtil.string(RancherShared.CONFIG_LABELS_INCLUDE_TAGS, "Labels made into tags",
				"A regular expression for labels whose values will be used as tags for a node", false, ""));

		DESC = builder.build();
	}

	public Description getDescription() {
		return DESC;
	}
}

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

import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;

import com.dtolabs.rundeck.core.plugins.Plugin;
import com.dtolabs.rundeck.core.plugins.configuration.*;
import com.dtolabs.rundeck.core.resources.ResourceModelSource;
import com.dtolabs.rundeck.core.resources.ResourceModelSourceFactory;
import com.dtolabs.rundeck.plugins.ServiceNameConstants;
import com.dtolabs.rundeck.plugins.util.DescriptionBuilder;

import static com.bioraft.rundeck.rancher.Constants.OPT_EXCLUDE;
import static com.bioraft.rundeck.rancher.Constants.OPT_INCLUDE;
import static com.bioraft.rundeck.rancher.RancherShared.*;

/**
 * RancherResourceModelSourceFactory establishes parameters for Rancher node
 * resources.
 *
 * @author Karl DeBisschop <kdebisschop@gmail.com>
 * @since 2019-12-08
 */
@Plugin(name = RancherShared.RANCHER_SERVICE_PROVIDER, service = ServiceNameConstants.ResourceModelSource)
public class RancherResourceModelSourceFactory implements ResourceModelSourceFactory, Describable {

    static final Description DESC;

    static {
        DescriptionBuilder builder = DescriptionBuilder.builder();
        builder.name(RANCHER_SERVICE_PROVIDER);
        builder.title("Rancher Node Executor");
        builder.description("Executes a command on a remote rancher node.");

        builder.property(PropertyUtil.string(RANCHER_CONFIG_ENDPOINT, "API EndPoint",
                "URL of API endpoint (e.g., https://my,rancher.host/v2-beta)", true, null, null, PropertyScope.Instance));

        builder.property(PropertyUtil.string(CONFIG_ENVIRONMENT_IDS, "Environment IDs",
                "List of environments to include, comma-separated", true, null, null, PropertyScope.Instance));

        builder.property(PropertyUtil.string(CONFIG_ACCESSKEY_PATH, "Access Key Storage Path",
                "Path in Rundeck Storage for the Rancher API Access Key (e.g. keys/rancher/access.key)", true,
                "keys/rancher/access.key", null, PropertyScope.Instance));

        builder.property(PropertyUtil.string(CONFIG_SECRETKEY_PATH, "Secret Key Storage Path",
                "Path in Rundeck Storage for the Rancher API Secret Key (e.g. keys/rancher/secret.key)", true,
                "keys/rancher/secret.key", null, PropertyScope.Instance));

        builder.property(PropertyUtil.string(CONFIG_ACCESSKEY, "Access Key", "The Rancher API Access Key",
                true, null, null, null,
                Collections.singletonMap("displayType", StringRenderingConstants.DisplayType.PASSWORD)));

        builder.property(PropertyUtil.string(CONFIG_SECRETKEY, "Secret Key", "The Rancher API Secret Key",
                true, null, null, null,
                Collections.singletonMap("displayType", StringRenderingConstants.DisplayType.PASSWORD)));

        builder.property(PropertyUtil.string(CONFIG_STACK_FILTER, "Stack Filter",
                "A regular expression for stacks to be included", true, "^.*$"));

        builder.property(PropertyUtil.bool(CONFIG_NODE_TYPE_INCLUDE_SERVICE, "Use Services",
                "Create nodes from services", true, "true"));

        builder.property(PropertyUtil.bool(CONFIG_NODE_TYPE_INCLUDE_CONTAINER, "Use Containers",
                "Create nodes from containers", true, "true"));

        builder.property(PropertyUtil.bool(CONFIG_LIMIT_ONE_CONTAINER, "Limit to One Container",
                "Only run on one container for each service", true, "true"));

        builder.property(PropertyUtil.select(CONFIG_HANDLE_STOPPED, "Handle Stopped Containers",
                "Exclude stopped containers", true, OPT_EXCLUDE, Arrays.asList(OPT_EXCLUDE, OPT_INCLUDE)));

        builder.property(PropertyUtil.select(CONFIG_HANDLE_SYSTEM, "Handle System Containers",
                "Exclude system containers", true, OPT_EXCLUDE, Arrays.asList(OPT_EXCLUDE, OPT_INCLUDE)));

        builder.property(PropertyUtil.select(CONFIG_HANDLE_GLOBAL, "Handle Global Containers",
                "Exclude global containers", true, OPT_EXCLUDE, Arrays.asList(OPT_EXCLUDE, OPT_INCLUDE)));

        builder.property(PropertyUtil.string(CONFIG_TAGS, "Tags",
                "Tags to apply to all nodes in this set", false, "rancher"));

        builder.property(PropertyUtil.string(CONFIG_LABELS_INCLUDE_ATTRIBUTES,
                "Labels made into attributes",
                "A regular expression for labels whose values will be used as a node attribute (name will be last part of label ID)",
                false, ""));

        builder.property(PropertyUtil.string(CONFIG_LABELS_INCLUDE_TAGS, "Labels made into tags",
                "A regular expression for labels whose values will be used as tags for a node", false, ""));

        DESC = builder.build();
    }

    @Override
    public ResourceModelSource createResourceModelSource(Properties configuration) throws ConfigurationException {
        return new RancherResourceModelSource(configuration);
    }

    public Description getDescription() {
        return DESC;
    }
}

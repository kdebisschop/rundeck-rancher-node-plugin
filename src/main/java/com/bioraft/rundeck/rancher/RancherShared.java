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

/**
 * RancherWebSocketListener connects to Rancher containers.
 *
 * @author Karl DeBisschop <kdebisschop@gmail.com>
 * @since 2019-12-11
 */

package com.bioraft.rundeck.rancher;

public class RancherShared {

	public static final String PROJ_PROP_PREFIX = "project.";

	public static final String NODE_ATTR_RANCHER_ENDPOINT = "rancher.endpoint";
	public static final String NODE_ATTR_RANCHER_ACCESSKEY_PATH = "rancher.accessKey.path";
	public static final String NODE_ATTR_RANCHER_SECRETKEY_PATH = "rancher.secretKey.path";

	public static final String PROJ_PROP_ENDPOINT = PROJ_PROP_PREFIX + NODE_ATTR_RANCHER_ENDPOINT;
	public static final String PROJ_PROP_ACCESSKEY_PATH = PROJ_PROP_PREFIX + NODE_ATTR_RANCHER_ACCESSKEY_PATH;
	public static final String PROJ_PROP_SECRETKEY_PATH = PROJ_PROP_PREFIX + NODE_ATTR_RANCHER_SECRETKEY_PATH;

	public static final String CONFIG_ENDPOINT = "rancher-api-endpoint";
	public static final String CONFIG_ENVIRONMENT_IDS = "environment-ids";
	public static final String CONFIG_ACCESSKEY = "access-key";
	public static final String CONFIG_SECRETKEY = "secret-key";
	public static final String CONFIG_ACCESSKEY_PATH = "accessKey-storage-path";
	public static final String CONFIG_SECRETKEY_PATH = "secretKey-storage-path";
	public static final String CONFIG_STACK_FILTER = "stack-filter";
	public static final String CONFIG_LIMIT_ONE_CONTAINER = "limit-to-one";
	public static final String CONFIG_HANDLE_STOPPED = "exclude-include-restrict-stopped";
	public static final String CONFIG_HANDLE_SYSTEM = "io-rancher-container-system";
	public static final String CONFIG_HANDLE_GLOBAL = "io-rancher-scheduler-global";
	public static final String CONFIG_TAGS = "tags";
	public static final String CONFIG_LABELS_INCLUDE_ATTRIBUTES = "labels-copied-to-attribs";
	public static final String CONFIG_LABELS_INCLUDE_TAGS = "labels-copied-to-tags";

	public static final String SERVICE_PROVIDER_NAME = "rancher";

}
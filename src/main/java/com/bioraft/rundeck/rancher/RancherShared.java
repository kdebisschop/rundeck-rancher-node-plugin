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

import static com.dtolabs.rundeck.core.plugins.configuration.PropertyResolverFactory.PROJECT_PREFIX;
import static com.dtolabs.rundeck.core.plugins.configuration.PropertyResolverFactory.FRAMEWORK_PREFIX;

/**
 * Shared code and constants for Rancher node.
 *
 * @author Karl DeBisschop <kdebisschop@gmail.com>
 * @since 2019-12-11
 */
public class RancherShared {

    public static final String RANCHER_SERVICE_PROVIDER = "rancher";

    // Resource Model
    public static final String RANCHER_CONFIG_ENDPOINT = "rancher-api-endpoint";
	public static final String CONFIG_ACCESSKEY_PATH = "accessKey-storage-path";
	public static final String CONFIG_SECRETKEY_PATH = "secretKey-storage-path";
    public static final String CONFIG_ENVIRONMENT_IDS = "environment-ids";
    public static final String CONFIG_ACCESSKEY = "access-key";
    public static final String CONFIG_SECRETKEY = "secret-key";
    public static final String CONFIG_STACK_FILTER = "stack-filter";
    public static final String CONFIG_LIMIT_ONE_CONTAINER = "limit-to-one";
    public static final String CONFIG_HANDLE_STOPPED = "exclude-include-restrict-stopped";
    public static final String CONFIG_HANDLE_SYSTEM = "io-rancher-container-system";
    public static final String CONFIG_HANDLE_GLOBAL = "io-rancher-scheduler-global";
    public static final String CONFIG_TAGS = "tags";
    public static final String CONFIG_LABELS_INCLUDE_ATTRIBUTES = "labels-copied-to-attribs";
    public static final String CONFIG_LABELS_INCLUDE_TAGS = "labels-copied-to-tags";

    // Node Executor
    public static final String RANCHER_CONFIG_EXECUTOR_TIMEOUT = "rancher-node-executor-timeout";
	public static final String PROJ_RANCHER_EXECUTOR_TIMEOUT = PROJECT_PREFIX + RANCHER_CONFIG_EXECUTOR_TIMEOUT;
	public static final String FMWK_RANCHER_EXECUTOR_TIMEOUT = FRAMEWORK_PREFIX + RANCHER_CONFIG_EXECUTOR_TIMEOUT;

	// File Copier
    public static final String RANCHER_CONFIG_CLI_PATH = "rancher-cli-path";
	public static final String PROJ_RANCHER_CLI_PATH = PROJECT_PREFIX + RANCHER_CONFIG_CLI_PATH;
	public static final String FMWK_RANCHER_CLI_PATH = FRAMEWORK_PREFIX + RANCHER_CONFIG_CLI_PATH;

    // Step Plugins
    public static final String PROJ_RANCHER_ENDPOINT = PROJECT_PREFIX + RANCHER_CONFIG_ENDPOINT;
    public static final String PROJ_RANCHER_ENVIRONMENT_IDS = PROJECT_PREFIX + RANCHER_SERVICE_PROVIDER + "-" + CONFIG_ENVIRONMENT_IDS;
    public static final String PROJ_RANCHER_ACCESSKEY_PATH = PROJECT_PREFIX + RANCHER_SERVICE_PROVIDER + "-" + CONFIG_ACCESSKEY_PATH;
    public static final String PROJ_RANCHER_SECRETKEY_PATH = PROJECT_PREFIX + RANCHER_SERVICE_PROVIDER + "-" + CONFIG_SECRETKEY_PATH;

    public static String ensureStringIsJsonObject(String string) {
        if (string == null) {
            return "";
        }
        String trimmed = string.replaceFirst("^\\s*\\{?", "{").replaceFirst("\\s*$", "");
        return trimmed + (trimmed.endsWith("}") ? "" : "}");
    }

    public static String ensureStringIsJsonArray(String string) {
        if (string == null) {
            return "";
        }
        String trimmed = string.replaceFirst("^\\s*\\[?", "[").replaceFirst("\\s*$", "");
        return trimmed + (trimmed.endsWith("]") ? "" : "]");
    }
}
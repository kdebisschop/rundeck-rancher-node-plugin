/*
 * Copyright 2020 BioRAFT, Inc. (http://bioraft.com)
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

import static com.dtolabs.rundeck.core.plugins.configuration.PropertyResolverFactory.FRAMEWORK_PREFIX;
import static com.dtolabs.rundeck.core.plugins.configuration.PropertyResolverFactory.PROJECT_PREFIX;

/**
 * Shared code and constants for Rancher node.
 *
 * @author Karl DeBisschop <kdebisschop@gmail.com>
 * @since 2020-02-28
 */

public class Constants {

    private Constants() {
        throw new IllegalStateException("Utility class");
    }

    public static final int INTERVAL_MILLIS = 2000;

    // These are fields in JSON from Rancher API.
    public static final String NODE_ID = "id";
    public static final String NODE_NAME = "name";
    public static final String NODE_ACCOUNT_ID = "accountId";
    public static final String NODE_STATE = "state";
    public static final String NODE_IMAGE_UUID = "imageUuid";

    // These are nodeEntry attributes we define and set.
    public static final String NODE_ATT_ID = "id";
    public static final String NODE_ATT_EXTERNAL_ID = "externalId";
    public static final String NODE_ATT_FILE_COPIER = "file-copier";
    public static final String NODE_ATT_NODE_EXECUTOR = "node-executor";
    public static final String NODE_ATT_TYPE = "type";
    public static final String NODE_ATT_STATE = "state";
    public static final String NODE_ATT_ACCOUNT = "account";
    public static final String NODE_ATT_ENVIRONMENT = "environment";
    public static final String NODE_ATT_IMAGE = "image";
    public static final String NODE_ATT_LINKS = "links";
    public static final String NODE_ATT_ACTIONS = "actions";
    public static final String NODE_ATT_SELF = "self";

    public static final String NODE_LINK_SERVICES = "services";

    public static final String STATE_ACTIVE = "active";
    public static final String STATE_UPGRADED = "upgraded";

    public static final String NODE_ACTION_EXECUTE = "execute";

    public static final String LAUNCH_CONFIG = "launchConfig";
    public static final String START_FIRST = "startFirst";

    public static final String NODE_LABEL_STACK_NAME = "io.rancher.stack.name";
    public static final String NODE_LABEL_STACK_SERVICE_NAME = "io.rancher.stack_service.name";

    public static final String DISPLAY_CODE = "CODE";
    public static final String SYNTAX_MODE_JSON = "json";

    public static final String PATH_PROJECTS = "/projects/";
    public static final String JSON_PAGINATION = "pagination";

    public static final String OPT_DATA_VOLUMES = "dataVolumes";
    public static final String OPT_CONTAINER_PORTS = "ports";
    public static final String OPT_ENV_VARS = "environment";
    public static final String OPT_ENV_IDS = "environmentId";
    public static final String OPT_IMAGE_UUID = "imageUuid";
    public static final String OPT_LABELS = "labels";
    public static final String OPT_SECRETS = "secrets";
    public static final String OPT_SERVICE_NAME = "serviceName";
    public static final String OPT_STACK_NAME = "stackName";

    public static final String OPT_EXCLUDE = "Exclude";
    public static final String OPT_INCLUDE = "Include";

    public static final String RANCHER_SERVICE_PROVIDER = "rancher";
    public static final String RANCHER_SERVICE_CONTROLLER = "rancher-service-controller";

    // Resource Model
    public static final String RANCHER_CONFIG_ENDPOINT = "rancher-api-endpoint";
    public static final String CONFIG_ACCESSKEY_PATH = "accessKey-storage-path";
    public static final String CONFIG_SECRETKEY_PATH = "secretKey-storage-path";
    public static final String CONFIG_ENVIRONMENT_IDS = "environment-ids";
    public static final String CONFIG_ACCESSKEY = "access-key";
    public static final String CONFIG_SECRETKEY = "secret-key";
    public static final String CONFIG_STACK_FILTER = "stack-filter";
    public static final String CONFIG_NODE_TYPE_INCLUDE_SERVICE = "node-type-include-service";
    public static final String CONFIG_NODE_TYPE_INCLUDE_CONTAINER = "node-type-include-container";
    public static final String CONFIG_LIMIT_ONE_CONTAINER = "limit-to-one";
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
    public static final String FMWK_RANCHER_ENDPOINT = FRAMEWORK_PREFIX + RANCHER_CONFIG_ENDPOINT;

    public static final String PROJ_RANCHER_ACCESSKEY_PATH = PROJECT_PREFIX + RANCHER_SERVICE_PROVIDER + "-" + CONFIG_ACCESSKEY_PATH;
    public static final String FMWK_RANCHER_ACCESSKEY_PATH = FRAMEWORK_PREFIX + RANCHER_SERVICE_PROVIDER + "-" + CONFIG_ACCESSKEY_PATH;

    public static final String PROJ_RANCHER_SECRETKEY_PATH = PROJECT_PREFIX + RANCHER_SERVICE_PROVIDER + "-" + CONFIG_SECRETKEY_PATH;
    public static final String FMWK_RANCHER_SECRETKEY_PATH = FRAMEWORK_PREFIX + RANCHER_SERVICE_PROVIDER + "-" + CONFIG_SECRETKEY_PATH;

    public static final String PROJ_RANCHER_ENVIRONMENT_IDS = PROJECT_PREFIX + RANCHER_SERVICE_PROVIDER + "-" + CONFIG_ENVIRONMENT_IDS;
}

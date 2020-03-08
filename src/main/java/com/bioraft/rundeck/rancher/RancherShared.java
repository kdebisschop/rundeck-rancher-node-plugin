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

import com.dtolabs.rundeck.core.execution.ExecutionContext;
import com.dtolabs.rundeck.core.execution.workflow.steps.FailureReason;
import com.dtolabs.rundeck.core.storage.ResourceMeta;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.dtolabs.rundeck.core.plugins.configuration.PropertyResolverFactory.FRAMEWORK_PREFIX;
import static com.dtolabs.rundeck.core.plugins.configuration.PropertyResolverFactory.PROJECT_PREFIX;

/**
 * Shared code and constants for Rancher node.
 *
 * @author Karl DeBisschop <kdebisschop@gmail.com>
 * @since 2019-12-11
 */
public class RancherShared {

    private RancherShared() {
        throw new IllegalStateException("Utility class");
    }

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
    public static final String FMWK_RANCHER_ENDPOINT = FRAMEWORK_PREFIX + RANCHER_CONFIG_ENDPOINT;

    public static final String PROJ_RANCHER_ACCESSKEY_PATH = PROJECT_PREFIX + RANCHER_SERVICE_PROVIDER + "-" + CONFIG_ACCESSKEY_PATH;
    public static final String FMWK_RANCHER_ACCESSKEY_PATH = FRAMEWORK_PREFIX + RANCHER_SERVICE_PROVIDER + "-" + CONFIG_ACCESSKEY_PATH;

    public static final String PROJ_RANCHER_SECRETKEY_PATH = PROJECT_PREFIX + RANCHER_SERVICE_PROVIDER + "-" + CONFIG_SECRETKEY_PATH;
    public static final String FMWK_RANCHER_SECRETKEY_PATH = FRAMEWORK_PREFIX + RANCHER_SERVICE_PROVIDER + "-" + CONFIG_SECRETKEY_PATH;

    public static final String PROJ_RANCHER_ENVIRONMENT_IDS = PROJECT_PREFIX + RANCHER_SERVICE_PROVIDER + "-" + CONFIG_ENVIRONMENT_IDS;

    public static String ensureStringIsJsonObject(String string) {
        if (string == null) {
            return "";
        }
        string = string.trim();
        if (string.isEmpty()) {
            return "";
        }
        return (string.startsWith("{") ? "" : "{") + string + (string.endsWith("}") ? "" : "}");
    }

    public static String ensureStringIsJsonArray(String string) {
        if (string == null) {
            return "";
        }
        string = string.trim();
        if (string.isEmpty()) {
            return "";
        }
        return (string.startsWith("[") ? "" : "[") + string + (string.endsWith("]") ? "" : "]");
    }

    public static String apiPath(String environmentId, String target) {
        return "/projects/" + environmentId + target;
    }

    /**
     * Get a (secret) value from password storage.
     *
     * @param context             The current plugin execution context.
     * @param passwordStoragePath The path to look up in storage.
     * @return The requested secret or password.
     * @throws IOException When there is an IO Exception writing to stream.
     */
    public static String loadStoragePathData(final ExecutionContext context, final String passwordStoragePath) throws IOException {
        if (null == passwordStoragePath) {
            throw new IOException("Storage path is not defined.");
        }
        ResourceMeta contents = context.getStorageTree().getResource(passwordStoragePath).getContents();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        contents.writeContent(byteArrayOutputStream);
        return new String(byteArrayOutputStream.toByteArray());
    }

    /**
     * Builds a JsonNode object for insertion into the secrets array.
     *
     * @param secretId A secret ID from Rancher (like "1se1")
     * @return JSON expression for secret reference.
     */
    public static JsonNode buildSecret(String secretId) {
        return (new ObjectMapper()).valueToTree(secretJsonMap(secretId));
    }

    public static Map<String, String> secretJsonMap(String secretId) {
        HashMap<String, String> map = new HashMap<>();
        map.put("type", "secretReference");
        map.put("uid", "0");
        map.put("gid", "0");
        map.put("mode", "444");
        map.put("name", "");
        map.put("secretId", secretId);
        return map;
    }

    public static String mountPoint(String mountSpec) {
        return mountSpec.replaceFirst("[^:]+:", "").replaceFirst(":.*", "");
    }

    public enum ErrorCause implements FailureReason {
        INVALID_CONFIGURATION,
        INVALID_JSON,
        IO_EXCEPTION,
        ACTION_FAILED,
        ACTION_NOT_SUPPORTED,
        NO_SERVICE_OBJECT,
        SERVICE_NOT_RUNNING,
        MISSING_ACTION_URL,
        MISSING_UPGRADE_URL,
        NO_UPGRADE_DATA,
        UPGRADE_FAILURE,
        INTERRUPTED,
        INVALID_STACK_NAME,
        INVALID_ENVIRONMENT_NAME
    }
}
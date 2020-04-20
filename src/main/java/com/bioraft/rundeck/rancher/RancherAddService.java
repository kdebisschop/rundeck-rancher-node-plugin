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

import com.dtolabs.rundeck.core.common.Framework;
import com.dtolabs.rundeck.core.execution.workflow.steps.StepException;
import com.dtolabs.rundeck.core.plugins.Plugin;
import com.dtolabs.rundeck.plugins.PluginLogger;
import com.dtolabs.rundeck.plugins.ServiceNameConstants;
import com.dtolabs.rundeck.plugins.descriptions.*;
import com.dtolabs.rundeck.plugins.step.PluginStepContext;
import com.dtolabs.rundeck.plugins.step.StepPlugin;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.bioraft.rundeck.rancher.Constants.*;
import static com.bioraft.rundeck.rancher.Errors.ErrorCause.*;
import static com.dtolabs.rundeck.core.Constants.*;
import static com.dtolabs.rundeck.core.plugins.configuration.PropertyResolverFactory.FRAMEWORK_PREFIX;
import static com.dtolabs.rundeck.core.plugins.configuration.PropertyResolverFactory.PROJECT_PREFIX;
import static com.dtolabs.rundeck.core.plugins.configuration.StringRenderingConstants.CODE_SYNTAX_MODE;
import static com.dtolabs.rundeck.core.plugins.configuration.StringRenderingConstants.DISPLAY_TYPE_KEY;
import static org.apache.commons.lang.StringUtils.defaultString;

@Plugin(name = RancherAddService.SERVICE_PROVIDER_NAME, service = ServiceNameConstants.WorkflowStep)
@PluginDescription(title = "Rancher - Create New Service", description = "Creates a new service in a rancher stack.")
public class RancherAddService implements StepPlugin {
    public static final String SERVICE_PROVIDER_NAME = "com.bioraft.rundeck.rancher.RancherAddService";

    @PluginProperty(name = PROJ_RANCHER_ENVIRONMENT_IDS, title = "Environment ID", description = "The ID of the environment to create the stack in", required = true)
    String environmentId;

    @PluginProperty(title = "Stack Name", description = "The name of the stack", required = true)
    private String stackName;

    @PluginProperty(title = "Service Name", description = "The name of the new service", required = true)
    private String serviceName;

    @PluginProperty(title = "Docker Image", description = "The image to deploy", required = true)
    private String imageUuid;

    @PluginProperty(title = "Data Volumes", description = "JSON array Lines of \"source:mountPoint\"")
    @RenderingOption(key = DISPLAY_TYPE_KEY, value = DISPLAY_CODE)
    @RenderingOption(key = CODE_SYNTAX_MODE, value = SYNTAX_MODE_JSON)
    private String dataVolumes;

    @PluginProperty(title = "Container OS Environment", description = "JSON object of \"variable\": \"value\"")
    @RenderingOption(key = DISPLAY_TYPE_KEY, value = DISPLAY_CODE)
    @RenderingOption(key = CODE_SYNTAX_MODE, value = SYNTAX_MODE_JSON)
    private String environment;

    @PluginProperty(title = "Service Labels", description = "JSON object of \"variable\": \"value\"")
    @RenderingOption(key = DISPLAY_TYPE_KEY, value = DISPLAY_CODE)
    @RenderingOption(key = CODE_SYNTAX_MODE, value = SYNTAX_MODE_JSON)
    private String labels;

    @PluginProperty(title = "Secret IDs", description = "List of secrets IDs, space or comma separated")
    private String secrets;

    private HttpClient client;

    public RancherAddService () {
        this.client = new HttpClient();
    }

    public RancherAddService (HttpClient client) {
        this.client = client;
    }

    @Override
    public void executeStep(final PluginStepContext context, final Map<String, Object> configuration) throws
            StepException {

        stackName = (String) configuration.getOrDefault(OPT_STACK_NAME, defaultString(stackName));
        if (stackName.isEmpty()) {
            throw new StepException("Stack name cannot be empty", INVALID_STACK_NAME);
        }

        environmentId = (String) configuration.getOrDefault(OPT_ENV_IDS, defaultString(environmentId));
        if (environmentId.isEmpty()) {
            throw new StepException("Environment cannot be empty", INVALID_ENVIRONMENT_NAME);
        }

        serviceName = (String) configuration.getOrDefault(OPT_SERVICE_NAME, defaultString(serviceName));
        if (serviceName.isEmpty()) {
            throw new StepException("Service Name cannot be empty", INVALID_CONFIGURATION);
        }

        imageUuid = (String) configuration.getOrDefault(OPT_IMAGE_UUID, defaultString(imageUuid));
        if (imageUuid.isEmpty()) {
            throw new StepException("Image UUID cannot be empty", INVALID_CONFIGURATION);
        }

        dataVolumes = (String) configuration.getOrDefault(OPT_DATA_VOLUMES, defaultString(dataVolumes));

        environment = (String) configuration.getOrDefault(OPT_ENV_VARS, defaultString(environment));

        labels = (String) configuration.getOrDefault(OPT_LABELS, defaultString(labels));

        secrets = (String) configuration.getOrDefault(OPT_SECRETS, defaultString(secrets));

        Framework framework = context.getFramework();
        String project = context.getFrameworkProject();
        PluginLogger logger = context.getLogger();
        client.setLogger(logger);

        String endpoint = cfgFromProjectOrFramework(framework, project, RANCHER_CONFIG_ENDPOINT);
        String spec = endpoint + (new Strings()).apiPath(environmentId, "/services");

        try {
            Storage storage = new Storage(context.getExecutionContext());
            String accessKeyPath = cfgFromRancherProjectOrFramework(framework, project, CONFIG_ACCESSKEY_PATH);
            client.setAccessKey(storage.loadStoragePathData(accessKeyPath));
            String secretKeyPath = cfgFromRancherProjectOrFramework(framework, project, CONFIG_SECRETKEY_PATH);
            client.setSecretKey(storage.loadStoragePathData(secretKeyPath));
        } catch (IOException e) {
            throw new StepException("Could not get secret storage path", e, IO_EXCEPTION);
        }

        ImmutableMap.Builder<String, Object> mapBuilder = ImmutableMap.builder();
        mapBuilder.put("type", "launchConfig");
        mapBuilder.put(OPT_IMAGE_UUID, imageUuid);
        mapBuilder.put("kind", "container");
        mapBuilder.put("networkMode", "managed");

        addJsonData(OPT_DATA_VOLUMES, (new Strings()).ensureStringIsJsonArray(dataVolumes), mapBuilder);
        addJsonData(OPT_ENV_VARS, (new Strings()).ensureStringIsJsonObject(environment), mapBuilder);
        addJsonData(OPT_LABELS, (new Strings()).ensureStringIsJsonObject(labels), mapBuilder);
        addSecrets(mapBuilder);

        JsonNode check;
        String stackCheck;
        String stackId;
        try {
            // First look for a stack with the designated ID.
            stackCheck = endpoint + (new Strings()).apiPath(environmentId, "/stacks/" + stackName);
            logger.log(INFO_LEVEL, "Looking for " + stackCheck);
            check = client.get(stackCheck);
            if (check.path("type").asText().equals("error")) {
                throw new IOException();
            } else {
                stackId = stackName;
            }
        } catch (IOException e) {
            stackId = stackId(stackName, endpoint, logger);
        }
        if (stackId == null) {
            throw new StepException("Stack does not exist: " + stackName, INVALID_CONFIGURATION);
        }

        ObjectMapper mapper = new ObjectMapper();
        try {
            Map<String, Object> map = ImmutableMap.<String, Object>builder().put("type", "service")
                    .put("assignServiceIpAddress", false).put("startOnCreate", true).put("name", serviceName)
                    .put("scale", 1).put("serviceIndexStrategy", "deploymentUnitBased")
                    .put("launchConfig", mapBuilder.build())
                    .put("stackId", stackId).build();
            String payload = mapper.writeValueAsString(map);
            logger.log(DEBUG_LEVEL, mapper.readTree(payload).toPrettyString());
            JsonNode serviceResult = client.post(spec, map);
            logger.log(INFO_LEVEL, "Success!");
            logger.log(INFO_LEVEL, "New service ID:" + serviceResult.path("id").asText());
            logger.log(INFO_LEVEL, "New service name:" + serviceResult.path("name").asText());
        } catch (IOException e) {
            throw new StepException("Failed at " + spec + "\n" + e.getMessage(), e, INVALID_CONFIGURATION);
        }
    }

    private String cfgFromProjectOrFramework(Framework framework, String project, String field) {
        String config = framework.getProjectProperty(project, PROJECT_PREFIX + field);
        if (config == null) {
            config = framework.getProperty(FRAMEWORK_PREFIX + field);
        }
        return config;
    }

    private String cfgFromRancherProjectOrFramework(Framework framework, String project, String field) {
        return cfgFromProjectOrFramework(framework, project, RANCHER_SERVICE_PROVIDER + "-" + field);
    }

    private String stackId(String stackName, String endpoint, PluginLogger logger) throws StepException {
        try {
            String stackCheck = endpoint + (new Strings()).apiPath(environmentId, "/stacks?name=" + stackName);
            logger.log(INFO_LEVEL, "Looking for " + stackCheck);
            JsonNode check = client.get(stackCheck);
            if (check.path("data").has(0)) {
                return check.path("data").get(0).path("id").asText();
            } else {
                logger.log(ERR_LEVEL, "FATAL: no stack `" + stackName + "` was found.");
                throw new StepException("Stack does not exist", INVALID_CONFIGURATION);
            }
        } catch (IOException ex) {
            logger.log(ERR_LEVEL, "FATAL: no stack `" + stackName + "` was found.");
            throw new StepException("Stack does not exist", INVALID_CONFIGURATION);
        }
    }

    private void addJsonData(String name, String data, ImmutableMap.Builder<String, Object> builder) throws StepException {
        if (data.isEmpty()) {
            return;
        }

        ObjectMapper objectMapper = new ObjectMapper();
        try {
            JsonNode map = objectMapper.readTree(data);
            builder.put(name, map);
        } catch (JsonProcessingException e) {
            throw new StepException("Could not parse JSON for " + name + "\n" + data, e, INVALID_CONFIGURATION);
        }
    }

    private void addSecrets(ImmutableMap.Builder<String, Object> mapBuilder) {
        if (secrets != null && secrets.trim().length() > 0) {
            // Add in the new or replacement secrets specified in the step.
            List<Map<String, String>> secretsArray = new ArrayList<>();
            for (String secretId : secrets.split("[,; ]+")) {
                secretsArray.add((new Strings()).secretJsonMap(secretId));
            }
            mapBuilder.put(OPT_SECRETS, (new ObjectMapper()).valueToTree(secretsArray));
        }
    }
}

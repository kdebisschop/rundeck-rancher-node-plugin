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
import com.dtolabs.rundeck.plugins.descriptions.PluginDescription;
import com.dtolabs.rundeck.plugins.descriptions.PluginProperty;
import com.dtolabs.rundeck.plugins.step.PluginStepContext;
import com.dtolabs.rundeck.plugins.step.StepPlugin;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;

import java.io.IOException;
import java.util.Map;

import static com.bioraft.rundeck.rancher.Constants.*;
import static com.bioraft.rundeck.rancher.Errors.ErrorCause.*;
import static com.dtolabs.rundeck.core.Constants.ERR_LEVEL;
import static com.dtolabs.rundeck.core.Constants.INFO_LEVEL;
import static org.apache.commons.lang.StringUtils.defaultString;

@Plugin(name = RancherNewStack.SERVICE_PROVIDER_NAME, service = ServiceNameConstants.WorkflowStep)
@PluginDescription(title = "Rancher - Create New Stack", description = "Creates a new stack in rancher.")
public class RancherNewStack implements StepPlugin {
    public static final String SERVICE_PROVIDER_NAME = "com.bioraft.rundeck.rancher.RancherNewStack";

    @PluginProperty(title = "Stack Name", description = "The name of the new stack", required = true)
    private String stackName;

    @PluginProperty(name = CONFIG_ENVIRONMENT_IDS, title = "Environment ID", description = "The ID of the environment to create the stack in", required = true)
    String environmentId;

    final HttpClient client;

    public RancherNewStack () {
        client = new HttpClient();
    }

    public RancherNewStack (HttpClient client) {
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

        Framework framework = context.getFramework();
        String project = context.getFrameworkProject();
        PluginLogger logger = context.getLogger();
        String endpoint = framework.getProjectProperty(project, PROJ_RANCHER_ENDPOINT);
        if (endpoint == null) {
            endpoint = framework.getProperty(FMWK_RANCHER_ENDPOINT);
        }
        String accessKeyPath = framework.getProjectProperty(project, PROJ_RANCHER_ACCESSKEY_PATH);
        if (accessKeyPath == null) {
            accessKeyPath = framework.getProperty(FMWK_RANCHER_ACCESSKEY_PATH);
        }
        String secretKeyPath = framework.getProjectProperty(project, PROJ_RANCHER_SECRETKEY_PATH);
        if (secretKeyPath == null) {
            secretKeyPath = framework.getProperty(FMWK_RANCHER_SECRETKEY_PATH);
        }

        String spec = endpoint + "/projects/" + environmentId + "/stacks/";
        try {
            Storage storage = new Storage(context.getExecutionContext());
            String accessKey = storage.loadStoragePathData(accessKeyPath);
            client.setAccessKey(accessKey);
            String secretKey = storage.loadStoragePathData(secretKeyPath);
            client.setSecretKey(secretKey);
        } catch (IOException e) {
            throw new StepException("Could not get secret storage path", e, IO_EXCEPTION);
        }

        try {
            JsonNode check = client.get(spec, ImmutableMap.<String, String>builder().put("name", stackName).build());
            if (check.path("data").has(0)) {
                logger.log(ERR_LEVEL, "FATAL: A stack with the name " + stackName + " already exists.");
                throw new StepException("Stack already exists", INVALID_CONFIGURATION);
            }
        } catch (IOException e) {
            throw new StepException("Failed posting to " + spec, e, IO_EXCEPTION);
        }

        try {
            Map<String, Object> map = ImmutableMap.<String, Object>builder()
                    .put("name", stackName).put("system", false)
                    .put("dockerCompose", "").put("rancherCompose", "").build();
            JsonNode stackResult = client.post(spec, map);
            logger.log(INFO_LEVEL, "Success!");
            logger.log(INFO_LEVEL, "New stack ID:" + stackResult.path("id").asText());
            logger.log(INFO_LEVEL, "New stack name:" + stackResult.path("name").asText());
        } catch (IOException e) {
            throw new StepException("Failed posting to " + spec, e, INVALID_CONFIGURATION);
        }
    }
}

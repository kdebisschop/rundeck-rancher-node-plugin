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
import com.dtolabs.rundeck.core.execution.ExecutionContext;
import com.dtolabs.rundeck.core.execution.workflow.steps.FailureReason;
import com.dtolabs.rundeck.core.execution.workflow.steps.StepException;
import com.dtolabs.rundeck.core.plugins.Plugin;
import com.dtolabs.rundeck.core.storage.ResourceMeta;
import com.dtolabs.rundeck.plugins.PluginLogger;
import com.dtolabs.rundeck.plugins.ServiceNameConstants;
import com.dtolabs.rundeck.plugins.descriptions.*;
import com.dtolabs.rundeck.plugins.step.PluginStepContext;
import com.dtolabs.rundeck.plugins.step.StepPlugin;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import static com.bioraft.rundeck.rancher.RancherShared.*;
import static com.dtolabs.rundeck.core.Constants.ERR_LEVEL;
import static com.dtolabs.rundeck.core.Constants.INFO_LEVEL;
import static com.dtolabs.rundeck.core.plugins.configuration.StringRenderingConstants.CODE_SYNTAX_MODE;
import static com.dtolabs.rundeck.core.plugins.configuration.StringRenderingConstants.DISPLAY_TYPE_KEY;

@Plugin(name = RancherAddService.SERVICE_PROVIDER_NAME, service = ServiceNameConstants.WorkflowStep)
@PluginDescription(title = "Rancher - Create New Service", description = "Creates a new service in a rancher stack.")
public class RancherAddService implements StepPlugin {
    public static final String SERVICE_PROVIDER_NAME = "com.bioraft.rundeck.rancher.RancherAddService";

    @PluginProperty(title = "Stack Name", description = "The name of the stack", required = true)
    private String stackName;

    @PluginProperty(title = "Service Name", description = "The name of the new service", required = true)
    private String serviceName;

    @PluginProperty(title = "Docker Image", description = "The image to deploy", required = true)
    private String imageUuid;

    @PluginProperty(title = "Data Volumes", description = "JSON array Lines of \"source:mountPoint\"")
    @RenderingOptions({
            @RenderingOption(key = DISPLAY_TYPE_KEY, value = "CODE"),
            @RenderingOption(key = CODE_SYNTAX_MODE, value = "json"),
    })
    private String dataVolumes;

    @PluginProperty(title = "Container OS Environment", description = "JSON object of \"variable\": \"value\"")
    @RenderingOptions({
            @RenderingOption(key = DISPLAY_TYPE_KEY, value = "CODE"),
            @RenderingOption(key = CODE_SYNTAX_MODE, value = "json"),
    })
    private String environment;

    @PluginProperty(title = "Service Labels", description = "JSON object of \"variable\": \"value\"")
    @RenderingOptions({
            @RenderingOption(key = DISPLAY_TYPE_KEY, value = "CODE"),
            @RenderingOption(key = CODE_SYNTAX_MODE, value = "json"),
    })
    private String labels;

    @PluginProperty(title = "Secret IDs", description = "List of secrets IDs, space or comma separated")
    private String secrets;

    @PluginProperty(name = PROJ_RANCHER_ENVIRONMENT_IDS, title = "Environment ID", description = "The ID of the environment to create the stack in", required = true)
    String environmentId;

    Map<String, Object> configuration;

    @Override
    public void executeStep(final PluginStepContext context, final Map<String, Object> configuration) throws
            StepException {

        if ((stackName == null || stackName.isEmpty()) && configuration.containsKey("stackName")) {
            stackName = (String) configuration.get("stackName");
        }
        if (stackName == null || stackName.isEmpty()) {
            throw new StepException("Stack Name cannot be empty", Cause.InvalidConfiguration);
        }

        if ((serviceName == null || serviceName.isEmpty()) && configuration.containsKey("serviceName")) {
            serviceName = (String) configuration.get("serviceName");
        }
        if (serviceName == null || serviceName.isEmpty()) {
            throw new StepException("Service Name cannot be empty", Cause.InvalidConfiguration);
        }

        if ((imageUuid == null || imageUuid.isEmpty()) && configuration.containsKey("imageUuid")) {
            imageUuid = (String) configuration.get("imageUuid");
        }
        if (imageUuid == null || imageUuid.isEmpty()) {
            throw new StepException("Image UUID cannot be empty", Cause.InvalidConfiguration);
        }


        if ((environment == null || environment.isEmpty()) && configuration.containsKey("environment")) {
            environment = (String) configuration.get("environment");
        }

        if ((labels == null || labels.isEmpty()) && configuration.containsKey("labels")) {
            labels = (String) configuration.get("labels");
        }

        if ((secrets == null || secrets.isEmpty()) && configuration.containsKey("secrets")) {
            secrets = (String) configuration.get("secrets");
        }

        ObjectMapper objectMapper = new ObjectMapper();
        Framework framework = context.getFramework();
        String project = context.getFrameworkProject();
        PluginLogger logger = context.getLogger();
        String endpoint = framework.getProjectProperty(project, PROJ_RANCHER_ENDPOINT);
        String accessKeyPath = framework.getProjectProperty(project, PROJ_RANCHER_ACCESSKEY_PATH);
        String secretKeyPath = framework.getProjectProperty(project, PROJ_RANCHER_SECRETKEY_PATH);

        String spec = endpoint + "/projects/" + environmentId + "/services";
        String accessKey = loadStoragePathData(context.getExecutionContext(), accessKeyPath);
        String secretKey = loadStoragePathData(context.getExecutionContext(), secretKeyPath);

        HttpClient client = new HttpClient(accessKey, secretKey);

        ImmutableMap.Builder<String, Object> mapBuilder = ImmutableMap.builder();
        mapBuilder.put("type", "launchConfig");
        mapBuilder.put("imageUuid", imageUuid);
        mapBuilder.put("kind", "container");
        mapBuilder.put("networkMode", "managed");

        addJsonData("dataVolumes", ensureStringIsJsonArray(dataVolumes), mapBuilder);
        addJsonData("environment", ensureStringIsJsonObject(environment), mapBuilder);
        addJsonData("labels", ensureStringIsJsonObject(labels), mapBuilder);

        JsonNode check;
        String stackCheck;
        try {
            stackCheck = endpoint + "/projects/" + environmentId + "/stacks/" + stackName;
            logger.log(INFO_LEVEL, "Looking for `" + stackCheck);
            check = client.get(stackCheck);
//            if (check.has("error")) {
//                stackCheck = endpoint + "/projects/" + environmentId + "/stacks";
//                logger.log(INFO_LEVEL, "Looking for `" + stackCheck);
//                check = client.get(stackCheck, ImmutableMap.<String, String>builder().put("name", stackName).build());
//                if (check.path("data").elements().hasNext()) {
//                    stackName = check.path("data").elements().next().path("id").asText();
//                }
//            }
        } catch (IOException e) {
            try {
                stackCheck = endpoint + "/projects/" + environmentId + "/stacks";
                logger.log(INFO_LEVEL, "Looking for `" + stackCheck);
                check = client.get(stackCheck, ImmutableMap.<String, String>builder().put("name", stackName).build());
                if (check.path("data").elements().hasNext()) {
                    stackName = check.path("data").elements().next().path("id").asText();
                }
            } catch (IOException ex) {
                logger.log(ERR_LEVEL, "FATAL: no stack `" + stackName + "` was found.");
                throw new StepException("Stack does not exist", Cause.InvalidConfiguration);
            }
        }

        try {
            Map<String, Object> map = ImmutableMap.<String, Object>builder()
                    .put("assignServiceIpAddress", false).put("startOnCreate", true).put("name", serviceName)
                    .put("stackId", stackName).put("rancherCompose", "").put("launchConfig", mapBuilder.build()).build();
            JsonNode stackResult = client.post(spec, map);
            logger.log(INFO_LEVEL, "Success!");
            logger.log(INFO_LEVEL, "New stack ID:" + stackResult.path("id").asText());
            logger.log(INFO_LEVEL, "New stack name:" + stackResult.path("name").asText());
        } catch (IOException e) {
            throw new StepException("Failed posting to " + spec, e, Cause.InvalidConfiguration);
        }
    }

    private void addJsonData(String name, String data, ImmutableMap.Builder<String, Object> builder) throws StepException {
        if ((data == null || data.isEmpty()) && configuration.containsKey(name)) {
            data = (String) configuration.get(name);
        }

        ObjectMapper objectMapper = new ObjectMapper();
        if ((data == null || data.isEmpty()) && configuration.containsKey(name)) {
            data = (String) configuration.get(name);
        }
        if (data != null && !data.isEmpty()) {
            try {
                JsonNode map = objectMapper.readTree(data);
                builder.put(name, map);
            } catch (JsonProcessingException e) {
                throw new StepException("Could not parse JSON for " + name, e, Cause.InvalidConfiguration);
            }
        }
    }

    public enum Cause implements FailureReason {
        InvalidConfiguration,
        InvalidStackName,
        IOException
    }

    /**
     * Get a (secret) value from password storage.
     *
     * @param context             The current plugin execution context.
     * @param passwordStoragePath The path to look up in storage.
     * @return The requested secret or password.
     * @throws StepException When there is an IO Exception writing to stream.
     */
    private String loadStoragePathData(final ExecutionContext context, final String passwordStoragePath) throws StepException {
        if (null == passwordStoragePath) {
            return null;
        }
        ResourceMeta contents = context.getStorageTree().getResource(passwordStoragePath).getContents();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            contents.writeContent(byteArrayOutputStream);
        } catch (IOException e) {
            throw new StepException("Could not get " + passwordStoragePath, e, Cause.IOException);
        }
        return new String(byteArrayOutputStream.toByteArray());
    }
}

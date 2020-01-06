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

import com.dtolabs.rundeck.core.execution.workflow.steps.FailureReason;
import com.dtolabs.rundeck.core.execution.workflow.steps.StepException;
import com.dtolabs.rundeck.core.plugins.Plugin;
import com.dtolabs.rundeck.plugins.ServiceNameConstants;
import com.dtolabs.rundeck.plugins.descriptions.PluginProperty;
import com.dtolabs.rundeck.plugins.step.PluginStepContext;
import com.dtolabs.rundeck.plugins.step.StepPlugin;

import io.rancher.Rancher;
import io.rancher.service.*;
import io.rancher.type.*;
import retrofit2.Response;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

@Plugin(name = RancherNewStack.SERVICE_PROVIDER_NAME, service = ServiceNameConstants.WorkflowStep)
public class RancherNewStack implements StepPlugin {
    public static final String SERVICE_PROVIDER_NAME = "com.bioraft.rundeck.rancher.RancherNewStack";

    @PluginProperty(title = "Stack Name", description = "The name of the new stack")
    private String stackName;

    public void executeStep(final PluginStepContext context, final Map<String, Object> configuration) throws
            StepException {
        if (stackName == null || stackName.isEmpty()) {
            stackName = (String) configuration.get("stack");
        }
        if (stackName == null || stackName.isEmpty()) {
            throw new StepException("Stack name cannot be empty", RancherNewStackFailureReason.InvalidStackName);
        }

        Rancher.Config config = null;
        try {
            config = new Rancher.Config(new URL("https://rancher.mydomain.com/v2-beta/"), "MyAPIAccessKey", "MyAPISecretKey");
        } catch (MalformedURLException e) {
            throw new StepException("Could not access Rancher", e, RancherNewStackFailureReason.MalformedURLException);
        }
        Rancher rancher = new Rancher(config);
        StackService stackService = rancher.type(StackService.class);
        Stack stack = new Stack();
        stack.setName(stackName);
        try {
            Response<Stack> stackResult = stackService.create(stack).execute();
        } catch (IOException e) {
            throw new StepException("Rancher lost connection", e, RancherNewStackFailureReason.IOException);
        }
    }

    public enum RancherNewStackFailureReason implements FailureReason {
        InvalidStackName,
        MalformedURLException,
        IOException
    }
}
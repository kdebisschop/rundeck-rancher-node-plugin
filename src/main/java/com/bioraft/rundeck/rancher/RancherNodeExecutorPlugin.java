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

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

import com.dtolabs.rundeck.core.common.INodeEntry;
import com.dtolabs.rundeck.core.execution.ExecutionContext;
import com.dtolabs.rundeck.core.execution.ExecutionListener;
import com.dtolabs.rundeck.core.execution.service.NodeExecutor;
import com.dtolabs.rundeck.core.execution.service.NodeExecutorResult;
import com.dtolabs.rundeck.core.execution.service.NodeExecutorResultImpl;
import com.dtolabs.rundeck.core.execution.utils.ResolverUtil;
import com.dtolabs.rundeck.core.execution.workflow.steps.StepFailureReason;
import com.dtolabs.rundeck.core.plugins.Plugin;
import com.dtolabs.rundeck.core.plugins.configuration.*;
import com.dtolabs.rundeck.plugins.ServiceNameConstants;
import com.dtolabs.rundeck.plugins.util.DescriptionBuilder;

import static com.dtolabs.rundeck.core.Constants.DEBUG_LEVEL;
import static com.bioraft.rundeck.rancher.Constants.*;

/**
 * RancherNodeExecutorPlugin is a {@link NodeExecutor} plugin implementation for
 * Rancher.
 *
 * @author Karl DeBisschop <kdebisschop@gmail.com>
 * @since 2019-12-08
 */
@Plugin(name = RANCHER_SERVICE_PROVIDER, service = ServiceNameConstants.NodeExecutor)
public class RancherNodeExecutorPlugin implements NodeExecutor, Describable {

    static final Description DESC;

    private String accessKey;
    private String secretKey;

    static {
        DescriptionBuilder builder = DescriptionBuilder.builder();
        builder.name(RANCHER_SERVICE_PROVIDER);
        builder.title("Rancher Node Executor");
        builder.description("Executes a command on a remote rancher node.");

        builder.property(PropertyUtil.integer(RANCHER_CONFIG_EXECUTOR_TIMEOUT, "Maximum execution time",
                "Terminate execution after specified number of seconds", true, "300"));

		builder.mapping(RANCHER_CONFIG_EXECUTOR_TIMEOUT, PROJ_RANCHER_EXECUTOR_TIMEOUT);
		builder.frameworkMapping(RANCHER_CONFIG_EXECUTOR_TIMEOUT, FMWK_RANCHER_EXECUTOR_TIMEOUT);

        DESC = builder.build();
    }

    @Override
    public Description getDescription() {
        return DESC;
    }

    @Override
    public NodeExecutorResult executeCommand(final ExecutionContext context, final String[] command,
                                             final INodeEntry node) {
        Map<String, String> nodeAttributes = node.getAttributes();

        if (nodeAttributes.get("type").equals("service")) {
            String message = "Node executor is not currently supported for services";
            return NodeExecutorResultImpl.createFailure(StepFailureReason.PluginFailed, message, node);
        }

        try {
            Storage storage = new Storage(context);
            accessKey = storage.loadStoragePathData(nodeAttributes.get(CONFIG_ACCESSKEY_PATH));
            secretKey = storage.loadStoragePathData(nodeAttributes.get(CONFIG_SECRETKEY_PATH));
        } catch (IOException e) {
            return NodeExecutorResultImpl.createFailure(StepFailureReason.IOFailure, e.getMessage(), node);
        }

        ExecutionListener listener = context.getExecutionListener();

        String url = nodeAttributes.get("execute");

        Map<String, String> jobContext = context.getDataContext().get("job");
        String temp = this.baseName(command, jobContext);

        int timeout = ResolverUtil.resolveIntProperty(RANCHER_CONFIG_EXECUTOR_TIMEOUT, 300, node,
                context.getFramework().getFrameworkProjectMgr().getFrameworkProject(context.getFrameworkProject()),
                context.getFramework());
        try {
            context.getExecutionLogger().log(DEBUG_LEVEL, "Running " + String.join(" ", command));
            RancherWebSocketListener.runJob(url, accessKey, secretKey, command, listener, temp, timeout);
            context.getExecutionLogger().log(DEBUG_LEVEL, "Ran " + String.join(" ", command));
        } catch (IOException e) {
            return NodeExecutorResultImpl.createFailure(StepFailureReason.IOFailure, e.getMessage(), node);
        } catch (InterruptedException e) {
            return NodeExecutorResultImpl.createFailure(StepFailureReason.Interrupted, e.getMessage(), node);
        }

        String[] pidFile;
        try {
            String file = temp + ".pid";
            context.getExecutionLogger().log(DEBUG_LEVEL, "Reading '" + file + "' on " + url);
            pidFile = this.readLogFile(file, url).split(" +");
        } catch (IOException e) {
            return NodeExecutorResultImpl.createFailure(StepFailureReason.IOFailure, e.getMessage(), node);
        } catch (InterruptedException e) {
            return NodeExecutorResultImpl.createFailure(StepFailureReason.Interrupted, e.getMessage(), node);
        }
        if (pidFile.length > 1 && Integer.parseInt(pidFile[1]) == 0) {
            return NodeExecutorResultImpl.createSuccess(node);
        } else {
            return NodeExecutorResultImpl.createFailure(StepFailureReason.PluginFailed,
                    "Process " + pidFile[0] + " status " + pidFile[1], node);
        }
    }

    /**
     * Create a unique file path without the extension.
     *
     * @param command    The command array to be executed for the job.
     * @param jobContext The job context map.
     * @return A unique filename for the PID and status of this step.
     */
    private String baseName(String[] command, Map<String, String> jobContext) {
        long time = System.currentTimeMillis();
        int hash = Arrays.hashCode(command);
        return "/tmp/" + jobContext.get("project") + "_" + jobContext.get("execid") + time + "_" + hash;
    }

    /**
     * Read a file on the Docker container.
     *
     * @param file The full path to the file.
     * @param url  The URL for executing jobs on the desired container.
     * @return The contents of the file as a string.
     * @throws InterruptedException When listener is interrupted.
     * @throws IOException          When connection to Rancher fails.
     */
    private String readLogFile(String file, String url) throws IOException, InterruptedException {
        StringBuilder output = new StringBuilder();
        RancherWebSocketListener.getFile(url, accessKey, secretKey, output, file);
        return output.toString();
    }
}
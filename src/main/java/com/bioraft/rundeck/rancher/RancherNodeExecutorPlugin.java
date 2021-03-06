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
import com.dtolabs.rundeck.core.execution.ExecutionLogger;
import com.dtolabs.rundeck.core.execution.service.NodeExecutor;
import com.dtolabs.rundeck.core.execution.service.NodeExecutorResult;
import com.dtolabs.rundeck.core.execution.service.NodeExecutorResultImpl;
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

    private final RancherWebSocketListener socketListener;
    private final RancherWebSocketListener fileCopier;
    private final Storage storage;
    private String accessKey;
    private String secretKey;
    private ExecutionListener listener;
    private ExecutionLogger logger;
    private INodeEntry node;

    /**
     * Constructor called by RunDeck.
     */
    public RancherNodeExecutorPlugin() {
        socketListener = new RancherWebSocketListener();
        fileCopier = new RancherWebSocketListener();
        this.storage = new Storage();
    }

    /**
     * Constructor used for injecting unit testing mocks.
     *
     * @param rancherWebSocketListener Socket used to execute the command.
     * @param webSocketFileCopier      Socket used to fetch the PID + status file.
     * @param storage                  Rancher secret storage service.
     */
    public RancherNodeExecutorPlugin(RancherWebSocketListener rancherWebSocketListener, RancherWebSocketListener webSocketFileCopier, Storage storage) {
        socketListener = rancherWebSocketListener;
        fileCopier = webSocketFileCopier;
        this.storage = storage;
    }

    @Override
    public Description getDescription() {
        return DESC;
    }

    @Override
    public NodeExecutorResult executeCommand(final ExecutionContext context, final String[] command,
                                             final INodeEntry node) {
        this.node = node;
        Map<String, String> nodeAttributes = node.getAttributes();

        try {
            storage.setExecutionContext(context);
            accessKey = storage.loadStoragePathData(nodeAttributes.get(CONFIG_ACCESSKEY_PATH));
            secretKey = storage.loadStoragePathData(nodeAttributes.get(CONFIG_SECRETKEY_PATH));
        } catch (IOException e) {
            return NodeExecutorResultImpl.createFailure(StepFailureReason.IOFailure, e.getMessage(), node);
        }

        listener = context.getExecutionListener();
        logger = context.getExecutionLogger();

        Map<String, String> jobContext = context.getDataContext().get("job");
        String temp = this.baseName(command, jobContext);

        int timeout = IResolverUtil.resolveIntProperty(
                RANCHER_CONFIG_EXECUTOR_TIMEOUT,
                300,
                node,
                context.getIFramework().getFrameworkProjectMgr().getFrameworkProject(context.getFrameworkProject()),
                context.getIFramework()
        );

        if (nodeAttributes.get("type").equals("service")) {
            // "self": "https://rancher.example.com/v2-beta/projects/1a10/services/1s56"
            // "execute": "https://rancher.example.com/v2-beta/projects/1a10/containers/1i234/?action=execute",
            String self = nodeAttributes.get(NODE_ATT_SELF);
            String[] instanceIds = nodeAttributes.get("instanceIds").split(",");
            NodeExecutorResult result = NodeExecutorResultImpl.createFailure(StepFailureReason.PluginFailed, "No containers in node", node);
            for (String instance: instanceIds) {
                String url = self.replaceFirst("/services/[0-9]+s[0-9]+", "/containers/" + instance + "/?action=execute");
                result = runJob(url, command, temp, timeout);
                if (!result.isSuccess()) {
                    break;
                }
            }
            return result;
        } else {
            String url = nodeAttributes.get("execute");
            return runJob(url, command, temp, timeout);
        }
    }

    private NodeExecutorResult runJob(String url, String[] command, String temp, int timeout) {
        try {
            logger.log(DEBUG_LEVEL, "Running " + String.join(" ", command));
            socketListener.thisRunJob(url, accessKey, secretKey, command, listener, temp, timeout);
            logger.log(DEBUG_LEVEL, "Ran " + String.join(" ", command));
        } catch (IOException e) {
            return NodeExecutorResultImpl.createFailure(StepFailureReason.IOFailure, e.getMessage(), node);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return NodeExecutorResultImpl.createFailure(StepFailureReason.Interrupted, e.getMessage(), node);
        }

        String statusFileContents;
        String file = temp + ".pid";
        logger.log(DEBUG_LEVEL, "Reading '" + file + "' on " + url);
        try {
            statusFileContents = fileCopier.thisGetFile(url, accessKey, secretKey, file);
        } catch (IOException e) {
            return NodeExecutorResultImpl.createFailure(StepFailureReason.IOFailure, e.getMessage(), node);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return NodeExecutorResultImpl.createFailure(StepFailureReason.Interrupted, e.getMessage(), node);
        }

        String[] pidFile = statusFileContents.split(" ");
        if (pidFile.length < 2) {
            String message = "Process " + statusFileContents + " did not return a status.";
            return NodeExecutorResultImpl.createFailure(StepFailureReason.PluginFailed, message, node);
        } else if (Integer.parseInt(pidFile[1]) == 0) {
            return NodeExecutorResultImpl.createSuccess(node);
        } else {
            String message = "Process " + pidFile[0] + " status " + pidFile[1];
            return NodeExecutorResultImpl.createFailure(StepFailureReason.PluginFailed, message, node);
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
}
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
import com.dtolabs.rundeck.core.plugins.configuration.Describable;
import com.dtolabs.rundeck.core.plugins.configuration.Description;
import com.dtolabs.rundeck.core.plugins.configuration.PropertyUtil;
import com.dtolabs.rundeck.core.storage.ResourceMeta;
import com.dtolabs.rundeck.plugins.ServiceNameConstants;
import com.dtolabs.rundeck.plugins.util.DescriptionBuilder;

/**
 * RancherNodeExecutorPlugin is a {@link NodeExecutor} plugin implementation for
 * Rancher.
 *
 * @author Karl DeBisschop <kdebisschop@gmail.com>
 * @since 2019-12-08
 */
@Plugin(name = RancherShared.SERVICE_PROVIDER_NAME, service = ServiceNameConstants.NodeExecutor)
public class RancherNodeExecutorPlugin implements NodeExecutor, Describable {

	static final Description DESC;

	private String accessKey;
	private String secretKey;

	static {
		DescriptionBuilder builder = DescriptionBuilder.builder();
		builder.name(RancherShared.SERVICE_PROVIDER_NAME);
		builder.title("Rancher Node Executor");
		builder.description("Executes a command on a remote rancher node.");

		builder.property(PropertyUtil.integer(RancherShared.CONFIG_EXECUTOR_TIMEOUT, "Maximum execution time",
				"Terminate execution after specified number of seconds", true, "300"));

		builder.mapping(RancherShared.CONFIG_EXECUTOR_TIMEOUT, "project." + RancherShared.CONFIG_EXECUTOR_TIMEOUT);
		builder.frameworkMapping(RancherShared.CONFIG_EXECUTOR_TIMEOUT,
				"framework." + RancherShared.CONFIG_EXECUTOR_TIMEOUT);

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
		try {
			accessKey = this.loadStoragePathData(context, nodeAttributes.get(RancherShared.CONFIG_ACCESSKEY_PATH));
			secretKey = this.loadStoragePathData(context, nodeAttributes.get(RancherShared.CONFIG_SECRETKEY_PATH));
		} catch (IOException e) {
			return NodeExecutorResultImpl.createFailure(StepFailureReason.IOFailure, e.getMessage(), node);
		}

		ExecutionListener listener = context.getExecutionListener();

		String url = nodeAttributes.get("execute");

		Map<String, String> jobContext = context.getDataContext().get("job");
		String temp = this.baseName(command, jobContext);

		int timeout = ResolverUtil.resolveIntProperty(RancherShared.CONFIG_EXECUTOR_TIMEOUT, 300, node,
				context.getFramework().getFrameworkProjectMgr().getFrameworkProject(context.getFrameworkProject()),
				context.getFramework());
		try {
			RancherWebSocketListener.runJob(url, accessKey, secretKey, command, listener, temp, timeout);
		} catch (IOException e) {
			return NodeExecutorResultImpl.createFailure(StepFailureReason.IOFailure, e.getMessage(), node);
		} catch (InterruptedException e) {
			return NodeExecutorResultImpl.createFailure(StepFailureReason.Interrupted, e.getMessage(), node);
		}

		String[] pidFile;
		try {
			pidFile = this.readLogFile(temp + ".pid", url).split(" +");
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
	 * Create a (nearly) unique file path without the extension.
	 * 
	 * 
	 *
	 * @param command    The command array to be executed for the job.
	 * @param jobContext The job context map.
	 * @return
	 */
	private String baseName(String[] command, Map<String, String> jobContext) {
		long time = System.currentTimeMillis();
		int hash = command.hashCode();
		return "/tmp/" + jobContext.get("project") + "_" + jobContext.get("execid") + time + "_" + hash;
	}

	/**
	 * Read a file on the Docker container.
	 *
	 * @param file The full path to the file.
	 * @param url  The URL for executing jobs on the desired container.
	 * @return The contents of the file as a string.
	 * @throws InterruptedException
	 * @throws IOException
	 */
	private String readLogFile(String file, String url) throws IOException, InterruptedException {
		StringBuilder output = new StringBuilder();
		RancherWebSocketListener.getFile(url, accessKey, secretKey, output, file);
		return output.toString();
	}

	/**
	 * Get a (secret) value from password storage.
	 *
	 * @param context
	 * @param passwordStoragePath
	 * @return
	 * @throws IOException
	 */
	private String loadStoragePathData(final ExecutionContext context, final String passwordStoragePath)
			throws IOException {
		if (null == passwordStoragePath) {
			return null;
		}
		ResourceMeta contents = context.getStorageTree().getResource(passwordStoragePath).getContents();
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		contents.writeContent(byteArrayOutputStream);
		return new String(byteArrayOutputStream.toByteArray());
	}
}
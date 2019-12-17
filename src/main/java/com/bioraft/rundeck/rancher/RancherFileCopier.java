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

/*
 * RancherFileCopier.java
 * 
 * @author Karl DeBisschop <kdebisschop@gmail.com>
 * Created: 3/31/11 4:09 PM
 * 
 */

package com.bioraft.rundeck.rancher;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.apache.tools.ant.Project;

import com.dtolabs.rundeck.core.Constants;
import com.dtolabs.rundeck.core.common.INodeEntry;
import com.dtolabs.rundeck.core.execution.ExecutionContext;
import com.dtolabs.rundeck.core.execution.impl.common.BaseFileCopier;
import com.dtolabs.rundeck.core.execution.script.ScriptfileUtils;
import com.dtolabs.rundeck.core.execution.service.FileCopier;
import com.dtolabs.rundeck.core.execution.service.FileCopierException;
import com.dtolabs.rundeck.core.plugins.Plugin;
import com.dtolabs.rundeck.core.storage.ResourceMeta;
import com.dtolabs.rundeck.plugins.ServiceNameConstants;
import com.dtolabs.rundeck.plugins.descriptions.PluginDescription;

/**
 * RancherStubFileCopier provider for the FileCopier service
 *
 * @author Karl DeBisschop <kdebisschop@gmail.com>
 * @since 2019-12-08
 */
@Plugin(name = RancherShared.SERVICE_PROVIDER_NAME, service = ServiceNameConstants.FileCopier)
@PluginDescription(title = "Rancher File Copier", description = "Copies a file to a Rancher-mananged Docker container.")
public class RancherFileCopier implements FileCopier {

	@Override
	public String copyFileStream(final ExecutionContext context, InputStream input, INodeEntry node,
			final String destination) throws FileCopierException {

		return copyFile(context, null, input, null, node);
	}

	@Override
	public String copyFile(final ExecutionContext context, File scriptfile, INodeEntry node, final String destination)
			throws FileCopierException {
		return copyFile(context, scriptfile, null, null, node);
	}

	@Override
	public String copyScriptContent(ExecutionContext context, String script, INodeEntry node, final String destination)
			throws FileCopierException {

		return copyFile(context, null, null, script, node);
	}

	private String copyFile(final ExecutionContext context, final File scriptfile, final InputStream input,
			final String script, final INodeEntry node) throws FileCopierException {
		return copyFile(context, scriptfile, input, script, node, null);

	}

	private String copyFile(final ExecutionContext context, final File scriptfile, final InputStream input,
			final String script, final INodeEntry node, final String destinationPath) throws FileCopierException {

		Project project = new Project();

		final String remotefile;

		Map<String, String> nodeAttributes = node.getAttributes();
		String accessKey;
		String secretKey;
		try {
			accessKey = this.loadStoragePathData(context, nodeAttributes.get(RancherShared.CONFIG_ACCESSKEY_PATH));
			secretKey = this.loadStoragePathData(context, nodeAttributes.get(RancherShared.CONFIG_SECRETKEY_PATH));
		} catch (IOException e) {
			throw new FileCopierException(e.getMessage(), RancherFileCopierFailureReason.AuthenticationFailure);
		}

		if (null == destinationPath) {
			String identity = null != context.getDataContext() && null != context.getDataContext().get("job")
					? context.getDataContext().get("job").get("execid")
					: null;
			remotefile = BaseFileCopier.generateRemoteFilepathForNode(node,
					context.getFramework().getFrameworkProjectMgr().getFrameworkProject(context.getFrameworkProject()),
					context.getFramework(), (null != scriptfile ? scriptfile.getName() : "dispatch-script"), null,
					identity);
		} else {
			remotefile = destinationPath;
		}
		// write to a local temp file or use the input file
		final File localTempfile = null != scriptfile ? scriptfile
				: BaseFileCopier.writeTempFile(context, scriptfile, input, script);

		/**
		 * Copy the file over
		 */
		context.getExecutionListener().log(3, "copying file: '" + localTempfile.getAbsolutePath() + "' to: '"
				+ node.getNodename() + ":" + remotefile + "'");

		String[] command = {"env", "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
				"RANCHER_ENVIRONMENT=" + nodeAttributes.get("environment"),
				"RANCHER_DOCKER_HOST=" + nodeAttributes.get("hostId"),
				"RANCHER_URL=" + nodeAttributes.get("execute").replaceFirst("/projects/.*$", ""),
				"RANCHER_ACCESS_KEY=" + accessKey, "RANCHER_SECRET_KEY=" + secretKey, "rancher", "docker", "cp",
				localTempfile.getAbsolutePath(), nodeAttributes.get("externalId") + ":" + remotefile};
		context.getExecutionListener().log(3, String.join(" ", command));
		
		boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
		String errormsg = null;
		try {
			ProcessBuilder builder = new ProcessBuilder();
			if (isWindows) {
				builder.command("cmd.exe", "/c", "dir");
			} else {
				builder.command(command);
//				builder.command("env", "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
//						"RANCHER_ENVIRONMENT=" + nodeAttributes.get("environment"),
//						"RANCHER_DOCKER_HOST=" + nodeAttributes.get("hostId"),
//						"RANCHER_URL=" + nodeAttributes.get("execute").replaceFirst("/projects/.*$", ""),
//						"RANCHER_ACCESS_KEY=" + accessKey, "RANCHER_SECRET_KEY=" + secretKey, "rancher", "docker", "cp",
//						localTempfile.getAbsolutePath(), nodeAttributes.get("externalId") + ":" + remotefile);
			}
			builder.directory(new File(System.getProperty("user.home")));
			Process process = builder.start();
			StreamGobbler streamGobbler = new StreamGobbler(process.getInputStream(), System.out::println);
			Executors.newSingleThreadExecutor().submit(streamGobbler);
			int exitCode = process.waitFor();
			assert exitCode == 0;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			if (null == scriptfile) {
				if (!ScriptfileUtils.releaseTempFile(localTempfile)) {
					context.getExecutionListener().log(Constants.WARN_LEVEL,
							"Unable to remove local temp file: " + localTempfile.getAbsolutePath());
				}
			}
		}

		return remotefile;
	}

	private static class StreamGobbler implements Runnable {
		private InputStream inputStream;
		private Consumer<String> consumer;

		public StreamGobbler(InputStream inputStream, Consumer<String> consumer) {
			this.inputStream = inputStream;
			this.consumer = consumer;
		}

		@Override
		public void run() {
			new BufferedReader(new InputStreamReader(inputStream)).lines().forEach(consumer);
		}
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

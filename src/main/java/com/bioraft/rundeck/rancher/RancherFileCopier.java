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

import com.dtolabs.rundeck.core.Constants;
import com.dtolabs.rundeck.core.common.Framework;
import com.dtolabs.rundeck.core.common.INodeEntry;
import com.dtolabs.rundeck.core.execution.ExecutionContext;
import com.dtolabs.rundeck.core.execution.ExecutionLogger;
import com.dtolabs.rundeck.core.execution.impl.common.BaseFileCopier;
import com.dtolabs.rundeck.core.execution.script.ScriptfileUtils;
import com.dtolabs.rundeck.core.execution.service.FileCopier;
import com.dtolabs.rundeck.core.execution.service.FileCopierException;
import com.dtolabs.rundeck.core.plugins.Plugin;
import com.dtolabs.rundeck.core.plugins.configuration.Describable;
import com.dtolabs.rundeck.core.plugins.configuration.Description;
import com.dtolabs.rundeck.core.plugins.configuration.PropertyUtil;
import com.dtolabs.rundeck.plugins.ServiceNameConstants;
import com.dtolabs.rundeck.plugins.descriptions.PluginDescription;
import com.dtolabs.rundeck.plugins.util.DescriptionBuilder;

import java.io.*;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static com.bioraft.rundeck.rancher.Constants.*;
import static com.bioraft.rundeck.rancher.Errors.ErrorCause.*;
import static com.dtolabs.rundeck.core.Constants.DEBUG_LEVEL;

/**
 * RancherStubFileCopier provider for the FileCopier service
 *
 * @author Karl DeBisschop <kdebisschop@gmail.com>
 * @since 2019-12-08
 */
@Plugin(name = RANCHER_SERVICE_PROVIDER, service = ServiceNameConstants.FileCopier)
@PluginDescription(title = "Rancher File Copier", description = "Copies a file to a Rancher-managed Docker container.")
public class RancherFileCopier implements FileCopier, Describable {

    static final Description DESC;

    RancherWebSocketListener listener;

    static {
        DescriptionBuilder builder = DescriptionBuilder.builder();
        builder.name(RANCHER_SERVICE_PROVIDER);
        builder.title("Rancher File Copier");
        builder.description("Copies a file to a Rancher-managed Docker container");

        builder.property(PropertyUtil.string(RANCHER_CONFIG_CLI_PATH, "Search path ",
                "A search path on the Rundeck host that finds rancher, docker, sh, and base64 (e.g., /usr/local/bin:/usr/bin:/bin)",
                false, ""));

        builder.mapping(RANCHER_CONFIG_CLI_PATH, PROJ_RANCHER_CLI_PATH);
        builder.frameworkMapping(RANCHER_CONFIG_CLI_PATH, FMWK_RANCHER_CLI_PATH);

        DESC = builder.build();
    }

    public RancherFileCopier(RancherWebSocketListener listener) {
        this.listener = listener;
    }

    public RancherFileCopier() {
        this.listener = new RancherWebSocketListener();
    }

    @Override
    public Description getDescription() {
        return DESC;
    }

    @Override
    public String copyFileStream(ExecutionContext context, InputStream input, INodeEntry node, String destination)
            throws FileCopierException {
        return copyFile(context, null, input, null, node, destination);
    }

    @Override
    public String copyFile(final ExecutionContext context, File file, INodeEntry node, final String destination)
            throws FileCopierException {
        return copyFile(context, file, null, null, node, destination);
    }

    @Override
    public String copyScriptContent(ExecutionContext context, String script, INodeEntry node, final String destination)
            throws FileCopierException {
        return copyFile(context, null, null, script, node, destination);
    }

    private String copyFile(final ExecutionContext context, final File scriptfile, final InputStream input,
                            final String script, final INodeEntry node, final String destinationPath) throws FileCopierException {

        Map<String, String> nodeAttributes = node.getAttributes();

        if (nodeAttributes.get("type").equals("service")) {
            String message = "File copier is not currently supported for services";
            throw new FileCopierException(message, UNSUPPORTED_NODE_TYPE);
        }

        String accessKey;
        String secretKey;
        try {
            Storage storage = new Storage(context);
            accessKey = storage.loadStoragePathData(nodeAttributes.get(CONFIG_ACCESSKEY_PATH));
            secretKey = storage.loadStoragePathData(nodeAttributes.get(CONFIG_SECRETKEY_PATH));
        } catch (IOException e) {
            throw new FileCopierException(e.getMessage(), AUTHENTICATION_FAILURE);
        }

        String remotefile = getRemoteFile(destinationPath, context, node, scriptfile);

        // write to a local temp file or use the input file
        final File localTempfile = (null != scriptfile) ? scriptfile
                : BaseFileCopier.writeTempFile(context, null, input, script);

        // Copy the file over
        ExecutionLogger logger = context.getExecutionLogger();
        String absPath = localTempfile.getAbsolutePath();
        String message = "copying file: '" + absPath + "' to: '" + node.getNodename() + ":" + remotefile + "'";
        logger.log(DEBUG_LEVEL, message);

        Framework framework = context.getFramework();
        String project = context.getFrameworkProject();
        String searchPath = framework.getProjectProperty(project, PROJ_RANCHER_CLI_PATH);
        if ((searchPath == null || searchPath.equals("")) && framework.hasProperty(FMWK_RANCHER_CLI_PATH)) {
            searchPath = framework.getProperty(FMWK_RANCHER_CLI_PATH);
        }

        try {
            String result;
            if (searchPath == null || searchPath.equals("")) {
                result = copyViaApi(context, nodeAttributes, accessKey, secretKey, localTempfile, remotefile);
            } else {
                result = copyViaCli(context, nodeAttributes, accessKey, secretKey, localTempfile, remotefile, searchPath);
            }
            context.getExecutionLogger().log(DEBUG_LEVEL, "Copied '" + localTempfile + "' to '" + result );
            return result;
        } finally {
            if (null == scriptfile && !ScriptfileUtils.releaseTempFile(localTempfile)) {
                context.getExecutionListener().log(Constants.WARN_LEVEL,
                        "Unable to remove local temp file: " + localTempfile.getAbsolutePath());
            }
        }
    }

    private String getRemoteFile(final String destinationPath, final ExecutionContext context, final INodeEntry node, final File scriptfile) {
        if (null == destinationPath) {
            String identity = null != context.getDataContext() && null != context.getDataContext().get("job")
                    ? context.getDataContext().get("job").get("execid")
                    : null;
            return BaseFileCopier.generateRemoteFilepathForNode(node,
                    context.getFramework().getFrameworkProjectMgr().getFrameworkProject(context.getFrameworkProject()),
                    context.getFramework(), (null != scriptfile ? scriptfile.getName() : "dispatch-script"), null,
                    identity);
        } else {
            return destinationPath;
        }
    }

    private String copyViaCli(final ExecutionContext context, Map<String, String> nodeAttributes, String accessKey, String secretKey,
                              File localTempFile, String remotefile, String searchPath) throws FileCopierException {
        ExecutionLogger logger = context.getExecutionLogger();
        logger.log(DEBUG_LEVEL, "PATH: '" + searchPath + "'");

        String path = localTempFile.getAbsolutePath();
        String instance = nodeAttributes.get("externalId");
        String[] command = {"rancher", "docker", "cp", path, instance + ":" + remotefile};

        boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
        try {
            ProcessBuilder builder = new ProcessBuilder();
            Map<String, String> environment = builder.environment();
            logger.log(DEBUG_LEVEL, "CMD: '" + String.join(" ", command) + "'");
            environment.put("PATH", searchPath);
            environment.put("RANCHER_ENVIRONMENT", nodeAttributes.get("environment"));
            environment.put("RANCHER_DOCKER_HOST", nodeAttributes.get("hostname"));
            environment.put("RANCHER_URL", nodeAttributes.get("execute").replaceFirst("/projects/.*$", ""));
            environment.put("RANCHER_ACCESS_KEY", accessKey);
            environment.put("RANCHER_SECRET_KEY", secretKey);
            if (isWindows) {
                throw new FileCopierException("Windows is not currently supported.", UNSUPPORTED_OPERATING_SYSTEM);
            } else {
                builder.command(command);
            }
            logger.log(DEBUG_LEVEL, "CMD: '" + String.join(" ", command) + "'");
            builder.directory(new File(System.getProperty("java.io.tmpdir")));
            Process process = builder.start();
            StreamGobbler streamGobbler = new StreamGobbler(process.getInputStream(), System.out::println);
            Executors.newSingleThreadExecutor().submit(streamGobbler);
            int exitCode = process.waitFor();
            assert exitCode == 0;
        } catch (IOException e) {
            throw new FileCopierException("Child process IO Exception", IO_EXCEPTION, e);
        } catch (InterruptedException e) {
            throw new FileCopierException("Child process interrupted", INTERRUPTED, e);
        }

        return remotefile;
    }

    private String copyViaApi(final ExecutionContext context, Map<String, String> nodeAttributes, String accessKey, String secretKey, File file,
                              String destination) throws FileCopierException {
        try {
            String url = nodeAttributes.get("execute");
            listener.putFile(url, accessKey, secretKey, file, destination);
            context.getExecutionLogger().log(DEBUG_LEVEL, "PUT: '" + file + "'");
        } catch (IOException | InterruptedException e) {
            throw new FileCopierException(e.getMessage(), CONNECTION_FAILURE);
        }
        return destination;
    }

    private static class StreamGobbler implements Runnable {
        private final InputStream inputStream;
        private final Consumer<String> consumer;

        public StreamGobbler(InputStream inputStream, Consumer<String> consumer) {
            this.inputStream = inputStream;
            this.consumer = consumer;
        }

        @Override
        public void run() {
            new BufferedReader(new InputStreamReader(inputStream)).lines().forEach(consumer);
        }
    }
}

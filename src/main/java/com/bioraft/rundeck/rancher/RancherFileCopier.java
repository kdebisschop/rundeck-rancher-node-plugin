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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import com.dtolabs.rundeck.core.common.INodeEntry;
import com.dtolabs.rundeck.core.execution.ExecutionContext;
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
	public String copyFileStream(final ExecutionContext context, final InputStream input, final INodeEntry node,
			final String destination) throws FileCopierException {
		Map<String, String> nodeAttributes = node.getAttributes();
		String accessKey;
		String secretKey;
		try {
			accessKey = this.loadStoragePathData(context, nodeAttributes.get(RancherShared.CONFIG_ACCESSKEY_PATH));
			secretKey = this.loadStoragePathData(context, nodeAttributes.get(RancherShared.CONFIG_SECRETKEY_PATH));
		} catch (IOException e) {
			throw new FileCopierException(e.getMessage(), RancherFileCopierFailureReason.AuthenticationFailure);
		}
		String url = nodeAttributes.get("execute");

		try {
			RancherWebSocketListener.putFile(url, accessKey, secretKey, input, destination);
		} catch (IOException | InterruptedException e) {
			throw new FileCopierException(e.getMessage(), RancherFileCopierFailureReason.ConnectionFailure);
		}
		return destination;
	}

	@Override
	public String copyFile(final ExecutionContext context, final File file, final INodeEntry node,
			final String destination) throws FileCopierException {
		FileInputStream fileStream = null;
		try {
			fileStream = new FileInputStream(file);
			return copyFileStream(context, fileStream, node, destination);
		} catch (FileNotFoundException fnf) {
			throw new FileCopierException(fnf.getMessage(), RancherFileCopierFailureReason.ConnectionFailure);
		} finally {
			if (fileStream != null) {
				try {
					fileStream.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

	@Override
	public String copyScriptContent(final ExecutionContext context, final String script, final INodeEntry node,
			final String destination) throws FileCopierException {
		ByteArrayInputStream scriptStream = new ByteArrayInputStream(script.getBytes());
		try {
			return copyFileStream(context, scriptStream, node, destination);
		} finally {
			try {
				scriptStream.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
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

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

import java.util.Map;
import java.util.Set;

import com.dtolabs.rundeck.core.common.INodeEntry;
import com.dtolabs.rundeck.core.execution.workflow.SharedOutputContext;
import com.dtolabs.rundeck.core.execution.workflow.steps.node.NodeStepException;
import com.dtolabs.rundeck.core.plugins.Plugin;
import com.dtolabs.rundeck.plugins.ServiceNameConstants;
import com.dtolabs.rundeck.plugins.descriptions.PluginDescription;
import com.dtolabs.rundeck.plugins.descriptions.PluginProperty;
import com.dtolabs.rundeck.plugins.step.PluginStepContext;
import com.dtolabs.rundeck.plugins.step.NodeStepPlugin;

/**
 * Workflow Node Step Plug-in to choose one of two values to uplift into a step
 * variable.
 *
 * @author Karl DeBisschop <kdebisschop@gmail.com>
 * @since 2019-12-11
 */
@Plugin(name = RancherShared.SERVICE_PROVIDER_NAME, service = ServiceNameConstants.WorkflowNodeStep)
@PluginDescription(title = "Rancher Service Upgrade Node Plugin", description = "Upgrades the service associated with the selected nodes.")
public class RancherUpgradeServiceNodeStepPlugin implements NodeStepPlugin {

	@PluginProperty(name = "dockerImage", title = "Docker Image", description = "The fully specified Docker image to upgrade to.", required = true)
	private String group;

	@PluginProperty(name = "name", title = "Name", description = "Variable name (i.e., ${group.name}", required = true)
	private String name;

	@PluginProperty(name = "testValue", title = "Test Value", description = "First test value", required = true)
	private String testValue;

	@PluginProperty(name = "comparisonValue", title = "Comparison Value", description = "Second test value", required = true)
	private String comparisonValue;

	@PluginProperty(name = "ifTrue", title = "If True", description = "Value to assign if comparison is true", required = true)
	private String ifTrue;

	@PluginProperty(name = "ifFalse", title = "If False", description = "Value to assign if comparison is false", required = true)
	private String ifFalse;

	@Override
	public void executeNodeStep(PluginStepContext context, Map<String, Object> configuration, INodeEntry node)
			throws NodeStepException {
		String value;

		Map<String, String> attributes = node.getAttributes();
		
		Set<String> tags = node.getTags();
		
		if (group == null) {
			group = (String) configuration.get("group");
		}

		if (name == null) {
			name = (String) configuration.get("name");
		}
		if (testValue == null) {
			testValue = (String) configuration.get("testValue");
		}

		if (comparisonValue == null) {
			comparisonValue = (String) configuration.get("comparisonValue");
		}
		if (ifTrue == null) {
			ifTrue = (String) configuration.get("ifTrue");
		}
		if (ifFalse == null) {
			ifFalse = (String) configuration.get("ifFalse");
		}


		SharedOutputContext sharedOutputContext = context.getOutputContext();
		sharedOutputContext.addOutput(group, name, value);
	}

}
package com.bioraft.rundeck.rancher;

import com.dtolabs.rundeck.core.common.IFramework;
import com.dtolabs.rundeck.core.common.INodeEntry;
import com.dtolabs.rundeck.core.common.IRundeckProject;
import com.dtolabs.rundeck.core.execution.impl.jsch.JschNodeExecutor;

/*
 * Copyright 2016 SimplifyOps, Inc. (http://simplifyops.com)
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

/**
 * Created by greg on 3/19/15.
 */
public class IResolverUtil {
    /**
     * Resolve a node/project/framework property by first checking node attributes named X, then project properties
     * named "project.X", then framework properties named "framework.X". If none of those exist, return the default
     * value
     */
    public static String resolveProperty(
            final String nodeAttribute,
            final String defaultValue,
            final INodeEntry node,
            final IRundeckProject frameworkProject,
            final IFramework framework
    )
    {
        if (null != node.getAttributes().get(nodeAttribute)) {
            return node.getAttributes().get(nodeAttribute);
        } else if (frameworkProject.hasProperty(JschNodeExecutor.PROJ_PROP_PREFIX + nodeAttribute)
                && !"".equals(frameworkProject.getProperty(JschNodeExecutor.PROJ_PROP_PREFIX + nodeAttribute))) {
            return frameworkProject.getProperty(JschNodeExecutor.PROJ_PROP_PREFIX + nodeAttribute);
        } else if (framework.getPropertyLookup().hasProperty(JschNodeExecutor.FWK_PROP_PREFIX + nodeAttribute)) {
            return framework.getPropertyLookup().getProperty(JschNodeExecutor.FWK_PROP_PREFIX + nodeAttribute);
        } else {
            return defaultValue;
        }
    }

    public static int resolveIntProperty(
            final String attribute,
            final int defaultValue,
            final INodeEntry iNodeEntry,
            final IRundeckProject frameworkProject,
            final IFramework framework
    )
    {

        int value = defaultValue;
        final String string = resolveProperty(attribute, null, iNodeEntry, frameworkProject, framework);
        if (null != string) {
            try {
                value = Integer.parseInt(string);
            } catch (NumberFormatException e) {
            }
        }
        return value;
    }

    public static long resolveLongProperty(
            final String attribute,
            final long defaultValue,
            final INodeEntry iNodeEntry,
            final IRundeckProject frameworkProject,
            final IFramework framework
    )
    {

        long value = defaultValue;
        final String string = resolveProperty(attribute, null, iNodeEntry, frameworkProject, framework);
        if (null != string) {
            try {
                value = Long.parseLong(string);
            } catch (NumberFormatException e) {
            }
        }
        return value;
    }

    public static boolean resolveBooleanProperty(
            final String attribute,
            final boolean defaultValue,
            final INodeEntry iNodeEntry,
            final IRundeckProject frameworkProject,
            final IFramework framework
    )
    {

        boolean value = defaultValue;
        final String string = resolveProperty(attribute, null, iNodeEntry, frameworkProject, framework);
        if (null != string) {
            value = Boolean.parseBoolean(string);
        }
        return value;
    }
}

/*
 * Copyright 2020 BioRAFT, Inc. (http://bioraft.com)
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

/**
 * Shared code and constants for Rancher node.
 *
 * @author Karl DeBisschop <kdebisschop@gmail.com>
 * @since 2020-02-28
 */

public class Errors {

    private Errors() {
        throw new IllegalStateException("Utility class");
    }

    public enum ErrorCause implements FailureReason {
        UNSUPPORTED_OPERATING_SYSTEM,
        INVALID_CONFIGURATION,
        INVALID_JSON,
        IO_EXCEPTION,
        ACTION_FAILED,
        ACTION_NOT_SUPPORTED,
        NO_SERVICE_OBJECT,
        SERVICE_NOT_RUNNING,
        MISSING_ACTION_URL,
        MISSING_UPGRADE_URL,
        NO_UPGRADE_DATA,
        UPGRADE_FAILURE,
        INTERRUPTED,
        INVALID_STACK_NAME,
        INVALID_ENVIRONMENT_NAME,
        CONNECTION_FAILURE
    }
}

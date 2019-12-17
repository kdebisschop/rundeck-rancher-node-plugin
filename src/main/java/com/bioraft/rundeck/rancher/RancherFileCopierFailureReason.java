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

import com.dtolabs.rundeck.core.execution.workflow.steps.FailureReason;

/**
 * Failure reasons for Rancher File Copier.
 *
 * @author Karl DeBisschop <kdebisschop@gmail.com>
 * @since 2019-12-14
 */
public enum RancherFileCopierFailureReason implements FailureReason {
    /**
     * Requested file could not be found
     */
    FileNotFound,
    /**
     * Timeout on connection
     */
    ConnectionTimeout,
    /**
     * Connection unsuccessful
     */
    ConnectionFailure,
    /**
     * Authentication unsuccessful
     */
    AuthenticationFailure,
    /**
     * Command or script execution result code was not zero
     */
    NonZeroResultCode,
}

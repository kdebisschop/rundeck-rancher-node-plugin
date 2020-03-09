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

import com.dtolabs.rundeck.core.execution.ExecutionContext;
import com.dtolabs.rundeck.core.storage.ResourceMeta;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Shared code and constants for Rancher node.
 *
 * @author Karl DeBisschop <kdebisschop@gmail.com>
 * @since 2019-12-11
 */
public class RancherShared {

    private RancherShared() {
        throw new IllegalStateException("Utility class");
    }

    public static String ensureStringIsJsonObject(String string) {
        if (string == null) {
            return "";
        }
        string = string.trim();
        if (string.isEmpty()) {
            return "";
        }
        return (string.startsWith("{") ? "" : "{") + string + (string.endsWith("}") ? "" : "}");
    }

    public static String ensureStringIsJsonArray(String string) {
        if (string == null) {
            return "";
        }
        string = string.trim();
        if (string.isEmpty()) {
            return "";
        }
        return (string.startsWith("[") ? "" : "[") + string + (string.endsWith("]") ? "" : "]");
    }

    public static String apiPath(String environmentId, String target) {
        return "/projects/" + environmentId + target;
    }

    /**
     * Get a (secret) value from password storage.
     *
     * @param context             The current plugin execution context.
     * @param passwordStoragePath The path to look up in storage.
     * @return The requested secret or password.
     * @throws IOException When there is an IO Exception writing to stream.
     */
    public static String loadStoragePathData(final ExecutionContext context, final String passwordStoragePath) throws IOException {
        if (null == passwordStoragePath) {
            throw new IOException("Storage path is not defined.");
        }
        ResourceMeta contents = context.getStorageTree().getResource(passwordStoragePath).getContents();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        contents.writeContent(byteArrayOutputStream);
        return new String(byteArrayOutputStream.toByteArray());
    }

    /**
     * Builds a JsonNode object for insertion into the secrets array.
     *
     * @param secretId A secret ID from Rancher (like "1se1")
     * @return JSON expression for secret reference.
     */
    public static JsonNode buildSecret(String secretId) {
        return (new ObjectMapper()).valueToTree(secretJsonMap(secretId));
    }

    public static Map<String, String> secretJsonMap(String secretId) {
        HashMap<String, String> map = new HashMap<>();
        map.put("type", "secretReference");
        map.put("uid", "0");
        map.put("gid", "0");
        map.put("mode", "444");
        map.put("name", "");
        map.put("secretId", secretId);
        return map;
    }

    public static String mountPoint(String mountSpec) {
        return mountSpec.replaceFirst("[^:]+:", "").replaceFirst(":.*", "");
    }
}
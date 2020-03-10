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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared code and constants for Rancher node.
 *
 * @author Karl DeBisschop <kdebisschop@gmail.com>
 * @since 2019-12-11
 */
public class Strings {

    public String ensureStringIsJsonObject(String string) {
        if (string == null) {
            return "";
        }
        string = string.trim();
        if (string.isEmpty()) {
            return "";
        }
        return (string.startsWith("{") ? "" : "{") + string + (string.endsWith("}") ? "" : "}");
    }

    public String ensureStringIsJsonArray(String string) {
        if (string == null) {
            return "";
        }
        string = string.trim();
        if (string.isEmpty()) {
            return "";
        }
        return (string.startsWith("[") ? "" : "[") + string + (string.endsWith("]") ? "" : "]");
    }

    public String apiPath(String environmentId, String target) {
        return "/projects/" + environmentId + target;
    }

    /**
     * Builds a JsonNode object for insertion into the secrets array.
     *
     * @param secretId A secret ID from Rancher (like "1se1")
     * @return JSON expression for secret reference.
     */
    public JsonNode buildSecret(String secretId) {
        return (new ObjectMapper()).valueToTree(secretJsonMap(secretId));
    }

    public Map<String, String> secretJsonMap(String secretId) {
        HashMap<String, String> map = new HashMap<>();
        map.put("type", "secretReference");
        map.put("uid", "0");
        map.put("gid", "0");
        map.put("mode", "444");
        map.put("name", "");
        map.put("secretId", secretId);
        return map;
    }

    public String mountPoint(String mountSpec) {
        return mountSpec.replaceFirst("[^:]+:", "").replaceFirst(":.*", "");
    }
}
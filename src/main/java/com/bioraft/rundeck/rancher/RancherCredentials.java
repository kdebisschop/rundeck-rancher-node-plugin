package com.bioraft.rundeck.rancher;

import com.dtolabs.rundeck.core.execution.ExecutionContext;

import java.io.IOException;
import java.util.Map;

import static com.bioraft.rundeck.rancher.Constants.CONFIG_ACCESSKEY_PATH;
import static com.bioraft.rundeck.rancher.Constants.CONFIG_SECRETKEY_PATH;

public class RancherCredentials {
    private final String accessKey;
    private final String secretKey;

    public RancherCredentials(ExecutionContext context, Map<String, String> nodeAttributes) throws IOException {
        Storage storage = new Storage(context);
        accessKey = storage.loadStoragePathData(nodeAttributes.get(CONFIG_ACCESSKEY_PATH));
        secretKey = storage.loadStoragePathData(nodeAttributes.get(CONFIG_SECRETKEY_PATH));
    }

    public String getAccessKey() {
        return accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }
}

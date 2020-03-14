package com.bioraft.rundeck.rancher;

import com.dtolabs.rundeck.core.execution.ExecutionContext;
import com.dtolabs.rundeck.core.storage.ResourceMeta;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class Storage {

    private ExecutionContext executionContext;

    public Storage() {
    }

    public Storage(final ExecutionContext executionContext) {
        this.executionContext = executionContext;
    }

    public void setExecutionContext(ExecutionContext executionContext) {
        this.executionContext = executionContext;
    }

    /**
     * Get a (secret) value from password storage.
     *
     * @param passwordStoragePath The path to look up in storage.
     * @return The requested secret or password.
     * @throws IOException When there is an IO Exception writing to stream.
     */
    public String loadStoragePathData(final String passwordStoragePath) throws IOException {
        if (executionContext == null) {
            throw new IOException("ExecutionContext is not defined.");
        }
        if (null == passwordStoragePath) {
            throw new IOException("Storage path is not defined.");
        }
        ResourceMeta contents = executionContext.getStorageTree().getResource(passwordStoragePath).getContents();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        contents.writeContent(byteArrayOutputStream);
        return new String(byteArrayOutputStream.toByteArray());
    }

}

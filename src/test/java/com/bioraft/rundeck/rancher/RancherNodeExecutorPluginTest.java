package com.bioraft.rundeck.rancher;

import com.dtolabs.rundeck.core.common.Framework;
import com.dtolabs.rundeck.core.common.INodeEntry;
import com.dtolabs.rundeck.core.common.ProjectManager;
import com.dtolabs.rundeck.core.execution.ExecutionContext;
import com.dtolabs.rundeck.core.execution.ExecutionLogger;
import com.dtolabs.rundeck.core.execution.service.NodeExecutorResult;
import com.dtolabs.rundeck.core.execution.workflow.steps.StepFailureReason;
import com.dtolabs.rundeck.core.storage.ResourceMeta;
import com.dtolabs.rundeck.core.storage.StorageTree;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.rundeck.storage.api.Resource;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.bioraft.rundeck.rancher.Constants.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class RancherNodeExecutorPluginTest {

    @Mock
    ExecutionContext executionContext;

    @Mock
    ExecutionLogger executionLogger;

    @Mock
    Framework framework;

    @Mock
    ProjectManager projectManager;

    @Mock
    StorageTree storageTree;

    @Mock
    Resource<ResourceMeta> resource;

    @Mock
    ResourceMeta resourceMeta;

    @Mock
    INodeEntry node;

    @Mock
    Storage storage;

    @Mock
    RancherWebSocketListener rancherWebSocketListener;

    @Mock
    RancherWebSocketListener webSocketFileCopier;

    Map<String, String> nodeAttributes;

    Map<String, Map<String, String>> dataContext;

    Map<String, String> jobContext;

    @Before
    public void setUp() {
        nodeAttributes = new HashMap<>();
        dataContext = new HashMap<>();
        jobContext = new HashMap<>();
        jobContext.put("project", "project");
        jobContext.put("execid", "execid");
        dataContext.put("job", jobContext);
    }

    @Test
    public void testDescription() {
        RancherNodeExecutorPlugin nodeExecutorPlugin = new RancherNodeExecutorPlugin();
        assertTrue(nodeExecutorPlugin.getDescription().getDescription().startsWith("Executes a command "));
    }

    @Test
    public void serviceIsNotYetSupported() {
        RancherNodeExecutorPlugin nodeExecutorPlugin = new RancherNodeExecutorPlugin();
        String[] command = { "ls" };
        nodeAttributes.put("type", "service");
        when(node.getAttributes()).thenReturn(nodeAttributes);
        NodeExecutorResult result = nodeExecutorPlugin.executeCommand(executionContext, command, node);
        String message = "Node executor is not currently supported for services";
        assertEquals(message, result.getFailureMessage());
        assertEquals(StepFailureReason.PluginFailed, result.getFailureReason());
        assertEquals(-1, result.getResultCode());
    }

    @Test
    public void missingKeyCreatesFailure() {
        RancherNodeExecutorPlugin nodeExecutorPlugin = new RancherNodeExecutorPlugin();
        String[] command = { "ls" };
        nodeAttributes.put("type", "container");
        when(node.getAttributes()).thenReturn(nodeAttributes);
        NodeExecutorResult result = nodeExecutorPlugin.executeCommand(executionContext, command, node);
        String message = "Storage path is not defined.";
        assertEquals(message, result.getFailureMessage());
        assertEquals(StepFailureReason.IOFailure, result.getFailureReason());
        assertEquals(-1, result.getResultCode());
    }

    @Test
    public void missingKeyValueCreatesFailure() throws IOException {
        RancherNodeExecutorPlugin nodeExecutorPlugin = new RancherNodeExecutorPlugin();
        String[] command = { "ls" };
        nodeAttributes.put("type", "container");
        nodeAttributes.put(CONFIG_ACCESSKEY_PATH, "access_key");
        nodeAttributes.put(CONFIG_SECRETKEY_PATH, "secret_key");
        when(node.getAttributes()).thenReturn(nodeAttributes);
        when(executionContext.getStorageTree()).thenReturn(storageTree);
        when(storageTree.getResource(anyString())).thenReturn(resource);
        when(resource.getContents()).thenReturn(resourceMeta);
        doThrow(new IOException("Storage failure.")).when(resourceMeta).writeContent(any());
        NodeExecutorResult result = nodeExecutorPlugin.executeCommand(executionContext, command, node);
        String message = "Storage failure.";
        assertEquals(message, result.getFailureMessage());
        assertEquals(StepFailureReason.IOFailure, result.getFailureReason());
        assertEquals(-1, result.getResultCode());
    }

    @Test
    public void testExecutorEmpty() throws IOException, InterruptedException {
        when(webSocketFileCopier.thisGetFile(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("");

        String[] command = { "ls" };
        nodeAttributes.put("type", "container");
        nodeAttributes.put(CONFIG_ACCESSKEY_PATH, "access_key");
        nodeAttributes.put(CONFIG_SECRETKEY_PATH, "secret_key");
        nodeAttributes.put("execute", "execute");
        nodeAttributes.put(RANCHER_CONFIG_EXECUTOR_TIMEOUT, "30");
        when(node.getAttributes()).thenReturn(nodeAttributes);

        when(storage.loadStoragePathData(nodeAttributes.get(CONFIG_ACCESSKEY_PATH))).thenReturn("access");
        when(storage.loadStoragePathData(nodeAttributes.get(CONFIG_SECRETKEY_PATH))).thenReturn("secret");

        when(executionContext.getFramework()).thenReturn(framework);
        when(framework.getFrameworkProjectMgr()).thenReturn(projectManager);

        when(executionContext.getExecutionLogger()).thenReturn(executionLogger);
        when(executionContext.getDataContext()).thenReturn(dataContext);

        RancherNodeExecutorPlugin subject = new RancherNodeExecutorPlugin(rancherWebSocketListener, webSocketFileCopier, storage);
        NodeExecutorResult result = subject.executeCommand(executionContext, command, node);
        String message = "Process  did not return a status.";
        assertEquals(message, result.getFailureMessage());
        assertEquals(StepFailureReason.PluginFailed, result.getFailureReason());
        assertEquals(-1, result.getResultCode());
    }

    @Test
    public void testExecutorSuccess() throws IOException, InterruptedException {
        int exitStatus = 0;
        String fileContents = "123 " + exitStatus;
        testExecutor(fileContents);
    }

    @Test
    public void testExecutorError() throws IOException, InterruptedException {
        int exitStatus = 1;
        String fileContents = "123 " + exitStatus;
        testExecutor(fileContents);
    }

    public void testExecutor(String fileContents) throws IOException, InterruptedException {
        when(webSocketFileCopier.thisGetFile(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(fileContents);

        String[] command = { "ls" };
        nodeAttributes.put("type", "container");
        nodeAttributes.put(CONFIG_ACCESSKEY_PATH, "access_key");
        nodeAttributes.put(CONFIG_SECRETKEY_PATH, "secret_key");
        nodeAttributes.put("execute", "execute");
        nodeAttributes.put(RANCHER_CONFIG_EXECUTOR_TIMEOUT, "30");
        when(node.getAttributes()).thenReturn(nodeAttributes);

        when(storage.loadStoragePathData(nodeAttributes.get(CONFIG_ACCESSKEY_PATH))).thenReturn("access");
        when(storage.loadStoragePathData(nodeAttributes.get(CONFIG_SECRETKEY_PATH))).thenReturn("secret");

        when(executionContext.getFramework()).thenReturn(framework);
        when(framework.getFrameworkProjectMgr()).thenReturn(projectManager);

        when(executionContext.getExecutionLogger()).thenReturn(executionLogger);
        when(executionContext.getDataContext()).thenReturn(dataContext);

        RancherNodeExecutorPlugin subject = new RancherNodeExecutorPlugin(rancherWebSocketListener, webSocketFileCopier, storage);
        subject.executeCommand(executionContext, command, node);
        verify(executionLogger, times(3)).log(anyInt(), anyString());
    }
}
package com.bioraft.rundeck.rancher;

import com.dtolabs.rundeck.core.common.Framework;
import com.dtolabs.rundeck.core.common.INodeEntry;
import com.dtolabs.rundeck.core.common.NodeEntryImpl;
import com.dtolabs.rundeck.core.execution.ExecutionContext;
import com.dtolabs.rundeck.core.execution.service.FileCopierException;
import com.dtolabs.rundeck.core.storage.ResourceMeta;
import com.dtolabs.rundeck.core.storage.StorageTree;
import com.dtolabs.rundeck.plugins.PluginLogger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.rundeck.storage.api.Resource;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class RancherFileCopierTest {

    @Mock
    INodeEntry node;

    @Mock
    RancherWebSocketListener listener;

    @Mock
    PluginLogger logger;

    @Mock
    ExecutionContext executionContext;

    @Mock
    Framework framework;

    @Mock
    StorageTree storageTree;

    @Mock
    Resource<ResourceMeta> treeResource;

    @Mock
    ResourceMeta contents;

    @Mock
    NodeEntryImpl host;

    Map<String, String> map;

    Map<String, Object> cfg;
    @Before
    public void setUp() {
        cfg = new HashMap<>();
        map = Stream
                .of(new String[][]{{"services", "https://rancher.example.com/v2-beta/"},
                        {"self", "https://rancher.example.com/v2-beta/"},
                        {"type", "container"},
                        {RancherShared.CONFIG_ACCESSKEY_PATH, "keys/rancher/access.key"},
                        {RancherShared.CONFIG_SECRETKEY_PATH, "keys/rancher/secret.key"}})
                .collect(Collectors.toMap(data -> data[0], data -> data[1]));
        when(node.getAttributes()).thenReturn(map);
        when(treeResource.getContents()).thenReturn(contents);
        when(storageTree.getResource(anyString())).thenReturn(treeResource);
        when(executionContext.getStorageTree()).thenReturn(storageTree);
        when(executionContext.getExecutionLogger()).thenReturn(logger);
        when(executionContext.getFramework()).thenReturn(framework);
        when(framework.getProjectProperty(anyString(), anyString())).thenReturn("");
        when(framework.createFrameworkNode()).thenReturn(host);
        when(framework.getProperty(anyString())).thenReturn("/tmp/");
        when(host.getOsFamily()).thenReturn("unix");
    }

    @Test
    public void validateDefaultConstructor() {
        RancherFileCopier subject = new RancherFileCopier();
        assertNotNull(subject);
        String description = subject.getDescription().getDescription();
        assertTrue(description, description.startsWith("Copies a file"));
    }

    @Test
    public void testCopyFile() throws FileCopierException, IOException, InterruptedException {
        RancherFileCopier subject = new RancherFileCopier(listener);
        String destination = "/tmp/file.txt";
        File file = new File(
                Objects.requireNonNull(getClass().getClassLoader().getResource("stack.json")).getFile()
        );
        subject.copyFile(executionContext, file, node, destination);
        verify(listener, times(1)).putFile(anyString(), anyString(), anyString(), eq(file), anyString());
    }

    @Test(expected = FileCopierException.class)
    public void copyScriptThrowListener() throws FileCopierException, IOException, InterruptedException {
        doThrow(new IOException()).when(listener).putFile(anyString(), anyString(), anyString(), any(File.class), anyString());
        RancherFileCopier subject = new RancherFileCopier(listener);
        String destination = "/tmp/file.txt";
        String file = "foo";
        subject.copyScriptContent(executionContext, file, node, destination);
    }

    @Test(expected = FileCopierException.class)
    public void servicesAreUnsupported() throws FileCopierException {
        RancherFileCopier subject = new RancherFileCopier(listener);
        String destination = "/tmp/file.txt";
        map.put("type", "service");
        File file = new File(
                Objects.requireNonNull(getClass().getClassLoader().getResource("stack.json")).getFile()
        );
        subject.copyFile(executionContext, file, node, destination);
    }

    @Test(expected = FileCopierException.class)
    public void throwExceptionIfNoPasswordPath() throws FileCopierException {
        File file = new File(
                Objects.requireNonNull(getClass().getClassLoader().getResource("stack.json")).getFile()
        );
        map.put(RancherShared.CONFIG_ACCESSKEY_PATH, null);
        map.put(RancherShared.CONFIG_SECRETKEY_PATH, null);
        RancherFileCopier subject = new RancherFileCopier(listener);
        String destination = "/tmp/file.txt";
        subject.copyFile(executionContext, file, node, destination);

    }

    @Test(expected = FileCopierException.class)
    public void throwListenerException() throws FileCopierException, IOException, InterruptedException {
        File file = new File(
            Objects.requireNonNull(getClass().getClassLoader().getResource("stack.json")).getFile()
        );
        doThrow(new IOException()).when(listener).putFile(anyString(), anyString(), anyString(), eq(file), anyString());
        map.put(RancherShared.CONFIG_ACCESSKEY_PATH, null);
        map.put(RancherShared.CONFIG_SECRETKEY_PATH, null);
        RancherFileCopier subject = new RancherFileCopier(listener);
        String destination = "/tmp/file.txt";
        subject.copyFile(executionContext, file, node, destination);

    }
}

package com.bioraft.rundeck.rancher;

import com.dtolabs.rundeck.core.common.Framework;
import com.dtolabs.rundeck.core.common.INodeEntry;
import com.dtolabs.rundeck.core.common.ProjectManager;
import com.dtolabs.rundeck.core.execution.ExecutionContext;
import com.dtolabs.rundeck.core.execution.service.FileCopierException;
import com.dtolabs.rundeck.core.storage.ResourceMeta;
import com.dtolabs.rundeck.core.storage.StorageTree;
import com.dtolabs.rundeck.core.utils.IPropertyLookup;
import com.dtolabs.rundeck.plugins.PluginLogger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.rundeck.storage.api.Resource;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.bioraft.rundeck.rancher.Constants.CONFIG_ACCESSKEY_PATH;
import static com.bioraft.rundeck.rancher.Constants.CONFIG_SECRETKEY_PATH;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
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
    ProjectManager projectManager;

//    @Mock
//    IRundeckProject rundeckProject;

    @Mock
    IPropertyLookup propertyLookup;

    Map<String, String> map;

    @Before
    public void setUp() {
        when(treeResource.getContents()).thenReturn(contents);
        when(storageTree.getResource(anyString())).thenReturn(treeResource);
        when(executionContext.getStorageTree()).thenReturn(storageTree);
        when(executionContext.getExecutionLogger()).thenReturn(logger);
        when(executionContext.getFramework()).thenReturn(framework);
//        when(framework.getProjectProperty(anyString(), anyString())).thenReturn("");

//        when(framework.createFrameworkNode()).thenReturn(host);
//        when(framework.getProperty(anyString())).thenReturn("/tmp/");
//        when(host.getOsFamily()).thenReturn("unix");
    }

    private void setUpContainer() {
        map = Stream
                .of(new String[][]{
                        {"type", "container"},
                        {"services", "https://rancher.example.com/v2-beta/projects/1a8/containers/1i160059/services"},
                        {"self", "https://rancher.example.com/v2-beta/projects/1a8/containers/1i22"},
                        {CONFIG_ACCESSKEY_PATH, "keys/rancher/access.key"},
                        {CONFIG_SECRETKEY_PATH, "keys/rancher/secret.key"}})
                .collect(Collectors.toMap(data -> data[0], data -> data[1]));
        when(node.getAttributes()).thenReturn(map);
    }

    private void setUpService() {
        map = Stream
                .of(new String[][]{{"type", "service"},
                        {"self", "https://rancher.example.com/v2-beta/projects/1a8/services/1s7 "},
                        {"instanceIds", "1i21,1i22"},
                        {CONFIG_ACCESSKEY_PATH, "keys/rancher/access.key"},
                        {CONFIG_SECRETKEY_PATH, "keys/rancher/secret.key"}})
                .collect(Collectors.toMap(data -> data[0], data -> data[1]));
        when(node.getAttributes()).thenReturn(map);
    }

    @Test
    public void validateDefaultConstructor() {
        RancherFileCopier subject = new RancherFileCopier();
        assertNotNull(subject);
        String description = subject.getDescription().getDescription();
        assertTrue(description, description.startsWith("Copies a file"));
    }

    @Test
    public void testCopyFileToContainer() throws FileCopierException, IOException, InterruptedException {
        this.setUpContainer();
        RancherFileCopier subject = new RancherFileCopier(listener);
        String destination = "/tmp/file.txt";
        File file = new File(
                Objects.requireNonNull(getClass().getClassLoader().getResource("stack.json")).getFile()
        );
        subject.copyFile(executionContext, file, node, destination);
        verify(listener, times(1)).putFile(eq(null), anyString(), anyString(), eq(file), anyString());
    }

    @Test
    public void testCopyFileToService() throws FileCopierException, IOException, InterruptedException {
        this.setUpService();
        RancherFileCopier subject = new RancherFileCopier(listener);
        String destination = "/tmp/file.txt";
        File file = new File(
                Objects.requireNonNull(getClass().getClassLoader().getResource("stack.json")).getFile()
        );
        subject.copyFile(executionContext, file, node, destination);
        verify(listener, times(2)).putFile(startsWith("https://rancher.example.com/v2-beta/projects/1a8/containers/"), anyString(), anyString(), eq(file), anyString());
    }

    @Test(expected = FileCopierException.class)
    public void throwExceptionIfNoPasswordPath() throws FileCopierException {
        this.setUpContainer();
        File file = new File(
                Objects.requireNonNull(getClass().getClassLoader().getResource("stack.json")).getFile()
        );
        map.put(CONFIG_ACCESSKEY_PATH, null);
        map.put(CONFIG_SECRETKEY_PATH, null);
        RancherFileCopier subject = new RancherFileCopier(listener);
        String destination = "/tmp/file.txt";
        subject.copyFile(executionContext, file, node, destination);

    }

    @Test(expected = FileCopierException.class)
    public void throwListenerException() throws FileCopierException {
        this.setUpContainer();
        File file = new File(
            Objects.requireNonNull(getClass().getClassLoader().getResource("stack.json")).getFile()
        );
        map.put(CONFIG_ACCESSKEY_PATH, null);
        map.put(CONFIG_SECRETKEY_PATH, null);
        RancherFileCopier subject = new RancherFileCopier(listener);
        String destination = "/tmp/file.txt";
        subject.copyFile(executionContext, file, node, destination);
    }

//    @Test(expected = FileCopierException.class)
//    public void throwListenerExceptionIfInterrupted() throws FileCopierException, InterruptedException, IOException {
//        this.setUpContainer();
//        File file = new File(
//                Objects.requireNonNull(getClass().getClassLoader().getResource("stack.json")).getFile()
//        );
//        doThrow(new InterruptedException()).when(listener).putFile(anyString(), anyString(), anyString(), eq(file), anyString());
//        RancherFileCopier subject = new RancherFileCopier(listener);
//        String destination = "/tmp/file.txt";
//        subject.copyFile(executionContext, file, node, destination);
//
//    }

    @Test
    public void testCopyFileToContainerNoPath() throws FileCopierException, IOException, InterruptedException {
        this.setUpContainer();

//        when(rundeckProject.hasProperty("project.project.file-copy-destination-dir")).thenReturn(true);
//        when(rundeckProject.getProperty("project.project.file-copy-destination-dir")).thenReturn("/tmp");
//
//        when(propertyLookup.hasProperty("framework.framework.file-copy-destination-dir")).thenReturn(true);
//        when(propertyLookup.getProperty("framework.framework.file-copy-destination-dir")).thenReturn("/tmp");

        when(framework.getPropertyLookup()).thenReturn(propertyLookup);
        when(framework.getFrameworkProjectMgr()).thenReturn(projectManager);

//        when(projectManager.getFrameworkProject(anyString())).thenReturn(rundeckProject);

        RancherFileCopier subject = new RancherFileCopier(listener);
        File file = new File(
                Objects.requireNonNull(getClass().getClassLoader().getResource("stack.json")).getFile()
        );
        subject.copyFile(executionContext, file, node, null);
        verify(listener, times(1)).putFile(eq(null), anyString(), anyString(), eq(file), anyString());
    }
}

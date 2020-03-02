package com.bioraft.rundeck.rancher;

import com.dtolabs.rundeck.core.common.INodeEntry;
import com.dtolabs.rundeck.core.execution.ExecutionContext;
import com.dtolabs.rundeck.core.execution.workflow.steps.node.NodeStepException;
import com.dtolabs.rundeck.core.storage.ResourceMeta;
import com.dtolabs.rundeck.core.storage.StorageTree;
import com.dtolabs.rundeck.plugins.PluginLogger;
import com.dtolabs.rundeck.plugins.step.PluginStepContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.Call;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.rundeck.storage.api.Resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RancherManageServiceTest {
    @Mock
    HttpClient client;

    @Mock
    PluginStepContext ctx;

    @Mock
    PluginLogger logger;

    @Mock
    ExecutionContext executionContext;

    @Mock
    Resource<ResourceMeta> treeResource;

    @Mock
    StorageTree storageTree;

    @Mock
    ResourceMeta contents;

    @Mock
    INodeEntry node;

    Map<String, Object> cfg;

    Map<String, String> map;

    @Before
    public void setUp() {
        cfg = new HashMap<>();
        map = Stream
                .of(new String[][]{{"services", "https://rancher.example.com/v2-beta/"},
                        {"self", "https://rancher.example.com/v2-beta/"},
                        {"type", "service"},
                        {RancherShared.CONFIG_ACCESSKEY_PATH, "keys/rancher/access.key"},
                        {RancherShared.CONFIG_SECRETKEY_PATH, "keys/rancher/secret.key"},})
                .collect(Collectors.toMap(data -> data[0], data -> data[1]));
        Mockito.when(node.getAttributes()).thenReturn(map);
        Mockito.when(ctx.getLogger()).thenReturn(logger);
        Mockito.when(ctx.getExecutionContext()).thenReturn(executionContext);
        Mockito.when(executionContext.getStorageTree()).thenReturn(storageTree);
        Mockito.when(storageTree.getResource(anyString())).thenReturn(treeResource);
        Mockito.when(treeResource.getContents()).thenReturn(contents);
    }

    @Test
    public void validateDefaultConstructor() {
        RancherManageService subject = new RancherManageService();
        assertNotNull(subject);
    }

    @Test
    public void testActivate() throws IOException, NodeStepException {
        RancherManageService subject = new RancherManageService(client);
        cfg.put("action", "activate");
        ObjectMapper mapper = new ObjectMapper();

        String text = readFromInputStream(getResourceStream("service.json"));
        ObjectNode json1 = (ObjectNode) mapper.readTree(text);
        json1.put("state", "inactive");
        ObjectNode json2 = (ObjectNode) mapper.readTree(text);

        when(client.get(any())).thenReturn(json1);
        when(client.post(any(), eq(""))).thenReturn(json2);
        subject.executeNodeStep(ctx, cfg, node);
        verify(client, times(1)).get(any());
        verify(client, times(1)).post(any(), eq(""));
    }

    @Test
    public void testDeactivate() throws IOException, NodeStepException {
        RancherManageService subject = new RancherManageService(client);
        cfg.put("action", "deactivate");
        ObjectMapper mapper = new ObjectMapper();

        String text = readFromInputStream(getResourceStream("service.json"));
        ObjectNode json1 = (ObjectNode) mapper.readTree(text);
        ObjectNode json2 = (ObjectNode) mapper.readTree(text);

        when(client.get(any())).thenReturn(json1);
        when(client.post(any(), eq(""))).thenReturn(json2);
        subject.executeNodeStep(ctx, cfg, node);
        verify(client, times(1)).get(any());
        verify(client, times(1)).post(any(), eq(""));
    }

    @Test
    public void testRestart() throws IOException, NodeStepException {
        RancherManageService subject = new RancherManageService(client);
        cfg.put("action", "restart");
        ObjectMapper mapper = new ObjectMapper();

        String text = readFromInputStream(getResourceStream("service.json"));
        ObjectNode json1 = (ObjectNode) mapper.readTree(text);
        ObjectNode json2 = (ObjectNode) mapper.readTree(text);

        when(client.get(any())).thenReturn(json1);
        when(client.post(any(), eq(""))).thenReturn(json2);
        subject.executeNodeStep(ctx, cfg, node);
        verify(client, times(1)).get(any());
        verify(client, times(1)).post(any(), eq(""));
    }

    @Test(expected = NodeStepException.class)
    public void testUnsupportedAction() throws IOException, NodeStepException {
        cfg.put("action", "unsupported");
        ObjectMapper mapper = new ObjectMapper();

        String text = readFromInputStream(getResourceStream("service.json"));
        ObjectNode json1 = (ObjectNode) mapper.readTree(text);
        ObjectNode json2 = (ObjectNode) mapper.readTree(text);

        when(client.get(any())).thenReturn(json1);

        try {
            RancherManageService subject = new RancherManageService(client);
            subject.executeNodeStep(ctx, cfg, node);
        } catch (NodeStepException e) {
            assertEquals("Invalid action: unsupported", e.getMessage());
            throw e;
        }
    }

    @Test(expected = NodeStepException.class)
    public void testActivateAlreadyActive() throws IOException, NodeStepException {
        map.put("type", "container");
        cfg.put("action", "activate");

        ObjectMapper mapper = new ObjectMapper();
        String text = readFromInputStream(getResourceStream("services.json"));
        JsonNode json1 = mapper.readTree(text);

        when(client.get(anyString())).thenReturn(json1);

        try {
            RancherManageService subject = new RancherManageService(client);
            subject.executeNodeStep(ctx, cfg, node);
        } catch (NodeStepException e) {
            assertEquals("Service state is already active", e.getMessage());
            throw e;
        }
    }

    @Test(expected = NodeStepException.class)
    public void testDeactivateAlreadyInactive() throws IOException, NodeStepException {
        cfg.put("action", "deactivate");

        ObjectMapper mapper = new ObjectMapper();
        String text = readFromInputStream(getResourceStream("service.json"));
        ObjectNode json1 = (ObjectNode) mapper.readTree(text);
        json1.put("state", "inactive");

        when(client.get(anyString())).thenReturn(json1);

        try {
            RancherManageService subject = new RancherManageService(client);
            subject.executeNodeStep(ctx, cfg, node);
        } catch (NodeStepException e) {
            assertEquals("Service state must be running, was inactive", e.getMessage());
            throw e;
        }
    }

    private InputStream getResourceStream(String resource) {
        ClassLoader classLoader = getClass().getClassLoader();
        InputStream stream = classLoader.getResourceAsStream(resource);
        if (stream == null) throw new AssertionError();
        return stream;
    }

    private String readFromInputStream(InputStream inputStream) throws IOException {
        StringBuilder resultStringBuilder = new StringBuilder();
        InputStreamReader reader = new InputStreamReader(inputStream);
        try (BufferedReader br = new BufferedReader(reader)) {
            String line;
            while ((line = br.readLine()) != null) {
                resultStringBuilder.append(line).append("\n");
            }
        }
        return resultStringBuilder.toString();
    }
}

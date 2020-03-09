package com.bioraft.rundeck.rancher;

import com.dtolabs.rundeck.core.execution.workflow.steps.node.NodeStepException;
import com.dtolabs.rundeck.plugins.PluginLogger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Iterator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.*;

public class RancherLaunchConfigTest {

    final String name = "node";

    ObjectNode objectNode;
    ObjectNode reference;

    @Mock
    PluginLogger logger;

    @Before
    public void setUp() throws Exception {
        reference = (ObjectNode) readFromInputStream(getResourceStream());
        objectNode = (ObjectNode) readFromInputStream(getResourceStream());
        MockitoAnnotations.initMocks(this);
    }

    @Test
    /*
     * If nothing is changed, the launchConfig should be unchanged.
     */
    public void update() throws NodeStepException {
        verify(logger,never()).log(anyInt(), anyString());
        RancherLaunchConfig rancherLaunchConfig = new RancherLaunchConfig(name, objectNode, logger);
        assertEquals(reference, rancherLaunchConfig.update());
    }

    @Test
    /*
     * If nothing is changed, the launchConfig should be unchanged.
     */
    public void removeField() throws NodeStepException {
        verify(logger,never()).log(anyInt(), anyString());
        RancherLaunchConfig rancherLaunchConfig = new RancherLaunchConfig(name, objectNode, logger);
        rancherLaunchConfig.removeField("NoSuchField", "NoSuchValue");
        assertEquals(reference, rancherLaunchConfig.update());
    }

    @Test
    /*
     * If nothing is changed, the launchConfig should be unchanged.
     */
    public void removeEmptyField() throws NodeStepException {
        verify(logger,never()).log(anyInt(), anyString());
        RancherLaunchConfig rancherLaunchConfig = new RancherLaunchConfig(name, objectNode, logger);
        rancherLaunchConfig.setField("logConfig", "{\"type\": \"logConfig\"}");
        assertEquals(reference, rancherLaunchConfig.update());
        rancherLaunchConfig.removeField("logConfig", "[\"type\"]");
        assertNotEquals(reference, rancherLaunchConfig.update());
        rancherLaunchConfig.removeField("logConfig", "[\"type2\"]");
        assertNotEquals(reference, rancherLaunchConfig.update());
    }

    @Test
    /*
     * If nothing is changed, the launchConfig should be unchanged.
     */
    public void removeNoField() throws NodeStepException {
        verify(logger,never()).log(anyInt(), anyString());
        objectNode.remove("logConfig");
        RancherLaunchConfig rancherLaunchConfig = new RancherLaunchConfig(name, objectNode, logger);
        assertNotEquals(reference, rancherLaunchConfig.update());
        rancherLaunchConfig.removeField("logConfig", "[\"type\"]");
        assertNotEquals(reference, rancherLaunchConfig.update());

        rancherLaunchConfig.setField("logConfig", "{\"type\": \"logConfig\"}");
        ObjectNode lc = rancherLaunchConfig.update();
        String text = lc.path("logConfig").path("type").asText();
        assertEquals("logConfig", text);
    }

    @Test
    /*
     * If nothing is changed, the launchConfig should be unchanged.
     */
    public void removeNullField() throws NodeStepException {
        verify(logger,never()).log(anyInt(), anyString());
        objectNode.put("logConfig", (String) null);
        RancherLaunchConfig rancherLaunchConfig = new RancherLaunchConfig(name, objectNode, logger);
        assertNotEquals(reference, rancherLaunchConfig.update());
        rancherLaunchConfig.removeField("logConfig", "[\"type\"]");
        assertNotEquals(reference, rancherLaunchConfig.update());

        rancherLaunchConfig.setField("logConfig", "{\"type\": \"logConfig\"}");
        ObjectNode lc = rancherLaunchConfig.update();
        String text = lc.path("logConfig").path("type").asText();
        assertEquals("logConfig", text);
    }

    @Test(expected = NodeStepException.class)
    /*
     * If nothing is changed, the launchConfig should be unchanged.
     */
    public void removeInvalidField() throws NodeStepException {
        verify(logger,never()).log(anyInt(), anyString());
        RancherLaunchConfig rancherLaunchConfig = new RancherLaunchConfig(name, objectNode, logger);
        rancherLaunchConfig.removeField("environment", "[\"NoSuchValue\",]");
        assertEquals(reference, rancherLaunchConfig.update());
    }

    @Test(expected = NodeStepException.class)
    /*
     * If nothing is changed, the launchConfig should be unchanged.
     */
    public void addInvalidField() throws NodeStepException {
        verify(logger,never()).log(anyInt(), anyString());
        RancherLaunchConfig rancherLaunchConfig = new RancherLaunchConfig(name, objectNode, logger);
        rancherLaunchConfig.setField("NoSuchField", "NoSuchValue");
    }

    @Test
    /*
     * Test by inserting values and verifying object changes, then removing them and ensuring
     * object has return to its original value.
     */
    public void updateEnvironment() throws NodeStepException {
        RancherLaunchConfig rancherLaunchConfig = new RancherLaunchConfig(name, objectNode, logger);
        rancherLaunchConfig.setEnvironment("{\"VAR\": \"value\"}");
        assertNotEquals(reference, rancherLaunchConfig.update());
        verify(logger, times(1)).log(anyInt(), anyString());

        rancherLaunchConfig.removeEnvironment("[\"VAR\"]");
        ObjectNode result = rancherLaunchConfig.update();
        verify(logger, times(3)).log(anyInt(), anyString());

        assertEquals(reference, result);
    }

    @Test
    /*
     * Test by inserting values and verifying object changes, then removing them and ensuring
     * object has return to its original value.
     */
    public void updateEnvironmentNoMatch() throws NodeStepException {
        RancherLaunchConfig rancherLaunchConfig = new RancherLaunchConfig(name, objectNode, logger);
        rancherLaunchConfig.setEnvironment("{\"VAR\": \"value\"}");
        assertNotEquals(reference, rancherLaunchConfig.update());
        verify(logger, times(1)).log(anyInt(), anyString());

        rancherLaunchConfig.removeEnvironment("[\"NO_VAR\"]");
        ObjectNode result = rancherLaunchConfig.update();
        verify(logger, times(3)).log(anyInt(), anyString());

        assertNotEquals(reference, result);
    }

    @Test
    /*
     * Test by inserting values and verifying object changes, then removing them and ensuring
     * object has return to its original value.
     */
    public void updateLabels() throws NodeStepException {
        RancherLaunchConfig rancherLaunchConfig = new RancherLaunchConfig(name, objectNode, logger);
        rancherLaunchConfig.setLabels("\"com.example.SERVICE\": \"service\", \"com.example.SITE\": \"site\"");
        assertNotEquals(reference, rancherLaunchConfig.update());
        verify(logger, times(2)).log(anyInt(), anyString());

        rancherLaunchConfig.removeLabels("\"com.example.SERVICE\", \"com.example.SITE\"");
        ObjectNode result = rancherLaunchConfig.update();
        verify(logger, times(6)).log(anyInt(), anyString());

        assertEquals(reference, result);
    }

    @Test
    /*
     * Test by inserting values and verifying object changes, then removing them and ensuring
     * object has return to its original value.
     */
    public void updateMounts() throws NodeStepException {
        RancherLaunchConfig rancherLaunchConfig = new RancherLaunchConfig(name, objectNode, logger);
        int originalCount = 0;
        Iterator<JsonNode> elements = objectNode.path("dataVolumes").elements();
        while (elements.hasNext()) {
            elements.next();
            originalCount++;
        }
        ObjectNode result;
        int newCount;

        rancherLaunchConfig.setDataVolumes("\"/source1:/mountpoint1\"");
        result = rancherLaunchConfig.update();
        elements = result.path("dataVolumes").elements();
        newCount = 0;
        while (elements.hasNext()) {
            elements.next();
            newCount++;
        }
        assertEquals(originalCount, newCount);
        assertEquals(result, reference);

        String changedMount = "/source1:/mountpoint1:ro";
        rancherLaunchConfig.setDataVolumes("[\"" + changedMount + "\"]");
        result = rancherLaunchConfig.update();
        elements = result.path("dataVolumes").elements();
        newCount = 0;
        while (elements.hasNext()) {
            JsonNode element = elements.next();
            assertEquals(changedMount, element.asText());
            newCount++;
        }
        assertEquals(originalCount, newCount);
        assertNotEquals(result, reference);

        String newMount = "/source3:/mountpoint3";
        rancherLaunchConfig.setDataVolumes("[\"" + newMount + "\"]");
        result = rancherLaunchConfig.update();
        elements = result.path("dataVolumes").elements();
        newCount = 0;
        while (elements.hasNext()) {
            JsonNode element = elements.next();
            newCount++;
            // This is a little hacky, but assures us the order of the array does not cause the test to fail.
            if (element.asText().equals(changedMount)) {
                assertEquals(changedMount, element.asText());
            } else {
                assertEquals(newMount, element.asText());
            }
        }
        assertEquals(originalCount + 1, newCount);
    }

    @Test(expected = NodeStepException.class)
    /*
     * If nothing is changed, the launchConfig should be unchanged.
     */
    public void invalidDataVolumes() throws NodeStepException {
        verify(logger,never()).log(anyInt(), anyString());
        RancherLaunchConfig rancherLaunchConfig = new RancherLaunchConfig(name, objectNode, logger);
        rancherLaunchConfig.setDataVolumes("[\"NoSuchValue\",]");
        assertEquals(reference, rancherLaunchConfig.update());
    }

    @Test
    /*
     * If nothing is changed, the launchConfig should be unchanged.
     */
    public void hasNullDataVolumes() throws NodeStepException {
        objectNode.remove("dataVolumes");
        objectNode.put("dataVolumes", (Short) null);
        verify(logger,never()).log(anyInt(), anyString());
        RancherLaunchConfig rancherLaunchConfig = new RancherLaunchConfig(name, objectNode, logger);
        rancherLaunchConfig.setDataVolumes("[\"/source1:/mountpoint1\"]");
        assertEquals(reference, rancherLaunchConfig.update());
    }

    @Test
    /*
     * If nothing is changed, the launchConfig should be unchanged.
     */
    public void hasNoDataVolumes() throws NodeStepException {
        objectNode.remove("dataVolumes");
        verify(logger,never()).log(anyInt(), anyString());
        RancherLaunchConfig rancherLaunchConfig = new RancherLaunchConfig(name, objectNode, logger);
        rancherLaunchConfig.setDataVolumes("[\"/source1:/mountpoint1\"]");
        assertEquals(reference, rancherLaunchConfig.update());
    }

    @Test
    /*
     * If nothing is changed, the launchConfig should be unchanged.
     */
    public void addSameSecrets() throws NodeStepException {
        verify(logger,never()).log(anyInt(), anyString());
        RancherLaunchConfig rancherLaunchConfig = new RancherLaunchConfig(name, objectNode, logger);
        rancherLaunchConfig.setSecrets("1se2");
        assertEquals(reference, rancherLaunchConfig.update());
    }

    @Test
    /*
     * If nothing is changed, the launchConfig should be unchanged.
     */
    public void addBothSameSecrets() throws NodeStepException {
        verify(logger,never()).log(anyInt(), anyString());
        RancherLaunchConfig rancherLaunchConfig = new RancherLaunchConfig(name, objectNode, logger);
        rancherLaunchConfig.setSecrets("1se1,1se2");
        assertEquals(reference, rancherLaunchConfig.update());
    }

    @Test
    /*
     * If nothing is changed, the launchConfig should be unchanged.
     */
    public void addHasNoSecrets() throws NodeStepException {
        objectNode.remove("secrets");
        verify(logger,never()).log(anyInt(), anyString());
        RancherLaunchConfig rancherLaunchConfig = new RancherLaunchConfig(name, objectNode, logger);
        rancherLaunchConfig.setSecrets("1se1,1se2");
        assertEquals(reference, rancherLaunchConfig.update());
    }

    @Test
    /*
     * If nothing is changed, the launchConfig should be unchanged.
     */
    public void addHasNullSecrets() throws NodeStepException {
        objectNode.remove("secrets");
        objectNode.put("secrets", (Short) null);
        verify(logger,never()).log(anyInt(), anyString());
        RancherLaunchConfig rancherLaunchConfig = new RancherLaunchConfig(name, objectNode, logger);
        rancherLaunchConfig.setSecrets("1se1,1se2");
        assertEquals(reference, rancherLaunchConfig.update());
    }

    @Test
    /*
     * If nothing is changed, the launchConfig should be unchanged.
     */
    public void addHasEmptySecrets() throws NodeStepException {
        objectNode.putArray("secrets");
        verify(logger,never()).log(anyInt(), anyString());
        RancherLaunchConfig rancherLaunchConfig = new RancherLaunchConfig(name, objectNode, logger);
        rancherLaunchConfig.setSecrets("1se1,1se2");
        assertEquals(reference, rancherLaunchConfig.update());
    }

    protected InputStream getResourceStream() {
        ClassLoader classLoader = getClass().getClassLoader();
        InputStream stream = classLoader.getResourceAsStream("launchConfig.json");
        if (stream == null) throw new AssertionError();
        return stream;
    }

    protected JsonNode readFromInputStream(InputStream inputStream) throws IOException {
        StringBuilder resultStringBuilder = new StringBuilder();
        InputStreamReader reader = new InputStreamReader(inputStream);
        try (BufferedReader br = new BufferedReader(reader)) {
            String line;
            while ((line = br.readLine()) != null) {
                resultStringBuilder.append(line).append("\n");
            }
        }
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readTree(resultStringBuilder.toString());
    }
}
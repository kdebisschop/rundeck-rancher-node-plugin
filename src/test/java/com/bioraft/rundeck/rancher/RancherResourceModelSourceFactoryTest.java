package com.bioraft.rundeck.rancher;

import com.dtolabs.rundeck.core.resources.ResourceModelSource;
import com.dtolabs.rundeck.core.plugins.configuration.ConfigurationException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Properties;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class RancherResourceModelSourceFactoryTest {

    @Mock
    Properties configuration;

    @Test
    public void validateDefaultConstructor() {
        RancherResourceModelSourceFactory subject = new RancherResourceModelSourceFactory();
        assertNotNull(subject);
        String description = subject.getDescription().getDescription();
        assertTrue(description, description.endsWith("remote rancher node."));
    }


    @Test(expected = ConfigurationException.class)
    public void testConfigurationException() throws ConfigurationException {
        RancherResourceModelSourceFactory subject = new RancherResourceModelSourceFactory();
        assertNotNull(subject);
        String description = subject.getDescription().getDescription();
        assertTrue(description, description.endsWith("remote rancher node."));

        ResourceModelSource source = subject.createResourceModelSource(configuration);
        assertNotNull(source);
    }
}

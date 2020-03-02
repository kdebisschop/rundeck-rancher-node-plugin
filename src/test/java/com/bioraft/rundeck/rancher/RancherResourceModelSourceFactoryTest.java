package com.bioraft.rundeck.rancher;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertNotNull;

@RunWith(MockitoJUnitRunner.class)
public class RancherResourceModelSourceFactoryTest {

    @Test
    public void validateDefaultConstructor() {
        RancherResourceModelSourceFactory subject = new RancherResourceModelSourceFactory();
        assertNotNull(subject);
    }
}

package com.bioraft.rundeck.rancher;

import org.junit.Test;

public class ConstantsTest {
    @Test(expected = IllegalStateException.class)
    public void tsstThatConstructorThrowsExcption () {
        new Constants();
    }
}

package com.bioraft.rundeck.rancher;

import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

public class LogMessageTest {

    private final String string = "string";
    private byte[] bytes;

    @Before
    public void setup() {
        bytes = string.getBytes();
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidStreamThrowsException() throws IllegalArgumentException {
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        new LogMessage(3, byteBuffer);
    }

    @Test
    public void streamIsConstructor() {
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        LogMessage message = new LogMessage(0, byteBuffer);
        assertEquals(0, message.stream().id());
        assertEquals(string, StandardCharsets.UTF_8.decode(byteBuffer).toString());
    }
}

package com.bioraft.rundeck.rancher;

import com.dtolabs.rundeck.core.execution.ExecutionListener;
import okhttp3.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

public class RancherWebSocketListenerTest {

    MockWebServer mockWebServer;

    @Mock
    ExecutionListener listener;

    @Mock
    OkHttpClient client;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.initMocks(this);
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @After
    public void teardown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    public void testConstructor() {
        RancherWebSocketListener listener = new RancherWebSocketListener();
        assertNotNull(listener);
    }

    @Test
    public void testMocakableConstructor() {
        RancherWebSocketListener listener = new RancherWebSocketListener(client);
        assertNotNull(listener);
    }

    @Test
    public void testRunJob() throws InterruptedException, IOException {
        String url = mockWebServer.url("/v2-beta/").toString();
        String accessKey = "access";
        String secretKey = "secret";
        String[] command = {"ls"};
        String temp = "";
        int timeout = 1;
        MockResponse mockedResponse = new MockResponse();
        mockedResponse.setResponseCode(200);
        mockedResponse.setBody("{\"url\":\"" + url + "\", \"token\":\"abcdef\"}");
        mockWebServer.enqueue(mockedResponse);
        MockResponse upgrade = new MockResponse()
                .setStatus("HTTP/1.1 101 Switching Protocols")
                .setHeader("Connection", "Upgrade")
                .setHeader("Upgrade", "websocket")
                .setHeader("Sec-WebSocket-Accept", "null");
        mockWebServer.enqueue(upgrade);
        doNothing().when(listener).log(anyInt(), anyString());
        RancherWebSocketListener.runJob(url, accessKey, secretKey, command, listener, temp, timeout);
        verify(listener, times(0)).log(anyInt(), anyString());
    }

    @Test(expected = IOException.class)
    public void throwExceptionWhenTokenFails() throws InterruptedException, IOException {
        String url = mockWebServer.url("/v2-beta/").toString();
        String accessKey = "access";
        String secretKey = "secret";
        String[] command = {"ls"};
        String temp = "";
        int timeout = 1;
        MockResponse mockedResponse = new MockResponse();
        mockedResponse.setResponseCode(200);
        mockedResponse.setBody("");
        mockWebServer.enqueue(mockedResponse);
        MockResponse upgrade = new MockResponse()
                .setStatus("HTTP/1.1 101 Switching Protocols")
                .setHeader("Connection", "Upgrade")
                .setHeader("Upgrade", "websocket")
                .setHeader("Sec-WebSocket-Accept", "null");
        mockWebServer.enqueue(upgrade);
        RancherWebSocketListener.runJob(url, accessKey, secretKey, command, listener, temp, timeout);
    }

    @Test(expected = IOException.class)
    public void throwExceptionWhenTokenInvalid() throws InterruptedException, IOException {
        String url = mockWebServer.url("/v2-beta/").toString();
        String accessKey = "access";
        String secretKey = "secret";
        String[] command = {"ls"};
        String temp = "";
        int timeout = 1;
        MockResponse mockedResponse = new MockResponse();
        mockedResponse.setResponseCode(200);
        mockedResponse.setBody("{");
        mockWebServer.enqueue(mockedResponse);
        MockResponse upgrade = new MockResponse()
                .setStatus("HTTP/1.1 101 Switching Protocols")
                .setHeader("Connection", "Upgrade")
                .setHeader("Upgrade", "websocket")
                .setHeader("Sec-WebSocket-Accept", "null");
        mockWebServer.enqueue(upgrade);
        RancherWebSocketListener.runJob(url, accessKey, secretKey, command, listener, temp, timeout);
    }

    @Test
    public void testLogDockerStream() {
        RancherWebSocketListener subject = new RancherWebSocketListener(listener, new StringBuilder());
        byte[] bytes = "abcdef".getBytes();
        doNothing().when(listener).log(anyInt(), anyString());
        subject.logDockerStream(bytes);
        // Buffer is designed to add a line feed at end of message.
        verify(listener, times(1)).log(2, "abcdef\n");
    }

    @Test
    public void testLogDockerStreamStderr() {
        RancherWebSocketListener subject = new RancherWebSocketListener(listener, new StringBuilder());
        byte[] bytes = ("STDERR_6v9ZvwThpU1FtyrlIBf4UIC8" + "abcde\n").getBytes();
        doNothing().when(listener).log(anyInt(), anyString());
        subject.logDockerStream(bytes);
        // Buffer is designed to add a line feed at end of message.
        verify(listener, times(1)).log(1, "abcde\n");
    }

    @Test
    public void testLogDockerStreamMixed() {
        RancherWebSocketListener subject = new RancherWebSocketListener(listener, new StringBuilder());
        byte[] bytes = ("STDERR_6v9ZvwThpU1FtyrlIBf4UIC8" + "abcde\nabcde\n").getBytes();
        doNothing().when(listener).log(anyInt(), anyString());
        subject.logDockerStream(bytes);
        // Buffer is designed to add a line feed at end of message.
        verify(listener, times(1)).log(1, "abcde\n");
        verify(listener, times(1)).log(2, "abcde\n");
    }

    @Test
    public void testLogDockerStreamMixed2() {
        RancherWebSocketListener subject = new RancherWebSocketListener(listener, new StringBuilder());
        byte[] bytes = ("abcde\n" + "STDERR_6v9ZvwThpU1FtyrlIBf4UIC8" + "abcde\n").getBytes();
        doNothing().when(listener).log(anyInt(), anyString());
        subject.logDockerStream(bytes);
        // Buffer is designed to add a line feed at end of message.
        verify(listener, times(1)).log(1, "abcde\n");
        verify(listener, times(1)).log(2, "abcde\n");
    }

    @Test
    public void testLogDockerStreamWithNoListener() {
        RancherWebSocketListener subject = new RancherWebSocketListener(null, new StringBuilder());
        byte[] bytes = "aaa".getBytes();
        subject.logDockerStream(bytes);
    }

    @Test
    public void testLogDockerStreamEmptyWithNoListener() {
        RancherWebSocketListener subject = new RancherWebSocketListener(null, new StringBuilder());
        byte[] bytes = "".getBytes();
        subject.logDockerStream(bytes);
    }

    @Test
    public void testLogDockerStreamStderrWithNoListener() {
        RancherWebSocketListener subject = new RancherWebSocketListener(null, new StringBuilder());
        byte[] bytes = ("STDERR_6v9ZvwThpU1FtyrlIBf4UIC8" + "aaa").getBytes();
        subject.logDockerStream(bytes);
    }
}

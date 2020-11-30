package com.bioraft.rundeck.rancher;

import com.dtolabs.rundeck.core.execution.ExecutionListener;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static com.bioraft.rundeck.rancher.Constants.STDERR_TOKEN;
import static org.junit.Assert.assertEquals;
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
        MockitoAnnotations.openMocks(this);
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
        mockedResponse.setBody("{\"url\":\"" + url + "\", \"token\":\"6chars\"}");
        RancherWebSocketListener serverListener = new RancherWebSocketListener();
        MockResponse upgrade = new MockResponse().withWebSocketUpgrade(serverListener);
        mockWebServer.enqueue(mockedResponse);
        mockWebServer.enqueue(upgrade.setBody("6chars-plus-more"));
        mockWebServer.takeRequest(200, TimeUnit.MILLISECONDS);
        mockWebServer.enqueue(mockedResponse);
        doNothing().when(listener).log(anyInt(), anyString());
        RancherWebSocketListener.runJob(url, accessKey, secretKey, command, listener, temp, timeout);
        assertEquals(2, mockWebServer.getRequestCount());
        mockWebServer.close();
        // If this was really working all the way, there would be some sort of logged data.
        // verify(listener, times(0)).log(anyInt(), anyString());
    }

//    @Test(expected = IOException.class)
//    public void throwExceptionWhenTokenFails() throws IOException, InterruptedException {
//        String url = mockWebServer.url("/v2-beta/").toString();
//        String accessKey = "access";
//        String secretKey = "secret";
//        String[] command = {"ls"};
//        String temp = "";
//        int timeout = 1;
//        MockResponse mockedResponse = new MockResponse();
//        mockedResponse.setResponseCode(200);
//        mockedResponse.setBody("");
//        mockWebServer.enqueue(mockedResponse);
//        MockResponse upgrade = new MockResponse()
//                .setStatus("HTTP/1.1 101 Switching Protocols")
//                .setHeader("Connection", "Upgrade")
//                .setHeader("Upgrade", "websocket")
//                .setHeader("Sec-WebSocket-Accept", "null");
//        mockWebServer.enqueue(upgrade);
//        RancherWebSocketListener.runJob(url, accessKey, secretKey, command, listener, temp, timeout);
//    }

    @Test(expected = IOException.class)
    public void throwExceptionWhenTokenInvalid() throws IOException, InterruptedException {
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
        byte[] bytes = "6chars".getBytes();
        doNothing().when(listener).log(anyInt(), anyString());
        subject.logDockerStream(bytes);
        // Buffer is designed to add a line feed at end of message.
        verify(listener, times(1)).log(2, "6chars");
    }

    @Test
    public void testLogDockerStreamStderr() {
        RancherWebSocketListener subject = new RancherWebSocketListener(listener, new StringBuilder());
        byte[] bytes = (STDERR_TOKEN + "string1\n" + STDERR_TOKEN + "string2\n" + STDERR_TOKEN + "string3\n").getBytes();
        doNothing().when(listener).log(anyInt(), anyString());
        subject.logDockerStream(bytes);
        // Buffer is designed to add a line feed at end of message.
        verify(listener, times(1)).log(1, "string1");
        verify(listener, times(1)).log(1, "string2");
        verify(listener, times(1)).log(1, "string3");
    }

    @Test
    public void testLogDockerStreamMixed() {
        RancherWebSocketListener subject = new RancherWebSocketListener(listener, new StringBuilder());
        byte[] bytes = (STDERR_TOKEN + "string1\nstring2\nstring3\n").getBytes();
        doNothing().when(listener).log(anyInt(), anyString());
        subject.logDockerStream(bytes);
        // Buffer is designed to add a line feed at end of message.
        verify(listener, times(1)).log(1, "string1");
        verify(listener, times(1)).log(2, "string2");
        verify(listener, times(1)).log(2, "string3");
    }

    @Test
    public void testLogDockerStreamMixed2() {
        RancherWebSocketListener subject = new RancherWebSocketListener(listener, new StringBuilder());
        byte[] bytes = ("string1\n" + STDERR_TOKEN + "string2\n").getBytes();
        doNothing().when(listener).log(anyInt(), anyString());
        subject.logDockerStream(bytes);
        // Buffer is designed to add a line feed at end of message.
        verify(listener, times(1)).log(2, "string1");
        verify(listener, times(1)).log(1, "string2");
    }

    @Test
    public void testLogDockerStreamMixed3() {
        RancherWebSocketListener subject = new RancherWebSocketListener(listener, new StringBuilder());
        byte[] bytes = ("string1" + STDERR_TOKEN + "string2\n").getBytes();
        doNothing().when(listener).log(anyInt(), anyString());
        subject.logDockerStream(bytes);
        // Buffer is designed to add a line feed at end of message.
        verify(listener, times(1)).log(2, "string1");
        verify(listener, times(1)).log(1, "string2");
    }

    @Test
    public void testLogDockerStreamMixed4() {
        RancherWebSocketListener subject = new RancherWebSocketListener(listener, new StringBuilder());
        byte[] bytes = ("string1" + STDERR_TOKEN + "string2\n" + STDERR_TOKEN + "string3").getBytes();
        doNothing().when(listener).log(anyInt(), anyString());
        subject.logDockerStream(bytes);
        // Buffer is designed to add a line feed at end of message.
        verify(listener, times(1)).log(2, "string1");
        verify(listener, times(1)).log(1, "string2");
        verify(listener, times(1)).log(1, "string3");
    }

    @Test
    public void testLogDockerStreamWithNoListener() {
        StringBuilder stringBuilder = new StringBuilder();
        RancherWebSocketListener subject = new RancherWebSocketListener(null, stringBuilder);
        byte[] bytes = "aaa".getBytes();
        subject.logDockerStream(bytes);
        assertEquals("aaa", stringBuilder.toString());
    }

    @Test
    public void testLogDockerStreamEmptyWithNoListener() {
        StringBuilder stringBuilder = new StringBuilder();
        RancherWebSocketListener subject = new RancherWebSocketListener(null, stringBuilder);
        byte[] bytes = "".getBytes();
        subject.logDockerStream(bytes);
        assertEquals("", stringBuilder.toString());
    }

    @Test
    public void testLogDockerStreamStderrWithNoListener() {
        StringBuilder stringBuilder = new StringBuilder();
        RancherWebSocketListener subject = new RancherWebSocketListener(null, stringBuilder);
        byte[] bytes = (STDERR_TOKEN + "aaa").getBytes();
        subject.logDockerStream(bytes);
        assertEquals(STDERR_TOKEN + "aaa", stringBuilder.toString());
    }
}

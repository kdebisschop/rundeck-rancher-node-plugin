package com.bioraft.rundeck.rancher;

import com.dtolabs.rundeck.core.execution.ExecutionListener;
import okhttp3.*;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mock;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class RancherWebSocketListenerTest {

    @Mock
    ExecutionListener listener;

    @Mock
    OkHttpClient client;

    @Mock
    Call call;

    @Mock
    Response response;

    @Mock
    ResponseBody responseBody;

    @Mock
    Dispatcher dispatcher;

    @Mock
    ExecutorService executorService;

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

    public void testRunJob() throws InterruptedException, IOException {
        String url = "https://rancher.example.com/v2-beta/";
        String accessKey = "access";
        String secretKey = "secret";
        String[] command = {"ls"};
        String temp = "";
        int timeout = 1;

        doNothing().when(client.newWebSocket(any(), any()));
        when(client.newCall(Matchers.anyObject())).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.body()).thenReturn(responseBody);
        when(responseBody.string()).thenReturn("{}");
        when(client.dispatcher()).thenReturn(dispatcher);
        when(dispatcher.executorService()).thenReturn(executorService);
        doNothing().when(executorService).shutdown();
        doNothing().when(executorService).awaitTermination(any(), any());
        RancherWebSocketListener.runJob(client, url, accessKey, secretKey, command, listener, temp, timeout);
        verify(client, times(1)).newWebSocket(any(), any());
        verify(client, times(2)).dispatcher();
    }
}

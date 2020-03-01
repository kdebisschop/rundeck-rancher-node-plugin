package com.bioraft.rundeck.rancher;

import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;
import static com.bioraft.rundeck.rancher.TestHelper.*;

@RunWith(MockitoJUnitRunner.class)
public class HttpClientTest {
    @Mock
    OkHttpClient mockClient;

    @Mock
    Call call;

    HttpClient subject;

    static class HttpClientImplementation extends HttpClient {
        public HttpClientImplementation(OkHttpClient client) {
            super(client);
        }
    }

    @Before
    public void setUp() {
        subject = new HttpClientImplementation(mockClient);
        when(mockClient.newCall(any())).thenReturn(call);
    }

    @Test
    public void testDefaultConstructor() {
        HttpClient client = new HttpClient();
        assertNotNull(client);
    }

    @Test
    public void testGetMethod() throws IOException {
        String url = "https://api.example.com/";
        String text = "{\"key\": \"value\"}";
        subject.setAccessKey("path");
        subject.setSecretKey("path");
        when(call.execute()).thenReturn(response(text));
        JsonNode json = subject.get(url);
        assertEquals(1, json.size());
        assertEquals("value", json.get("key").asText());
    }

    @Test
    public void testGetWithQuery() throws IOException {
        String url = "https://api.example.com/";
        String text = "{\"key\": \"value\"}";
        Map<String, String> query = new HashMap<>();
        query.put("key", "value");
        when(call.execute()).thenReturn(response(text));
        JsonNode json = subject.get(url, query);
        assertEquals(1, json.size());
        assertEquals("value", json.get("key").asText());
    }

    @Test
    public void testGetWithNullResponse() throws IOException {
        String url = "https://api.example.com/";
        when(call.execute()).thenReturn(response(null));
        JsonNode json = subject.get(url);
        assertEquals(0, json.size());
    }

    @Test(expected = IOException.class)
    public void testGetMethodReturns301() throws IOException {
        String url = "https://api.example.com/";
        String text = "{\"key\": \"value\"}";
        when(call.execute()).thenReturn(response(text, 301));
        JsonNode json = subject.get(url);
        assertEquals(1, json.size());
        assertEquals("value", json.get("key").asText());
    }

    @Test
    public void testPostMethod() throws IOException {
        String url = "https://api.example.com/";
        String text = "{\"key\": \"value\"}";
        when(call.execute()).thenReturn(response(text));
        JsonNode json = subject.post(url, text);
        assertEquals(1, json.size());
        assertEquals("value", json.get("key").asText());
    }

    @Test
    public void testPostMap() throws IOException {
        String url = "https://api.example.com/";
        String text = "{\"key\": \"value\"}";
        Map<String, Object> map = new HashMap<>();
        map.put("key", "value");
        when(call.execute()).thenReturn(response(text));
        JsonNode json = subject.post(url, map);
        assertEquals(1, json.size());
        assertEquals("value", json.get("key").asText());
    }

    @Test
    public void testPostWithNullResponse() throws IOException {
        String url = "https://api.example.com/";
        String text = "{\"key\": \"value\"}";
        when(call.execute()).thenReturn(response(null));
        JsonNode json = subject.post(url, text);
        assertEquals(0, json.size());
    }

    @Test(expected = IOException.class)
    public void testPostMethodReturns301() throws IOException {
        String url = "https://api.example.com/";
        String text = "{\"key\": \"value\"}";
        when(call.execute()).thenReturn(response(text, 301));
        JsonNode json = subject.post(url, text);
        assertEquals(1, json.size());
        assertEquals("value", json.get("key").asText());
    }
}

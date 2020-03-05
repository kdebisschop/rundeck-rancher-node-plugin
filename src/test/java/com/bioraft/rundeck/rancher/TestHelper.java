package com.bioraft.rundeck.rancher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.fail;

public class TestHelper {

    public static JsonNode resourceToJson(String resource) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.readTree(getResourceStream(resource));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            fail();
            return null;
        }
    }
    
    private static String getResourceStream(String resource) {
        String path = "src/test/resources/" + resource;
        try {
            return new String(Files.readAllBytes(Paths.get(path)));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    public static Response response(String json, int code) {
        Request request = new Request.Builder().url("https://example.com").build();
        ResponseBody body;
        if (json != null) {
            body = ResponseBody.create(MediaType.parse("text/json"), json);
        } else {
            body = null;
        }
        Response.Builder builder = new Response.Builder().request(request).protocol(Protocol.HTTP_2);
        builder.body(body).code(code).message("OK");
        return builder.build();
    }

    public static Response response(String json) {
        return response(json, 200);
    }
}

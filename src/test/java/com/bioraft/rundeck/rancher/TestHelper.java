package com.bioraft.rundeck.rancher;

import okhttp3.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class TestHelper {

    private Response response(InputStream stream) throws IOException {
        return response(readFromInputStream(stream));
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

    public static String readFromInputStream(InputStream inputStream) throws IOException {
        StringBuilder resultStringBuilder = new StringBuilder();
        InputStreamReader reader = new InputStreamReader(inputStream);
        try (BufferedReader br = new BufferedReader(reader)) {
            String line;
            while ((line = br.readLine()) != null) {
                resultStringBuilder.append(line).append("\n");
            }
        }
        return resultStringBuilder.toString();
    }
}

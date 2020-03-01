package com.bioraft.rundeck.rancher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

public class HttpClient {

    private String accessKey;
    private String secretKey;
    protected final OkHttpClient client;

    public HttpClient() {
        this.client = new OkHttpClient();
    }

    public HttpClient(OkHttpClient client) {
        this.client = client;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    protected JsonNode get(String url) throws IOException {
        return this.get(url, null);
    }

    protected JsonNode get(String url, Map<String, String> query) throws IOException {
        HttpUrl.Builder urlBuilder = Objects.requireNonNull(HttpUrl.parse(url)).newBuilder();
        if (query != null) {
            query.forEach(urlBuilder::addQueryParameter);
        }
        Request.Builder builder = new Request.Builder().url(urlBuilder.build().toString());
        builder.addHeader("Authorization", Credentials.basic(accessKey, secretKey));
        Response response = client.newCall(builder.build()).execute();
        // Since URL comes from the Rancher server itself, assume there are no redirects.
        if (response.code() >= 300) {
            throw new IOException("API get failed" + response.message());
        }
        ObjectMapper mapper = new ObjectMapper();
        if (response.body() == null) {
            return mapper.readTree("");
        }
        return mapper.readTree(response.body().string());
    }

    protected JsonNode post(String url, Map<String, Object> map) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        String payload = mapper.writeValueAsString(map);
        return this.post(url, payload);
    }

    protected JsonNode post(String url, String data) throws IOException {
        RequestBody postBody = RequestBody.create(MediaType.parse("application/json"), data);
        Request.Builder builder = new Request.Builder().url(url).post(postBody);
        builder.addHeader("Authorization", Credentials.basic(accessKey, secretKey));
        Response response = client.newCall(builder.build()).execute();
        // Since URL comes from the Rancher server itself, assume there are no redirects.
        if (response.code() >= 300) {
            throw new IOException("API post failed" + response.message());
        }
        ObjectMapper mapper = new ObjectMapper();
        if (response.body() == null) {
            return mapper.readTree("");
        }
        return mapper.readTree(response.body().string());
    }
}

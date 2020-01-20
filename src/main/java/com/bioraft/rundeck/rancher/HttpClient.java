package com.bioraft.rundeck.rancher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import okhttp3.*;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.httpclient.DefaultHttpMethodRetryHandler;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.*;
import org.apache.commons.httpclient.params.HttpMethodParams;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

public class HttpClient {

    private String accessKey;
    private String secretKey;
    private final OkHttpClient client;

    public HttpClient() {
        client = new OkHttpClient();
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
        assert response.body() != null;
        return mapper.readTree(response.body().string());
    }

    protected JsonNode delete(String url) throws IOException {
        DeleteMethod method = new DeleteMethod(url);
        return execute(method);
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
        assert response.body() != null;
        return mapper.readTree(response.body().string());
    }

    protected JsonNode post(String url) throws IOException {
        PostMethod method = new PostMethod(url);
        return this.execute(method);
    }

    protected JsonNode put(String url, Object data) throws IOException {
        PutMethod method = new PutMethod(url);
        method.setRequestEntity(getRequestBody(data));
        return this.execute(method);
    }

    private JsonNode execute(HttpMethod method) throws IOException {
        try {
            method.addRequestHeader("Authorization", getAuthorization());
            method.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, new DefaultHttpMethodRetryHandler(3, false));
            int statusCode = new org.apache.commons.httpclient.HttpClient().executeMethod(method);

            InputStream resStream = method.getResponseBodyAsStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(resStream));
            StringBuilder resBuffer = new StringBuilder();
            String resTemp;
            while ((resTemp = br.readLine()) != null) {
                resBuffer.append(resTemp);
            }
            String responseBody = resBuffer.toString();

            if (statusCode != HttpStatus.SC_OK && statusCode != HttpStatus.SC_ACCEPTED && statusCode != HttpStatus.SC_CREATED) {
                throw new RuntimeException(String.format("Some Error Happen statusCode %d response: %s", statusCode, responseBody));
            }
            return getObjectMapper().readTree(responseBody);
        } finally {
            method.releaseConnection();
        }
    }

    private StringRequestEntity getRequestBody(Object stack) throws JsonProcessingException, UnsupportedEncodingException {
        String requestBody = getObjectMapper().writeValueAsString(stack);
        return new StringRequestEntity(requestBody, "application/json", "UTF-8");
    }

    private String getAuthorization() {
        byte[] encodedAuth = Base64.encodeBase64((accessKey + ":" + secretKey).getBytes(StandardCharsets.US_ASCII));
        return "Basic " + new String(encodedAuth);
    }

    private ObjectMapper getObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
        objectMapper.configure(SerializationFeature.FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS, false);
        return objectMapper;
    }


}
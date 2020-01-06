package com.bioraft.rundeck.rancher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.httpclient.DefaultHttpMethodRetryHandler;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.*;
import org.apache.commons.httpclient.params.HttpMethodParams;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public abstract class HttpClient {

    private final String accesskey;
    private final String secretKey;
    private final String endpoint;

    public HttpClient(String endpoint, String accesskey, String secretKey) {
        this.accesskey = accesskey;
        this.secretKey = secretKey;
        this.endpoint = endpoint;
    }

    protected <T> T get(String url, Class<T> responseClass) throws IOException {
        GetMethod deleteMethod = new GetMethod(endpoint + url);
        return execute(deleteMethod, responseClass);
    }

    protected <T> T delete(String url, Class<T> responseClass) throws IOException {
        DeleteMethod deleteMethod = new DeleteMethod(endpoint + url);
        return execute(deleteMethod, responseClass);
    }

    protected <T> T post(String url, Object data, Class<T> responseClass) throws IOException {
        PostMethod method = new PostMethod(endpoint + url);
        method.setRequestEntity(getRequestBody(data));
        return this.execute(method, responseClass);
    }

    protected <T> T post(String url, Class<T> responseClass) throws IOException {
        PostMethod method = new PostMethod(endpoint + url);
        return this.execute(method, responseClass);
    }

    protected <T> T put(String url, Object data, Class<T> responseClass) throws IOException {
        PutMethod method = new PutMethod(endpoint + url);
        method.setRequestEntity(getRequestBody(data));
        return this.execute(method, responseClass);
    }

    private <T> T execute(HttpMethod method, Class<T> responseClass) {
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
            return getObjectMapper().readValue(responseBody, responseClass);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Connection to Rancher Failed Please check deploy configuration");
        } finally {
            method.releaseConnection();
        }
    }

    private StringRequestEntity getRequestBody(Object stack) throws JsonProcessingException, UnsupportedEncodingException {
        String requestBody = getObjectMapper().writeValueAsString(stack);
        return new StringRequestEntity(requestBody, "application/json", "UTF-8");
    }

    private String getAuthorization() {
        byte[] encodedAuth = Base64.encodeBase64((accesskey + ":" + secretKey).getBytes(StandardCharsets.US_ASCII));
        return "Basic " + new String(encodedAuth);
    }

    private ObjectMapper getObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
        objectMapper.configure(SerializationFeature.FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS, false);
        return objectMapper;
    }


}
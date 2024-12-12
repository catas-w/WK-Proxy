package com.catas.wicked.server;

import com.catas.wicked.server.client.MinimalHttpClient;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

public class MinimalHttpClientTest {

    @Test
    public void test() throws Exception {
        String url = "https://github.com/catas-w/WK-Proxy/releases/latest";
        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("Accept-Encoding", "gzip, deflate, br");
        headerMap.put("Accept", "*/*");
        headerMap.put("Host", "GitHub.com");
        headerMap.put("Connection", "keep-alive");
        headerMap.put("User-Agent", "PostmanRuntime/7.43.0");

        MinimalHttpClient client = MinimalHttpClient.builder()
            .uri(url)
            .method(HttpMethod.GET)
            .headers(headerMap)
            .fullResponse(true)
            .build();
        client.execute();
        HttpResponse response = client.response();
        HttpHeaders headers = response.headers();
        headers.forEach(item -> {
            System.out.println(item.getKey() + ": " + item.getValue());
        });
    }
}

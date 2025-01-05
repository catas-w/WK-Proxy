package com.catas.wicked.proxy.provider;

import com.catas.wicked.common.config.ExternalProxyConfig;
import com.catas.wicked.common.constant.ProxyProtocol;
import com.catas.wicked.server.client.MinimalHttpClient;
import com.catas.wicked.common.provider.VersionCheckProvider;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Singleton
public class DefaultVersionCheckProvider implements VersionCheckProvider {

    private static final String RELEASE_URL = "https://github.com/catas-w/WK-Proxy/releases/latest";

    @Override
    public Pair<String, String> fetchLatestVersion() throws InterruptedException {
        log.info("Checking update...");
        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("Accept-Encoding", "gzip, deflate, br");
        headerMap.put("Accept", "*/*");
        headerMap.put("Host", "GitHub.com");
        headerMap.put("Connection", "keep-alive");
        LinkedHashMap<String, String> headersMap = new LinkedHashMap<>();

        ExternalProxyConfig proxyConfig = new ExternalProxyConfig();
        proxyConfig.setProtocol(ProxyProtocol.SYSTEM);

        try (MinimalHttpClient client = MinimalHttpClient.builder()
                .uri(RELEASE_URL)
                .method(HttpMethod.GET)
                .headers(headerMap)
                .fullResponse(true)
                .proxyConfig(proxyConfig)
                .timeout(5000)
                .build()) {

            log.info("Fetching release info from: {}", RELEASE_URL);
            client.execute();
            HttpResponse response = client.response();
            HttpHeaders headers = response.headers();
            headers.forEach(item -> {
                headersMap.put(item.getKey(), item.getValue());
            });

            String location = headersMap.getOrDefault("Location", "");
            Pattern pattern = Pattern.compile("tag/(.+)");
            Matcher matcher = pattern.matcher(location);

            if (!matcher.find()) {
                throw new RuntimeException("Unable to find release info: " + location);
            }

            String version = matcher.group(1);
            log.info("Get latest version: {}", version);
            return Pair.of(version, location);
        } catch (Exception e) {
            log.error("Error in fetching release info.", e);
            throw new RuntimeException("Unable to find release info: " + RELEASE_URL);
        }
    }
}

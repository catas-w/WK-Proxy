package com.catas.wicked.server;

import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.bean.message.ResponseMessage;
import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.util.MockDataUtil;
import com.catas.wicked.common.util.SslUtils;
import com.catas.wicked.server.proxy.ProxyServer;
import io.micronaut.context.BeanContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.ehcache.Cache;

import java.util.Collections;

@Slf4j
@MicronautTest
public class ProxyServerTest {

    protected static CloseableHttpClient httpClient;

    // @Inject
    protected static ProxyServer proxyServer;

    // @Inject
    protected static ApplicationConfig appConfig;
    protected static TestMessageService testMessageService;
    protected static Cache<String, RequestMessage> cache;
    protected static PrevIdGenerator prevIdGenerator;
    protected static BeanContext context;
    protected final MockDataUtil mockDataUtil = new MockDataUtil();


    protected static void initHttpClient() throws Exception {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(10 * 1000)
                .setConnectionRequestTimeout(10 * 1000)
                .setProxy(new HttpHost("127.0.0.1", 9966))
                .build();

        httpClient = HttpClients.custom()
                .setDefaultHeaders(Collections.emptyList())
                .setDefaultRequestConfig(requestConfig)
                .setSSLSocketFactory(SslUtils.getSocketFactory(false, null, null))
                .build();
    }

    protected static void initBeanContext() {
        ProxyServer.standalone = true;
        context = BeanContext.build();
        context.start();

        appConfig = context.getBean(ApplicationConfig.class);
        proxyServer = context.getBean(ProxyServer.class);
        testMessageService = context.getBean(TestMessageService.class);
        prevIdGenerator = context.getBean(PrevIdGenerator.class);
        cache = context.getBean(Cache.class);
    }


    protected RequestMessage getRequestMessageFromCache(String requestId) {
        return getRequestMessageFromCache(requestId, 0);
    }

    protected RequestMessage getRequestMessageFromCache(String requestId, int waitTime) {
        if (waitTime >= 1000) {
            return null;
        }
        RequestMessage requestMessage = cache.get(requestId);
        if (requestMessage != null) {
            return requestMessage;
        }

        waitTime += 250;
        log.info("Waiting for requestMessage, waitTime={}", waitTime);
        try {
            Thread.sleep(250);
        } catch (InterruptedException ignored) {}
        return getRequestMessageFromCache(requestId, waitTime);
    }

    protected ResponseMessage getRespMessageFromCache(String requestId) {
        return getRespMessageFromCache(requestId, 0);
    }

    protected ResponseMessage getRespMessageFromCache(String requestId, int waitTime) {
        if (waitTime >= 2000) {
            return null;
        }
        RequestMessage requestMessage = cache.get(requestId);
        if (requestMessage != null && requestMessage.getResponse() != null) {
            return requestMessage.getResponse();
        }

        waitTime += 250;
        log.info("Waiting for respMessage: {}", waitTime);
        try {
            Thread.sleep(250);
        } catch (InterruptedException ignored) {}
        return getRespMessageFromCache(requestId, waitTime);
    }

}

package com.catas.wicked.server.proxy;

import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.bean.message.ResponseMessage;
import com.catas.wicked.common.bean.mock.ExpectModel;
import com.catas.wicked.common.bean.mock.RequestModel;
import com.catas.wicked.common.config.ExternalProxyConfig;
import com.catas.wicked.common.constant.ClientStatus;
import com.catas.wicked.common.constant.ProxyProtocol;
import com.catas.wicked.server.ProxyServerTest;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;

@Slf4j
public class ExternalProxyTest extends ProxyServerTest {

    @BeforeClass
    public static void init() throws Exception {
        initHttpClient();
        initBeanContext();
    }

    @Before
    public void before() {
        cache.clear();
    }

    @Test
    @Ignore
    public void testDirect() throws Exception {
        List<RequestModel> list = mockDataUtil.loadRequestModel("ex-proxy-data.json");
        Assert.assertNotNull(list);

        for (int i = 0; i < list.size(); i++) {
            RequestModel requestModel = list.get(i);
            String reqId = String.format("test-direct-id-%d", i);
            String assertMsg = "Error: id=" + reqId + ", url=" + requestModel.getUrl();
            log.info("==== RequestId: {}, uri: {} ====", reqId, requestModel.getUrl());

            // execute request
            HttpUriRequest request = mockDataUtil.getUriRequest(requestModel);
            prevIdGenerator.setNextId(reqId);

            CloseableHttpResponse response = httpClient.execute(request);
            log.info("---- Response of {}, code: {}", reqId, response.getStatusLine().getStatusCode());
            Assert.assertEquals(assertMsg, 504, response.getStatusLine().getStatusCode());

            // TODO
            RequestMessage requestMessage = getRequestMessageFromCache(reqId);
            // Assert.assertNull(requestMessage);
            Assert.assertNotNull(assertMsg, requestMessage);
            Assert.assertFalse(assertMsg, requestMessage.getClientStatus().isSuccess());

            response.close();
        }
    }

    @Test
    @Ignore
    public void testHttpProxy() throws Exception {
        List<RequestModel> list = mockDataUtil.loadRequestModel("ex-proxy-data.json");
        Assert.assertNotNull(list);

        // set http proxy
        ExternalProxyConfig proxyConfig = new ExternalProxyConfig();
        proxyConfig.setHost("127.0.0.1");
        proxyConfig.setPort(10809);
        proxyConfig.setProtocol(ProxyProtocol.HTTP);

        // TODO use settings.isEnableExProxy()
        proxyConfig.setUsingExternalProxy(true);
        appConfig.getSettings().setExternalProxy(proxyConfig);

        for (int i = 0; i < list.size(); i++) {
            RequestModel requestModel = list.get(i);
            String reqId = String.format("test-ex-http-id-%d", i);
            String assertMsg = "Error: id=" + reqId + ", url=" + requestModel.getUrl();
            log.info("==== RequestId: {}, uri: {} ====", reqId, requestModel.getUrl());

            // execute request
            HttpUriRequest request = mockDataUtil.getUriRequest(requestModel);
            prevIdGenerator.setNextId(reqId);
            CloseableHttpResponse response = httpClient.execute(request);
            log.info("---- Response of {}, code: {}", reqId, response.getStatusLine().getStatusCode());

            // validate result
            ExpectModel expectModel = requestModel.getExpect();
            if (expectModel.getStatus() == ClientStatus.Status.FINISHED && expectModel.getCode() != 0) {
                Assert.assertEquals(assertMsg, expectModel.getCode(), response.getStatusLine().getStatusCode());

                ResponseMessage responseMessage = getRespMessageFromCache(reqId);
                Assert.assertNotNull(assertMsg, responseMessage);
            }
            response.close();
        }
    }

    @Test
    @Ignore
    public void testSocksProxy() throws Exception {
        List<RequestModel> list = mockDataUtil.loadRequestModel("ex-proxy-data.json");
        Assert.assertNotNull(list);

        // set http proxy
        ExternalProxyConfig proxyConfig = new ExternalProxyConfig();
        proxyConfig.setHost("127.0.0.1");
        proxyConfig.setPort(10808);
        proxyConfig.setProtocol(ProxyProtocol.SOCKS5);
        proxyConfig.setUsingExternalProxy(true);
        appConfig.getSettings().setExternalProxy(proxyConfig);

        for (int i = 0; i < list.size(); i++) {
            RequestModel requestModel = list.get(i);
            String reqId = String.format("test-ex-socks-id-%d", i);
            String assertMsg = "Error: id=" + reqId + ", url=" + requestModel.getUrl();
            log.info("==== RequestId: {}, uri: {} ====", reqId, requestModel.getUrl());

            // execute request
            HttpUriRequest request = mockDataUtil.getUriRequest(requestModel);
            prevIdGenerator.setNextId(reqId);
            CloseableHttpResponse response = httpClient.execute(request);
            log.info("---- Response of {}, code: {}", reqId, response.getStatusLine().getStatusCode());

            // validate result
            ExpectModel expectModel = requestModel.getExpect();
            if (expectModel.getStatus() == ClientStatus.Status.FINISHED && expectModel.getCode() != 0) {
                Assert.assertEquals(assertMsg, expectModel.getCode(), response.getStatusLine().getStatusCode());

                ResponseMessage responseMessage = getRespMessageFromCache(reqId);
                Assert.assertNotNull(assertMsg, responseMessage);
            }
            response.close();
        }
    }

    /**
     * todo: apache httpclient can't connect to proxyServer when set -Djava.net.useSystemProxies=true
     */
    @Test
    @Ignore
    public void testSystemProxy() throws Exception {
        List<RequestModel> list = mockDataUtil.loadRequestModel("ex-proxy-data.json");
        Assert.assertNotNull(list);

        // set http proxy
        ExternalProxyConfig proxyConfig = new ExternalProxyConfig();
        proxyConfig.setProtocol(ProxyProtocol.SYSTEM);
        proxyConfig.setUsingExternalProxy(true);
        appConfig.getSettings().setExternalProxy(proxyConfig);

        for (int i = 0; i < list.size(); i++) {
            RequestModel requestModel = list.get(i);
            String reqId = String.format("test-oversize-id-%d", i);
            String assertMsg = "Error: id=" + reqId + ", url=" + requestModel.getUrl();
            log.info("==== RequestId: {}, uri: {} ====", reqId, requestModel.getUrl());

            // execute request
            HttpUriRequest request = mockDataUtil.getUriRequest(requestModel);
            prevIdGenerator.setNextId(reqId);
            CloseableHttpResponse response = httpClient.execute(request);
            log.info("---- Response of {}, code: {}", reqId, response.getStatusLine().getStatusCode());

            // validate result
            ExpectModel expectModel = requestModel.getExpect();
            if (expectModel.getStatus() == ClientStatus.Status.FINISHED && expectModel.getCode() != 0) {
                Assert.assertEquals(assertMsg, expectModel.getCode(), response.getStatusLine().getStatusCode());

                ResponseMessage responseMessage = getRespMessageFromCache(reqId);
                Assert.assertNotNull(assertMsg, responseMessage);
            }
            response.close();
        }
    }
}

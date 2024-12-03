package com.catas.wicked.server.proxy;

import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.bean.message.ResponseMessage;
import com.catas.wicked.common.bean.mock.ExpectModel;
import com.catas.wicked.common.bean.mock.RequestModel;
import com.catas.wicked.common.config.Settings;
import com.catas.wicked.common.constant.ClientStatus;
import com.catas.wicked.common.constant.ProxyConstant;
import com.catas.wicked.server.ProxyServerTest;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

@Slf4j
public class HttpsProxyTest extends ProxyServerTest {

    @Before
    public void before() {
        // reset all config
        appConfig.setSettings(new Settings());
        cache.clear();
    }

    @BeforeClass
    public static void init() throws Exception {
        initHttpClient();
        initBeanContext();
    }

    @Test
    public void testRecordHttps() throws Exception {
        List<RequestModel> list = mockDataUtil.loadRequestModel("https-data.json");
        Assert.assertNotNull(list);

        for (int i = 0; i < list.size(); i++) {
            String reqId = String.format("testHttps-id-%d", i);
            RequestModel requestModel = list.get(i);
            String assertMsg = "Error: id=" + reqId + ", url=" + requestModel.getUrl();
            log.info("==== RequestId: {}, uri: {} ====", reqId, requestModel.getUrl());

            // execute request
            HttpUriRequest request = mockDataUtil.getUriRequest(requestModel);
            prevIdGenerator.setNextId(reqId);
            CloseableHttpResponse response = httpClient.execute(request);
            log.info("---- Response: {}", response.getStatusLine().getStatusCode());
            response.close();

            // validate result
            ExpectModel expectModel = requestModel.getExpect();
            if (expectModel.getStatus() == ClientStatus.Status.FINISHED && expectModel.getCode() != 0) {
                ResponseMessage responseMessage = getRespMessageFromCache(reqId);
                Assert.assertNotNull(assertMsg, responseMessage);
                Assert.assertEquals(assertMsg, (int) responseMessage.getStatus(), expectModel.getCode());
            }
        }
    }

    @Test
    public void testRecordUnhandledHttps() throws Exception {
        List<RequestModel> list = mockDataUtil.loadRequestModel("https-data.json");
        Assert.assertNotNull(list);
        appConfig.setHandleSSL(false);

        for (int i = 0; i < list.size(); i++) {
            RequestModel requestModel = list.get(i);
            String reqId = String.format("testHttps-unhandled-id-%d", i);
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
                RequestMessage requestMessage = getRequestMessageFromCache(reqId);
                // latter https requests in one tunnel channel cannot be recorded
                if (requestMessage != null) {
                    Assert.assertTrue(assertMsg, requestMessage.getRequestUrl().endsWith(ProxyConstant.UNPARSED_ALIAS));
                    Assert.assertTrue(assertMsg, requestMessage.isEncrypted());
                }
            }
            response.close();
        }
    }

    @Test
    public void testUnRecordHttps() throws Exception {
        List<RequestModel> list = mockDataUtil.loadRequestModel("https-data.json");
        Assert.assertNotNull(list);
        appConfig.getSettings().setRecording(false);

        for (int i = 0; i < list.size(); i++) {
            RequestModel requestModel = list.get(i);
            String reqId = String.format("testHttps-unrecord-id-%d", i);
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
                RequestMessage requestMessage = getRequestMessageFromCache(reqId);
                Assert.assertNull(requestMessage);
            }
            response.close();
        }
    }
}

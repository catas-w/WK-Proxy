package com.catas.wicked.server.proxy;

import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.constant.ProxyConstant;
import com.catas.wicked.common.util.AntMatcherUtils;
import com.catas.wicked.server.ProxyServerTest;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

@Slf4j
public class WhiteListTest extends ProxyServerTest {

    @Before
    public void before() {
        // reset all config
        cache.clear();
    }

    @BeforeClass
    public static void init() throws Exception {
        initHttpClient();
        initBeanContext();
    }

    @Test
    public void testAntMatcher() {
        Assert.assertTrue(AntMatcherUtils.matches("com/**", "com/page/1"));
        Assert.assertTrue(AntMatcherUtils.matches("/com/**", "/com/page/2"));
        Assert.assertTrue(AntMatcherUtils.matches("http://test.com/**", "http://test.com/page/2"));
        Assert.assertTrue(AntMatcherUtils.matches("http://test.com/*/2", "http://test.com/page/2"));
        Assert.assertTrue(AntMatcherUtils.matches("**", "http://test.com/page/2"));
        Assert.assertTrue(AntMatcherUtils.matches("**/**", "http://test.com/page/2"));
        Assert.assertTrue(AntMatcherUtils.matches("test.com/**", "http://test.com/page/2"));
        Assert.assertTrue(AntMatcherUtils.matches("test.com/**", "https://test.com/page/2"));
        Assert.assertTrue(AntMatcherUtils.matches("test.com/**", "http://test.com"));
        Assert.assertTrue(AntMatcherUtils.matches("httpbin.org/**", "https://httpbin.org/"));
        Assert.assertTrue(AntMatcherUtils.matches("httpbin.org/**", "https://httpbin.org/12/23"));

        Assert.assertTrue(AntMatcherUtils.matches("test.com", "https://test.com"));
        Assert.assertTrue(AntMatcherUtils.matches("**.test.com", "https://ab.test.com"));
        Assert.assertTrue(AntMatcherUtils.matches("test.com/**", "https://test.com/12/23"));

        Assert.assertFalse(AntMatcherUtils.matchAny(List.of(), "1234"));
        Assert.assertFalse(AntMatcherUtils.matchAny(List.of("test.com", "test.com/12"), "page"));
        Assert.assertTrue(AntMatcherUtils.matchAny(List.of("test.com", "test.com/12"), "test.com"));
        Assert.assertTrue(AntMatcherUtils.matchAny(List.of("test.com", "test.com/12"), "https://test.com"));
        Assert.assertTrue(AntMatcherUtils.matchAny(List.of("test.com/**", "test"), "https://test.com"));
        Assert.assertTrue(AntMatcherUtils.matchAny(List.of("test.com/**", "test"), "https://test.com/1/2/3/4"));
        Assert.assertTrue(AntMatcherUtils.matchAny(List.of("org", "test.com/**", "test"), "https://test.com/1/4"));
    }

    @Test
    public void testRecordIncludeList() throws Exception {
        int index = 0;
        String prefix = "test-include-";

        appConfig.getSettings().setRecordIncludeList(
                List.of("httpbin.org/anything/include/**", "httpbin.org/anything/unique"));
        Assert.assertNull(sendGet("https://httpbin.org/get", prefix + (index++)));
        Assert.assertNull(sendGet("https://httpbin.org/get?name=jack", prefix + (index++)));
        Assert.assertNull(sendGet("https://httpbin.org/anything", prefix + (index++)));
        Assert.assertNull(sendGet("https://httpbin.org/anything/123", prefix + (index++)));
        Assert.assertNull(sendGet("http://httpbin.org/get", prefix + (index++)));
        Assert.assertNull(sendGet("http://httpbin.org/get?name=jack", prefix + (index++)));
        Assert.assertNull(sendGet("http://httpbin.org/anything", prefix + (index++)));
        Assert.assertNull(sendGet("http://httpbin.org/anything/123", prefix + (index++)));

        Assert.assertNotNull(sendGet("https://httpbin.org/anything/include", prefix + (index++)));
        Assert.assertNotNull(sendGet("https://httpbin.org/anything/include/123", prefix + (index++)));
        Assert.assertNotNull(sendGet("https://httpbin.org/anything/include/1/2/3", prefix + (index++)));
        Assert.assertNotNull(sendGet("https://httpbin.org/anything/unique", prefix + (index++)));
        Assert.assertNotNull(sendGet("http://httpbin.org/anything/include", prefix + (index++)));
        Assert.assertNotNull(sendGet("http://httpbin.org/anything/include/123", prefix + (index++)));
        Assert.assertNotNull(sendGet("http://httpbin.org/anything/include/1/2/3", prefix + (index++)));
        Assert.assertNotNull(sendGet("http://httpbin.org/anything/unique", prefix + (index++)));

        appConfig.getSettings().setRecordIncludeList(List.of());
        Assert.assertNotNull(sendGet("https://httpbin.org/get", prefix + (index++)));
        Assert.assertNotNull(sendGet("https://httpbin.org/get?name=jack", prefix + (index++)));
        Assert.assertNotNull(sendGet("http://httpbin.org/get", prefix + (index++)));
        Assert.assertNotNull(sendGet("http://httpbin.org/get?name=jack", prefix + (index++)));
    }

    @Test
    public void testRecordExcludeList() throws Exception {
        int index = 0;
        String prefix = "test-exclude-";

        appConfig.getSettings().setRecordExcludeList(
                List.of("httpbin.org/anything/exclude/**", "httpbin.org/anything/unique"));
        Assert.assertNotNull(sendGet("https://httpbin.org/get", prefix + (index++)));
        Assert.assertNotNull(sendGet("https://httpbin.org/get?name=jack", prefix + (index++)));
        Assert.assertNotNull(sendGet("https://httpbin.org/anything", prefix + (index++)));
        Assert.assertNotNull(sendGet("https://httpbin.org/anything/123", prefix + (index++)));
        Assert.assertNotNull(sendGet("http://httpbin.org/get", prefix + (index++)));
        Assert.assertNotNull(sendGet("http://httpbin.org/get?name=jack", prefix + (index++)));
        Assert.assertNotNull(sendGet("http://httpbin.org/anything", prefix + (index++)));
        Assert.assertNotNull(sendGet("http://httpbin.org/anything/123", prefix + (index++)));

        Assert.assertNull(sendGet("https://httpbin.org/anything/exclude", prefix + (index++)));
        Assert.assertNull(sendGet("https://httpbin.org/anything/exclude/123", prefix + (index++)));
        Assert.assertNull(sendGet("https://httpbin.org/anything/exclude/1/2/3", prefix + (index++)));
        Assert.assertNull(sendGet("https://httpbin.org/anything/exclude", prefix + (index++)));
        Assert.assertNull(sendGet("http://httpbin.org/anything/unique", prefix + (index++)));
        Assert.assertNull(sendGet("http://httpbin.org/anything/exclude/123", prefix + (index++)));
        Assert.assertNull(sendGet("http://httpbin.org/anything/exclude/1/2/3", prefix + (index++)));
        Assert.assertNull(sendGet("http://httpbin.org/anything/unique", prefix + (index++)));

        appConfig.getSettings().setRecordExcludeList(List.of());
        Assert.assertNotNull(sendGet("https://httpbin.org/anything/include/1/2/3", prefix + (index++)));
        Assert.assertNotNull(sendGet("https://httpbin.org/anything/unique", prefix + (index++)));
        Assert.assertNotNull(sendGet("http://httpbin.org/anything/include", prefix + (index++)));
        Assert.assertNotNull(sendGet("http://httpbin.org/anything/include/123", prefix + (index++)));
    }

    @Test
    public void testHandleSslExcludeList() throws Exception {
        int index = 0;
        String prefix = "test-ssl-exclude-";

        appConfig.getSettings().setSslExcludeList(List.of("httpbin.org", "test.com"));
        verifyEncrypted("https://httpbin.org/anything/1", prefix + (index++), true);
        verifyEncrypted("https://httpbin.org/anything/2", prefix + (index++), true);
        verifyEncrypted("https://httpbin.org/get", prefix + (index++), true);
        verifyEncrypted("https://httpbin.org/anything?name=jack", prefix + (index++), true);
        verifyEncrypted("http://httpbin.org/anything", prefix + (index++), false);
        verifyEncrypted("https://bing.com", prefix + (index++), false);

        appConfig.getSettings().setSslExcludeList(List.of("cn.bing.com"));
        verifyEncrypted("http://httpbin.org/anything/1", prefix + (index++), false);
        verifyEncrypted("https://httpbin.org/anything/1", prefix + (index++), false);
        verifyEncrypted("https://httpbin.org/anything/2", prefix + (index++), false);
        verifyEncrypted("https://httpbin.org/get", prefix + (index++), false);
        verifyEncrypted("https://httpbin.org/anything?name=jack", prefix + (index++), false);
        verifyEncrypted("https://cn.bing.com", prefix + (index++), true);

    }

    private void verifyEncrypted(String uri, String reqId, boolean encrypted) throws Exception {
        RequestMessage requestMessage = sendGet(uri, reqId);
        Assert.assertNotNull(requestMessage);
        Assert.assertEquals(encrypted, requestMessage.getRequestUrl().endsWith(ProxyConstant.UNPARSED_ALIAS));
    }

    private RequestMessage sendGet(String uri, String reqId) throws Exception {
        log.info("==== RequestId: {}, uri: {} ====", reqId, uri);

        // execute request
        prevIdGenerator.setNextId(reqId);
        // HttpUriRequest request = mockDataUtil.getUriRequest(requestModel);
        HttpGet request = new HttpGet(uri);
        CloseableHttpResponse response = httpClient.execute(request);
        log.info("---- Response: {}", response.getStatusLine().getStatusCode());
        response.close();

        return getRequestMessageFromCache(reqId);
    }
}

package com.catas.wicked.server.handler;

import com.catas.wicked.common.bean.ProxyRequestInfo;
import com.catas.wicked.common.bean.ProxyRequestTiming;
import org.junit.Assert;
import org.junit.Test;

public class ProxyRequestTimingUnitTest {

    @Test
    public void lateCompletionDoesNotOverwriteNextKeepAliveRequest() {
        ProxyRequestInfo requestInfo = new ProxyRequestInfo();
        requestInfo.setRequestId("first");
        requestInfo.resetBasicInfo();
        requestInfo.markRequestStart();
        ProxyRequestTiming first = requestInfo.timing();

        requestInfo.setRequestId("second");
        requestInfo.resetBasicInfo();
        requestInfo.markRequestStart();
        ProxyRequestTiming second = requestInfo.timing();

        requestInfo.markRequestEnd(first);

        Assert.assertEquals("first", first.getRequestId());
        Assert.assertTrue(first.getRequestDurationNanos() > 0);
        Assert.assertEquals("second", second.getRequestId());
        Assert.assertEquals(0, requestInfo.getRequestEndTime());
    }
}

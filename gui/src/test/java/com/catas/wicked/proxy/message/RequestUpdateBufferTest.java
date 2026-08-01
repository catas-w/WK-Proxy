package com.catas.wicked.proxy.message;

import com.catas.wicked.common.bean.ProcessInfo;
import com.catas.wicked.common.bean.message.RequestMessage;
import org.junit.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class RequestUpdateBufferTest {

    @Test
    public void processInfoOnlyUpdateDoesNotResetRecordedValues() {
        RequestMessage request = request("request", 1_000, 1_100, 256);
        request.setOversize(true);
        ProcessInfo processInfo = ProcessInfo.builder()
                .ownerPid(42)
                .lookupStatus(ProcessInfo.LookupStatus.FOUND)
                .build();
        RequestMessage update = new RequestMessage();
        update.setRequestId("request");
        update.setProcessInfo(processInfo);

        RequestUpdateBuffer.apply(request, update);

        assertEquals(1_000, request.getStartTime());
        assertEquals(1_100, request.getEndTime());
        assertEquals(256, request.getSize());
        assertTrue(request.isOversize());
        assertSame(processInfo, request.getProcessInfo());
    }

    @Test
    public void mergesMonotonicValuesAndOnlyPresentFields() {
        RequestMessage request = request("request", 1_000, 1_100, 100);
        request.setHeaders(new java.util.HashMap<>(Map.of("existing", "value")));
        RequestMessage update = request("request", 900, 1_300, 250);
        update.setHeaders(Map.of("new", "header"));
        update.setRemoteAddress("127.0.0.1");

        RequestUpdateBuffer.apply(request, update);

        assertEquals(900, request.getStartTime());
        assertEquals(1_300, request.getEndTime());
        assertEquals(250, request.getSize());
        assertEquals("value", request.getHeaders().get("existing"));
        assertEquals("header", request.getHeaders().get("new"));
        assertEquals("127.0.0.1", request.getRemoteAddress());
        assertFalse(request.isOversize());
    }

    @Test
    public void buffersAndMergesUpdatesThatArriveBeforeRequest() {
        RequestUpdateBuffer buffer = new RequestUpdateBuffer();
        buffer.defer(request("request", 1_000, 1_100, 100));
        buffer.defer(request("request", 900, 1_300, 250));

        RequestMessage request = request("request", 950, 1_050, 50);
        RequestUpdateBuffer.apply(request, buffer.drain("request"));

        assertEquals(900, request.getStartTime());
        assertEquals(1_300, request.getEndTime());
        assertEquals(250, request.getSize());
        assertEquals(0, buffer.size());
    }

    @Test
    public void removesDeletedAndClearedRequests() {
        RequestUpdateBuffer buffer = new RequestUpdateBuffer();
        buffer.defer(request("one", 1, 1, 1));
        buffer.defer(request("two", 2, 2, 2));

        buffer.removeAll(Set.of("one"));
        assertNull(buffer.drain("one"));
        assertEquals(1, buffer.size());

        buffer.clear();
        assertEquals(0, buffer.size());
    }

    private static RequestMessage request(String requestId, long start, long end, long size) {
        RequestMessage request = new RequestMessage();
        request.setRequestId(requestId);
        request.setStartTime(start);
        request.setEndTime(end);
        request.setSize(size);
        return request;
    }
}

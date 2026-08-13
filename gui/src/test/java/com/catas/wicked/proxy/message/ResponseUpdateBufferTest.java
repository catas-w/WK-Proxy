package com.catas.wicked.proxy.message;

import com.catas.wicked.common.bean.message.ResponseMessage;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ResponseUpdateBufferTest {

    @Test
    public void mergesUpdatesThatArriveBeforeResponse() {
        ResponseUpdateBuffer buffer = new ResponseUpdateBuffer();
        buffer.defer(timingUpdate("request", 100, 1_100));
        buffer.defer(timingUpdate("request", 250, 1_300));

        ResponseMessage response = response("request", 200, "OK", 50, 1_000);
        ResponseUpdateBuffer.apply(response, buffer.drain("request"));

        assertEquals(250, response.getSize());
        assertEquals(1_300, response.getEndTime());
        assertEquals(300, response.getDurationNanos());
        assertEquals(30, response.getWaitingDurationNanos());
        assertEquals(Integer.valueOf(200), response.getStatus());
        assertEquals("OK", response.getReasonPhrase());
        assertEquals(0, buffer.size());
    }

    @Test
    public void doesNotRegressResponseWithOlderUpdate() {
        ResponseMessage response = response("request", 304, "Not Modified", 250, 1_300);

        ResponseUpdateBuffer.apply(response, timingUpdate("request", 100, 1_100));

        assertEquals(250, response.getSize());
        assertEquals(1_300, response.getEndTime());
        assertEquals(Integer.valueOf(304), response.getStatus());
        assertEquals("Not Modified", response.getReasonPhrase());
    }

    @Test
    public void failureUpdatePromotesExistingResponseToInternalFailure() {
        ResponseMessage response = response("request", 200, "OK", 100, 1_100);
        ResponseMessage failure = timingUpdate("request", 250, 1_300);
        failure.setStatus(-1);
        failure.setReasonPhrase("downstream write failed");

        ResponseUpdateBuffer.apply(response, failure);

        assertEquals(Integer.valueOf(-1), response.getStatus());
        assertEquals("downstream write failed", response.getReasonPhrase());
        assertEquals(250, response.getSize());
        assertEquals(1_300, response.getEndTime());
    }

    @Test
    public void nonFailureUpdateStatusDoesNotReplaceInitialResponseStatus() {
        ResponseMessage response = response("request", 200, "OK", 100, 1_100);
        ResponseMessage update = timingUpdate("request", 250, 1_300);
        update.setStatus(503);
        update.setReasonPhrase("Service Unavailable");

        ResponseUpdateBuffer.apply(response, update);

        assertEquals(Integer.valueOf(200), response.getStatus());
        assertEquals("OK", response.getReasonPhrase());
    }

    @Test
    public void removesDeletedAndClearedRequests() {
        ResponseUpdateBuffer buffer = new ResponseUpdateBuffer();
        buffer.defer(timingUpdate("one", 1, 1));
        buffer.defer(timingUpdate("two", 2, 2));

        buffer.removeAll(Set.of("one"));
        assertNull(buffer.drain("one"));
        assertEquals(1, buffer.size());

        buffer.clear();
        assertEquals(0, buffer.size());
    }

    private static ResponseMessage timingUpdate(String requestId, long size, long endTime) {
        ResponseMessage update = new ResponseMessage();
        update.setRequestId(requestId);
        update.setSize(size);
        update.setEndTime(endTime);
        update.setDurationNanos(Math.max(0, endTime - 1_000));
        update.setWaitingDurationNanos(Math.max(0, endTime - 1_000) / 10);
        return update;
    }

    private static ResponseMessage response(String requestId, int status, String reason,
                                            long size, long endTime) {
        ResponseMessage response = timingUpdate(requestId, size, endTime);
        response.setStatus(status);
        response.setReasonPhrase(reason);
        return response;
    }
}

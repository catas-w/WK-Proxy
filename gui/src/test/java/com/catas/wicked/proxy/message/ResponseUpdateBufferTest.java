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
        buffer.defer(update("request", 100, 1_100));
        buffer.defer(update("request", 250, 1_300));

        ResponseMessage response = update("request", 50, 1_000);
        ResponseUpdateBuffer.apply(response, buffer.drain("request"));

        assertEquals(250, response.getSize());
        assertEquals(1_300, response.getEndTime());
        assertEquals(0, buffer.size());
    }

    @Test
    public void doesNotRegressResponseWithOlderUpdate() {
        ResponseMessage response = update("request", 250, 1_300);

        ResponseUpdateBuffer.apply(response, update("request", 100, 1_100));

        assertEquals(250, response.getSize());
        assertEquals(1_300, response.getEndTime());
    }

    @Test
    public void removesDeletedAndClearedRequests() {
        ResponseUpdateBuffer buffer = new ResponseUpdateBuffer();
        buffer.defer(update("one", 1, 1));
        buffer.defer(update("two", 2, 2));

        buffer.removeAll(Set.of("one"));
        assertNull(buffer.drain("one"));
        assertEquals(1, buffer.size());

        buffer.clear();
        assertEquals(0, buffer.size());
    }

    private static ResponseMessage update(String requestId, long size, long endTime) {
        ResponseMessage update = new ResponseMessage();
        update.setRequestId(requestId);
        update.setSize(size);
        update.setEndTime(endTime);
        return update;
    }
}

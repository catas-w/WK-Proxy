package com.catas.wicked.proxy.service.record;

import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.bean.message.ResponseMessage;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class RequestRecordStoreTest {

    @Test
    public void evictsPayloadButRetainsMetadata() {
        RequestRecordStore store = new RequestRecordStore(5);
        RequestMessage first = request("first", new byte[] {1, 2, 3, 4});
        store.put(first);
        RequestMessage second = request("second", null);
        ResponseMessage response = new ResponseMessage();
        response.setRequestId("second");
        response.setContent(new byte[] {5, 6, 7, 8});
        second.setResponse(response);
        store.put(second);

        RequestRecordSnapshot firstSnapshot = store.snapshot("first");
        RequestRecordSnapshot secondSnapshot = store.snapshot("second");
        assertEquals("https://example.test/first", firstSnapshot.message().getRequestUrl());
        assertEquals(PayloadAvailability.EVICTED, firstSnapshot.requestPayload());
        assertNull(firstSnapshot.message().getBody());
        assertEquals(PayloadAvailability.AVAILABLE, secondSnapshot.responsePayload());
        assertArrayEquals(new byte[] {5, 6, 7, 8}, secondSnapshot.message().getResponse().getContent());
        assertEquals(2, store.stats().requestCount());
        assertEquals(1, store.stats().evictedPayloadCount());
    }

    @Test
    public void payloadReadsUpdateLruOrder() {
        RequestRecordStore store = new RequestRecordStore(6);
        store.put(request("first", new byte[] {1, 1, 1}));
        store.put(request("second", new byte[] {2, 2, 2}));
        store.snapshot("first");
        store.put(request("third", new byte[] {3, 3, 3}));

        assertEquals(PayloadAvailability.AVAILABLE, store.snapshot("first").requestPayload());
        assertEquals(PayloadAvailability.EVICTED, store.snapshot("second").requestPayload());
        assertEquals(PayloadAvailability.AVAILABLE, store.snapshot("third").requestPayload());
    }

    @Test
    public void storeOwnsMetadataWithoutDuplicatingPayload() {
        RequestRecordStore store = new RequestRecordStore(16);
        RequestMessage request = request("one", new byte[] {1, 2, 3});
        store.put(request);

        assertSame(request, store.getMetadata("one"));
        assertNull(store.getMetadata("one").getBody());
        assertArrayEquals(new byte[] {1, 2, 3}, store.snapshot("one").message().getBody());
    }

    @Test
    public void retainsFiftyThousandMetadataRecordsWithoutPayloadStorage() {
        RequestRecordStore store = new RequestRecordStore(1024);
        for (int index = 0; index < 50_000; index++) {
            store.put(request("request-" + index, null));
        }

        assertEquals(50_000, store.stats().requestCount());
        assertEquals(0, store.stats().retainedPayloadBytes());
        assertEquals("request-49999", store.getMetadata("request-49999").getRequestId());
    }

    private static RequestMessage request(String id, byte[] body) {
        RequestMessage request = new RequestMessage("https://example.test/" + id);
        request.setRequestId(id);
        request.setMethod("POST");
        request.setBody(body);
        return request;
    }
}

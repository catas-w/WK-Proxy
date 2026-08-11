package com.catas.wicked.proxy.service.record;

import com.catas.wicked.common.bean.message.BaseMessage;
import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.bean.message.ResponseMessage;
import com.catas.wicked.common.config.ApplicationConfig;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Session request metadata plus a byte-weighted payload cache. Metadata remains
 * available for tree and statistics views after an old payload is evicted.
 */
@Slf4j
@Singleton
public class RequestRecordStore {

    static final long BYTES_PER_MB = 1024L * 1024L;
    private static final int DEFAULT_PAYLOAD_BUDGET_MB = 512;

    private final Map<String, StoredRecord> records = new LinkedHashMap<>();
    private final LinkedHashMap<PayloadKey, byte[]> payloads = new LinkedHashMap<>(64, 0.75f, true);
    private final ApplicationConfig applicationConfig;

    private long retainedPayloadBytes;
    private long evictedPayloadCount;
    private long maxPayloadBytes;
    private long lastHighWaterWarningNanos;

    @Inject
    public RequestRecordStore(ApplicationConfig applicationConfig) {
        this.applicationConfig = applicationConfig;
        this.maxPayloadBytes = budgetBytes();
    }

    RequestRecordStore(long maxPayloadBytes) {
        this.applicationConfig = null;
        this.maxPayloadBytes = Math.max(0, maxPayloadBytes);
    }

    /** Returns the owned mutable metadata object for message merging. */
    public synchronized RequestMessage getMetadata(String requestId) {
        StoredRecord record = records.get(requestId);
        return record == null ? null : record.metadata;
    }

    /** Returns a detached request view with currently retained payload references. */
    public synchronized RequestRecordSnapshot snapshot(String requestId) {
        StoredRecord record = records.get(requestId);
        if (record == null) {
            return null;
        }
        RequestMessage copy = copyRequest(record.metadata);
        byte[] requestBody = payloads.get(new PayloadKey(requestId, PayloadType.REQUEST));
        if (requestBody != null) {
            copy.setBody(requestBody);
        }
        ResponseMessage response = copy.getResponse();
        byte[] responseBody = payloads.get(new PayloadKey(requestId, PayloadType.RESPONSE));
        if (response != null && responseBody != null) {
            response.setContent(responseBody);
        }
        return new RequestRecordSnapshot(copy,
                availability(record.requestPayloadSeen, record.requestPayloadEvicted, requestBody),
                availability(record.responsePayloadSeen, record.responsePayloadEvicted, responseBody));
    }

    public synchronized void put(RequestMessage message) {
        if (message == null || message.getRequestId() == null) {
            return;
        }
        updateBudget();
        StoredRecord record = records.computeIfAbsent(message.getRequestId(), id -> new StoredRecord(message));
        record.metadata = message;
        byte[] requestBody = message.getBody();
        if (requestBody != null && requestBody.length > 0) {
            record.requestPayloadSeen = true;
            record.requestPayloadEvicted = false;
            putPayload(new PayloadKey(message.getRequestId(), PayloadType.REQUEST), requestBody);
        }
        message.setBody(null);

        ResponseMessage response = message.getResponse();
        if (response != null) {
            byte[] responseBody = response.getContent();
            if (responseBody != null && responseBody.length > 0) {
                record.responsePayloadSeen = true;
                record.responsePayloadEvicted = false;
                putPayload(new PayloadKey(message.getRequestId(), PayloadType.RESPONSE), responseBody);
            }
            response.setContent(null);
        }
        evictToBudget();
        warnAtHighWater();
    }

    public synchronized void remove(String requestId) {
        if (requestId == null) {
            return;
        }
        records.remove(requestId);
        removePayload(new PayloadKey(requestId, PayloadType.REQUEST));
        removePayload(new PayloadKey(requestId, PayloadType.RESPONSE));
    }

    public synchronized void removeAll(Collection<String> requestIds) {
        if (requestIds != null) {
            requestIds.forEach(this::remove);
        }
    }

    public synchronized void clear() {
        records.clear();
        payloads.clear();
        retainedPayloadBytes = 0;
    }

    public synchronized StoreStats stats() {
        return new StoreStats(records.size(), retainedPayloadBytes, maxPayloadBytes, evictedPayloadCount);
    }

    public synchronized void refreshBudget() {
        updateBudget();
        evictToBudget();
    }

    private void putPayload(PayloadKey key, byte[] payload) {
        byte[] previous = payloads.put(key, payload);
        if (previous != null) {
            retainedPayloadBytes -= previous.length;
        }
        retainedPayloadBytes += payload.length;
    }

    private void removePayload(PayloadKey key) {
        byte[] removed = payloads.remove(key);
        if (removed != null) {
            retainedPayloadBytes -= removed.length;
        }
    }

    private void evictToBudget() {
        var iterator = payloads.entrySet().iterator();
        while (retainedPayloadBytes > maxPayloadBytes && iterator.hasNext()) {
            Map.Entry<PayloadKey, byte[]> entry = iterator.next();
            iterator.remove();
            retainedPayloadBytes -= entry.getValue().length;
            evictedPayloadCount++;
            StoredRecord record = records.get(entry.getKey().requestId);
            if (record != null) {
                if (entry.getKey().type == PayloadType.REQUEST) {
                    record.requestPayloadEvicted = true;
                } else {
                    record.responsePayloadEvicted = true;
                }
            }
        }
        if (retainedPayloadBytes > maxPayloadBytes) {
            log.warn("Request payload budget exceeded: retained={} bytes, budget={} bytes",
                    retainedPayloadBytes, maxPayloadBytes);
        }
    }

    private void warnAtHighWater() {
        if (maxPayloadBytes < BYTES_PER_MB || retainedPayloadBytes * 10 < maxPayloadBytes * 9) {
            return;
        }
        long now = System.nanoTime();
        if (now - lastHighWaterWarningNanos < 30_000_000_000L) {
            return;
        }
        lastHighWaterWarningNanos = now;
        log.warn("Request payload store is above 90% of budget: retained={} MB, budget={} MB",
                retainedPayloadBytes / BYTES_PER_MB, maxPayloadBytes / BYTES_PER_MB);
    }

    private void updateBudget() {
        if (applicationConfig != null) {
            maxPayloadBytes = budgetBytes();
        }
    }

    private long budgetBytes() {
        int budgetMb = DEFAULT_PAYLOAD_BUDGET_MB;
        if (applicationConfig != null && applicationConfig.getSettings() != null) {
            budgetMb = applicationConfig.getSettings().getRetainedPayloadSizeMb();
        }
        return Math.max(1L, budgetMb) * BYTES_PER_MB;
    }

    private static PayloadAvailability availability(boolean seen, boolean evicted, byte[] payload) {
        if (payload != null) {
            return PayloadAvailability.AVAILABLE;
        }
        return seen && evicted ? PayloadAvailability.EVICTED : PayloadAvailability.EMPTY;
    }

    private static RequestMessage copyRequest(RequestMessage source) {
        RequestMessage copy = new RequestMessage();
        copyBase(source, copy);
        copy.setMethod(source.getMethod());
        copy.setContentType(source.getContentType());
        copy.setRequestId(source.getRequestId());
        copy.setRequestUrl(source.getRequestUrl());
        copy.setUrl(source.getUrl());
        copy.setRemoteHost(source.getRemoteHost());
        copy.setRemotePort(source.getRemotePort());
        copy.setRemoteAddress(source.getRemoteAddress());
        copy.setLocalAddress(source.getLocalAddress());
        copy.setLocalPort(source.getLocalPort());
        copy.setProtocol(source.getProtocol());
        copy.setEnd(source.isEnd());
        copy.setHeaders(source.getHeaders() == null ? null : new LinkedHashMap<>(source.getHeaders()));
        copy.setEncrypted(source.isEncrypted());
        copy.setOversize(source.isOversize());
        copy.setClientStatus(source.getClientStatus());
        copy.setProcessInfo(source.getProcessInfo());
        if (source.getResponse() != null) {
            copy.setResponse(copyResponse(source.getResponse()));
        }
        return copy;
    }

    private static ResponseMessage copyResponse(ResponseMessage source) {
        ResponseMessage copy = new ResponseMessage();
        copyBase(source, copy);
        copy.setRequestId(source.getRequestId());
        copy.setStatus(source.getStatus());
        copy.setReasonPhrase(source.getReasonPhrase());
        copy.setHeaders(source.getHeaders() == null ? null : new LinkedHashMap<>(source.getHeaders()));
        copy.setRetryTimes(source.getRetryTimes());
        copy.setOversize(source.isOversize());
        copy.setWaitingDurationNanos(source.getWaitingDurationNanos());
        return copy;
    }

    private static void copyBase(BaseMessage source, BaseMessage target) {
        target.setType(source.getType());
        target.setStartTime(source.getStartTime());
        target.setEndTime(source.getEndTime());
        target.setDurationNanos(source.getDurationNanos());
        target.setSize(source.getSize());
    }

    public record StoreStats(long requestCount, long retainedPayloadBytes,
                             long payloadBudgetBytes, long evictedPayloadCount) {
    }

    private static final class StoredRecord {
        private RequestMessage metadata;
        private boolean requestPayloadSeen;
        private boolean responsePayloadSeen;
        private boolean requestPayloadEvicted;
        private boolean responsePayloadEvicted;

        private StoredRecord(RequestMessage metadata) {
            this.metadata = metadata;
        }
    }

    private record PayloadKey(String requestId, PayloadType type) {
    }

    private enum PayloadType {
        REQUEST,
        RESPONSE
    }
}

package com.catas.wicked.proxy.message;

import com.catas.wicked.common.bean.message.RequestMessage;

import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class RequestUpdateBuffer {

    private final ConcurrentMap<String, RequestMessage> updates = new ConcurrentHashMap<>();

    void defer(RequestMessage update) {
        if (update == null || update.getRequestId() == null) {
            return;
        }
        updates.merge(update.getRequestId(), copy(update), RequestUpdateBuffer::merge);
    }

    RequestMessage drain(String requestId) {
        return requestId == null ? null : updates.remove(requestId);
    }

    void removeAll(Set<String> requestIds) {
        if (requestIds != null) {
            requestIds.forEach(updates::remove);
        }
    }

    void clear() {
        updates.clear();
    }

    int size() {
        return updates.size();
    }

    static void apply(RequestMessage target, RequestMessage update) {
        if (target == null || update == null) {
            return;
        }
        if (update.getStartTime() > 0
                && (target.getStartTime() <= 0 || update.getStartTime() < target.getStartTime())) {
            target.setStartTime(update.getStartTime());
        }
        target.setEndTime(Math.max(target.getEndTime(), update.getEndTime()));
        target.setSize(Math.max(target.getSize(), update.getSize()));
        target.setOversize(target.isOversize() || update.isOversize());
        if (update.getClientStatus() != null) {
            target.setClientStatus(update.getClientStatus());
        }
        if (update.getBody() != null) {
            target.setBody(update.getBody());
        }
        if (update.getHeaders() != null) {
            if (target.getHeaders() == null) {
                target.setHeaders(new HashMap<>());
            }
            target.getHeaders().putAll(update.getHeaders());
        }
        if (update.getRemoteAddress() != null && !update.getRemoteAddress().isBlank()) {
            target.setRemoteAddress(update.getRemoteAddress());
        }
        if (update.getProcessInfo() != null) {
            target.setProcessInfo(update.getProcessInfo());
        }
    }

    private static RequestMessage merge(RequestMessage current, RequestMessage incoming) {
        apply(current, incoming);
        return current;
    }

    private static RequestMessage copy(RequestMessage source) {
        RequestMessage copy = new RequestMessage();
        copy.setRequestId(source.getRequestId());
        apply(copy, source);
        return copy;
    }
}

package com.catas.wicked.proxy.message;

import com.catas.wicked.common.bean.message.ResponseMessage;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class ResponseUpdateBuffer {

    private final ConcurrentMap<String, ResponseMessage> updates = new ConcurrentHashMap<>();

    void defer(ResponseMessage update) {
        if (update == null || update.getRequestId() == null) {
            return;
        }
        updates.merge(update.getRequestId(), copy(update), ResponseUpdateBuffer::merge);
    }

    ResponseMessage drain(String requestId) {
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

    static void apply(ResponseMessage target, ResponseMessage update) {
        if (target == null || update == null) {
            return;
        }
        if (update.getStartTime() > 0
                && (target.getStartTime() <= 0 || update.getStartTime() < target.getStartTime())) {
            target.setStartTime(update.getStartTime());
        }
        target.setSize(Math.max(target.getSize(), update.getSize()));
        target.setEndTime(Math.max(target.getEndTime(), update.getEndTime()));
        target.setDurationNanos(Math.max(target.getDurationNanos(), update.getDurationNanos()));
        target.setWaitingDurationNanos(Math.max(
                target.getWaitingDurationNanos(), update.getWaitingDurationNanos()));
        if (update.getStatus() == -1) {
            target.setStatus(-1);
            target.setReasonPhrase(update.getReasonPhrase());
        }
    }

    private static ResponseMessage merge(ResponseMessage current, ResponseMessage incoming) {
        apply(current, incoming);
        return current;
    }

    private static ResponseMessage copy(ResponseMessage source) {
        ResponseMessage copy = new ResponseMessage();
        copy.setRequestId(source.getRequestId());
        copy.setSize(source.getSize());
        copy.setStartTime(source.getStartTime());
        copy.setEndTime(source.getEndTime());
        copy.setDurationNanos(source.getDurationNanos());
        copy.setWaitingDurationNanos(source.getWaitingDurationNanos());
        copy.setStatus(source.getStatus());
        copy.setReasonPhrase(source.getReasonPhrase());
        return copy;
    }
}

package com.catas.wicked.proxy.service.record;

import com.catas.wicked.common.bean.message.RequestMessage;

public record RequestRecordSnapshot(
        RequestMessage message,
        PayloadAvailability requestPayload,
        PayloadAvailability responsePayload
) {
    public boolean requestPayloadEvicted() {
        return requestPayload == PayloadAvailability.EVICTED;
    }

    public boolean responsePayloadEvicted() {
        return responsePayload == PayloadAvailability.EVICTED;
    }
}

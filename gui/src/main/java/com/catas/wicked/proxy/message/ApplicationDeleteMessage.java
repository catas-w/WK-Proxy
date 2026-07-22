package com.catas.wicked.proxy.message;

import com.catas.wicked.common.bean.message.BaseMessage;
import lombok.Getter;

import java.util.Set;

/** Internal GUI message for deleting an application or host request group. */
@Getter
final class ApplicationDeleteMessage extends BaseMessage {

    private final Set<String> requestIds;

    ApplicationDeleteMessage(Set<String> requestIds) {
        this.requestIds = requestIds == null ? Set.of() : Set.copyOf(requestIds);
    }
}

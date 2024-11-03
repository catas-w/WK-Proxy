package com.catas.wicked.common.bean.message;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class BaseMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private MessageType type;

    private long startTime;

    private long endTime;

    private long size;

    private int retryTimes = 3;

    public enum MessageType {
        REQUEST,

        @Deprecated
        REQUEST_CONTENT,

        RESPONSE,

        @Deprecated
        RESPONSE_CONTENT,

        POISON,

        UPDATE,
    }
}

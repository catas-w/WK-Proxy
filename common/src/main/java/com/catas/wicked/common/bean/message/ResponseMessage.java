package com.catas.wicked.common.bean.message;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Data
public class ResponseMessage extends BaseMessage implements Serializable {

    private String requestId;

    private Integer status;

    private String reasonPhrase;

    private Map<String, String> headers;

    private byte[] content;

    private int retryTimes = 3;

    private boolean isOversize;

    public ResponseMessage() {
        this.setType(MessageType.RESPONSE);
    }

    public String getReasonPhrase() {
        if (reasonPhrase == null) {
            return "";
        }
        return reasonPhrase;
    }

    public String getStatusStr() {
        if (status == null) {
            return "-";
        }
        return String.valueOf(status);
    }
}

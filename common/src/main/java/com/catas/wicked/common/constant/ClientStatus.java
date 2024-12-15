package com.catas.wicked.common.constant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

import java.io.Serializable;

@Data
public class ClientStatus implements Serializable {

    @Getter
    @AllArgsConstructor
    public enum Status {
        WAITING("Waiting"),
        FINISHED("Finished"),
        CONNECT_ERR("Connection Failure"),
        REJECTED("Rejected"),
        TIMEOUT("Connection Timeout"),
        ADDR_NOTFOUND("Address Not Found"),
        CLOSED("Connection Closed"),
        SSL_HANDSHAKE_ERR("SSL Handshake Failure"),
        UNKNOWN_ERR("Unknown Connection Error");

        private final String desc;
    }

    private Status status;
    private String msg;


    public ClientStatus() {
        status = Status.WAITING;
    }

    public boolean isFinished() {
        return this.status != Status.WAITING;
    }

    public boolean isSuccess() {
        return this.status == Status.FINISHED;
    }

    public ClientStatus copy() {
        ClientStatus copy = new ClientStatus();
        copy.setStatus(this.getStatus());
        copy.setMsg(this.getMsg());
        return copy;
    }
}

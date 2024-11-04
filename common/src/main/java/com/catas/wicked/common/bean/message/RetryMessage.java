package com.catas.wicked.common.bean.message;

import lombok.Data;

@Data
public class RetryMessage implements Message{

    private int retryTimes;

    public RetryMessage(int retryTimes) {
        this.retryTimes = retryTimes;
    }

    public RetryMessage() {
        this(3);
    }

    public void reduce() {
        if (retryTimes > 0) {
            retryTimes -= 1;
        }
    }
}

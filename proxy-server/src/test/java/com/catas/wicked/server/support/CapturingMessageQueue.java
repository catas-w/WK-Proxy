package com.catas.wicked.server.support;

import com.catas.wicked.common.bean.message.Message;
import com.catas.wicked.common.pipeline.MessageQueue;
import com.catas.wicked.common.pipeline.Topic;

import java.util.ArrayList;
import java.util.List;

public class CapturingMessageQueue extends MessageQueue {

    private final List<CapturedMessage> messages = new ArrayList<>();

    @Override
    public void pushMsg(Topic topic, Message message) {
        messages.add(new CapturedMessage(topic, message));
    }

    public List<CapturedMessage> getMessages() {
        return List.copyOf(messages);
    }

    public record CapturedMessage(Topic topic, Message message) {
    }
}

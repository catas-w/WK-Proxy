package com.catas.wicked.common.pipeline;

import com.catas.wicked.common.bean.message.BaseMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

/**
 * MessageChannel: topic -> blockingqueue
 */
@Slf4j
public class MessageChannel {

    private final Topic topic;
    private final BlockingQueue<BaseMessage> queue;

    private final List<Consumer<BaseMessage>> consumers;

    public MessageChannel(Topic topic) {
        this.topic = topic;
        this.queue = new LinkedBlockingQueue<>();
        this.consumers = new ArrayList<>();
    }

    public Topic getTopic() {
        return topic;
    }

    public void pushMsg(BaseMessage message) {
        queue.add(message);
    }

    public BaseMessage getMsg() throws InterruptedException {
        return queue.take();
    }

    public void clear() {
        queue.clear();
    }

    /**
     * subscribe consumer to current channel
     * @param consumer function, not null
     */
    public void addConsumer(Consumer<BaseMessage> consumer) {
        consumers.add(consumer);
    }

    /**
     * execute current message by every subscribed consumer
     * @param baseMessage currentMessage
     */
    public boolean consume(BaseMessage baseMessage) {
        if (consumers.isEmpty()) {
            log.warn("empty consumers");
            return false;
        }
        for (Consumer<BaseMessage> consumer : consumers) {
            try {
                consumer.accept(baseMessage);
                return true;
            } catch (Exception e) {
                log.error("Exception occurred in consumer of: {}", topic, e);
            } catch (Throwable throwable) {
                log.error("Error occurred in consumer of: {}", topic, throwable);
            }
        }
        return false;
    }
}

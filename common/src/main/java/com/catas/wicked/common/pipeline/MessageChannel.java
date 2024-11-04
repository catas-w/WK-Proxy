package com.catas.wicked.common.pipeline;

import com.catas.wicked.common.bean.message.Message;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

/**
 * MessageChannel: topic -> blockingqueue
 */
@Slf4j
public class MessageChannel {

    private final Topic topic;
    private final BlockingQueue<Message> queue;

    private final List<Consumer<Message>> consumers;

    private final Semaphore semaphore = new Semaphore(0);

    public MessageChannel(Topic topic) {
        this.topic = topic;
        this.queue = new LinkedBlockingQueue<>();
        this.consumers = new ArrayList<>();
    }

    public Topic getTopic() {
        return topic;
    }

    public void pushMsg(Message message) {
        queue.add(message);
    }

    public Message getMsg() throws InterruptedException {
        return queue.take();
    }

    public void clear() {
        queue.clear();
    }

    public int getSize() {
        return queue.size();
    }

    /**
     * subscribe consumer to current channel
     * @param consumer function, not null
     */
    public void addConsumer(Consumer<Message> consumer) {
        consumers.add(consumer);
        if (semaphore.availablePermits() == 0) {
            semaphore.release();
        }
    }

    /**
     * execute current message by every subscribed consumer
     */
    public boolean consume(Message msg) {
        if (consumers.isEmpty()) {
            log.warn("empty consumers");
            try {
                // wait until any consumer added
                semaphore.acquire(1);
            } catch (InterruptedException e) {
                log.error("Error in acquire messageChannel semaphore", e);
                return false;
            }
        }

        for (Consumer<Message> consumer : consumers) {
            try {
                consumer.accept(msg);
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

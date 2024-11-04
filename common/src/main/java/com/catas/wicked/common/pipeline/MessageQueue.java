package com.catas.wicked.common.pipeline;

import com.catas.wicked.common.bean.message.Message;
import com.catas.wicked.common.bean.message.PoisonMessage;
import com.catas.wicked.common.bean.message.RetryMessage;
import com.catas.wicked.common.executor.CommonExecutorService;
import com.catas.wicked.common.executor.ThreadPoolService;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

@Slf4j
@Singleton
public class MessageQueue {

    private final Map<Topic, MessageChannel> channelMap;

    private final List<ExecutorService> singleThreadExecutors;

    public MessageQueue() {
        this.channelMap = new HashMap<>();
        this.singleThreadExecutors = new ArrayList<>();
    }

    @PostConstruct
    public void init() {
        for (Topic topic : Topic.values()) {
            MessageChannel messageChannel = new MessageChannel(topic);
            channelMap.put(topic, messageChannel);

            // 设置线程名称
            ExecutorService executor = CommonExecutorService.singleThreadExecutor(topic.name().toLowerCase());
            singleThreadExecutors.add(executor);
            executor.execute(() -> {
                log.info("Start listening to topic: {}", messageChannel.getTopic());
                while (true) {
                    try {
                        Message msg = messageChannel.getMsg();
                        if (msg instanceof PoisonMessage) {
                            throw new InterruptedException("Quit");
                        }

                        // consume
                        boolean success = messageChannel.consume(msg);

                        // retry if failed on retryMsg
                        if (!success && msg instanceof RetryMessage retryMessage) {
                            if (retryMessage.getRetryTimes() > 0) {
                                retryMessage.reduce();
                                ThreadPoolService.getInstance().run(() -> {
                                    try {
                                        Thread.sleep(3000);
                                    } catch (Exception ignored) {}
                                    messageChannel.pushMsg(retryMessage);
                                });
                            } else {
                                log.error("Msg Failed to consume, topic: {}, msg: {}", topic, msg);
                            }
                        }
                    } catch (InterruptedException e) {
                        log.warn("Message listener interrupted: {}", messageChannel.getTopic());
                        break;
                    }
                }
                log.info("End listening to topic: {}", messageChannel.getTopic());
            });
        }
    }

    /**
     * subscribe to a topic
     * @param topic topic
     * @param consumer consumer function
     */
    public void subscribe(Topic topic, Consumer<Message> consumer) {
        if (consumer == null || topic == null) {
            throw new RuntimeException("topic or consumer cannot be null.");
        }
        MessageChannel messageChannel = channelMap.get(topic);
        messageChannel.addConsumer(consumer);
    }

    public void clearMsg(Topic topic) {
        MessageChannel messageChannel = channelMap.get(topic);
        messageChannel.clear();
    }

    /**
     * push message to a specific queue
     * @param topic topic
     * @param message message
     */
    public void pushMsg(Topic topic, Message message) {
        MessageChannel messageChannel = channelMap.get(topic);
        messageChannel.pushMsg(message);
    }

    public void clearAndPushMsg(Topic topic, Message message) {
        MessageChannel messageChannel = channelMap.get(topic);
        messageChannel.clear();
        messageChannel.pushMsg(message);
    }

    public void shutdown() {
        // System.out.println("Quit mq");
        for (MessageChannel channel : channelMap.values()) {
            if (channel != null) {
                channel.pushMsg(new PoisonMessage());
            }
        }
        for (ExecutorService executor : singleThreadExecutors) {
            if (executor != null) {
                executor.shutdownNow();
            }
        }
    }
}

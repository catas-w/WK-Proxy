package com.catas.wicked.proxy.service;

import com.catas.wicked.common.bean.message.OutputMessage;
import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.pipeline.MessageQueue;
import com.catas.wicked.common.pipeline.Topic;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.ehcache.Cache;

import java.io.File;

@Slf4j
@Singleton
public class DataOutputService {

    @Inject
    private MessageQueue messageQueue;

    @Inject
    private Cache<String, RequestMessage> requestCache;

    @PostConstruct
    public void init() {
        messageQueue.subscribe(Topic.DATA_OUTPUT, msg -> {
            log.info("Received data output message: {}", msg);
            if (msg instanceof OutputMessage outputMessage) {
                outputToFile(outputMessage.getRequestId(), outputMessage.getSource(), outputMessage.getTargetFile());
            } else {
                log.warn("Unexpected message type: {}", msg.getClass().getName());
            }
        });
    }

    public void outputToFile(String requestId, OutputMessage.Source source, File targetFile) {
        // TODO
        log.info("Output data for requestId: {}, source: {}, targetFilePath: {}", requestId, source, targetFile.getAbsoluteFile());
    }
}

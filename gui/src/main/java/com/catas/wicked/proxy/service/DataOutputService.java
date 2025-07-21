package com.catas.wicked.proxy.service;

import com.catas.wicked.common.bean.message.OutputMessage;
import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.pipeline.MessageQueue;
import com.catas.wicked.common.pipeline.Topic;
import com.catas.wicked.common.util.WebUtils;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.ehcache.Cache;

import java.io.File;
import java.nio.file.Files;

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
                try {
                    outputToFile(outputMessage.getRequestId(), outputMessage.getSource(), outputMessage.getTargetFile());
                } catch (Exception e) {
                    log.error("Failed to output data for requestId: {}, source: {}, targetFilePath: {}",
                            outputMessage.getRequestId(), outputMessage.getSource(), outputMessage.getTargetFile().getAbsolutePath(), e);
                }
            } else {
                log.warn("Unexpected message type: {}", msg.getClass().getName());
            }
        });
    }

    public void outputToFile(String requestId, OutputMessage.Source source, File targetFile) {
        if (requestId == null || requestId.isEmpty() || targetFile == null) {
            throw new IllegalArgumentException();
        }
        if (source == OutputMessage.Source.IGNORE) {
            log.info("Ignoring output for requestId: {}, source: {}", requestId, source);
            return;
        }

        RequestMessage request = requestCache.get(requestId);
        if (request == null) {
            throw new RuntimeException("Request request not found for requestId: " + requestId);
        }

        // determine the data to output based on the source
        byte[] toOutputData;
        if (source == OutputMessage.Source.REQ_CONTENT) {
            toOutputData = WebUtils.parseContent(request.getHeaders(), request.getBody());
        } else if (source == OutputMessage.Source.RESP_CONTENT) {
            if (request.getResponse() == null) {
                throw new RuntimeException("Response not found for requestId: " + requestId);
            }
            toOutputData = WebUtils.parseContent(request.getResponse().getHeaders(), request.getResponse().getContent());
        } else {
            log.warn("Unknown output source: {}, requestId: {}", source, requestId);
            return;
        }

        if (toOutputData == null || toOutputData.length == 0) {
            log.warn("No request to output for requestId: {}, source: {}", requestId, source);
            return;
        }

        // save request to targetFile
        try {
            Files.write(targetFile.toPath(), toOutputData);
            log.info("Data output successfully to file: {}", targetFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("Failed to write request to file: {}", targetFile.getAbsolutePath(), e);
            throw new RuntimeException("Failed to write request to file: " + targetFile.getAbsolutePath(), e);
        }
    }
}

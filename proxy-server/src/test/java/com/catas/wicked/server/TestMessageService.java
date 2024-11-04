package com.catas.wicked.server;

import com.catas.wicked.common.bean.message.Message;
import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.bean.message.ResponseMessage;
import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.pipeline.MessageQueue;
import com.catas.wicked.common.pipeline.Topic;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.ehcache.Cache;

@Slf4j
@Singleton
public class TestMessageService {

    @Inject
    private ApplicationConfig appConfig;

    @Inject
    private MessageQueue messageQueue;

    @Inject
    private Cache<String, RequestMessage> requestCache;

    @PostConstruct
    public void init() {
        messageQueue.subscribe(Topic.RECORD, this::processMsg);
        messageQueue.subscribe(Topic.UPDATE_MSG, this::processUpdate);
    }

    private void processUpdate(Message msg) {
        if (msg instanceof RequestMessage updateMsg) {
            log.info("Process requestId={}", updateMsg.getRequestId());
            RequestMessage requestMessage = requestCache.get(updateMsg.getRequestId());
            if (requestMessage == null) {
                // TODO: avoid
                System.out.println("Request is null!!!");
                return;
            }
            requestMessage.setSize(updateMsg.getSize());
            requestMessage.setEndTime(updateMsg.getEndTime());
            requestMessage.setClientStatus(updateMsg.getClientStatus());
            if (updateMsg.getBody() != null) {
                requestMessage.setBody(updateMsg.getBody());
            }
            if (updateMsg.getHeaders() != null) {
                requestMessage.getHeaders().putAll(updateMsg.getHeaders());
            }
            requestCache.put(requestMessage.getRequestId(), requestMessage);
        } else if (msg instanceof ResponseMessage updateMsg) {
            RequestMessage requestMessage = requestCache.get(updateMsg.getRequestId());
            if (requestMessage == null) {
                System.out.println("Request is null!!!");
                return;
            }
            if (requestMessage.getResponse() == null ) {
                if (updateMsg.getRetryTimes() > 0) {
                    updateMsg.setRetryTimes(updateMsg.getRetryTimes() - 1);
                    messageQueue.pushMsg(Topic.UPDATE_MSG, updateMsg);
                } else {
                    log.warn("Cannot update responseMsg, requestID = {}", requestMessage.getRequestId());
                }
                return;
            }
            // TODO 分开resp
            requestMessage.getResponse().setSize(updateMsg.getSize());
            requestMessage.getResponse().setEndTime(updateMsg.getEndTime());
            requestCache.put(requestMessage.getRequestId(), requestMessage);
        } else {
            log.warn("Unrecognized requestMsg");
        }
    }

    /**
     * record request and response msg
     * @param msg requestMessage/responseMessage
     */
    private void processMsg(Message msg) {
        if (msg instanceof RequestMessage requestMessage) {
            switch (requestMessage.getType()) {
                case REQUEST -> {
                    // put to cache
                    requestCache.put(requestMessage.getRequestId(), requestMessage);
                }
                case REQUEST_CONTENT -> {
                    // 添加请求体
                    RequestMessage contentMsg = (RequestMessage) msg;
                    RequestMessage data = requestCache.get(contentMsg.getRequestId());
                    if (data != null) {
                        data.setBody(contentMsg.getBody());
                        requestCache.put(data.getRequestId(), data);
                    }
                }
            }
        }

        if (msg instanceof ResponseMessage responseMessage) {
            switch (responseMessage.getType()) {
                case RESPONSE -> {
                    ResponseMessage respMessage = (ResponseMessage) msg;
                    RequestMessage data = requestCache.get(respMessage.getRequestId());
                    if (data != null) {
                        data.setResponse(respMessage);
                        requestCache.put(data.getRequestId(), data);
                    }
                }
                case RESPONSE_CONTENT -> {
                    ResponseMessage respMessage = (ResponseMessage) msg;
                    RequestMessage data = requestCache.get(respMessage.getRequestId());
                    if (data != null && data.getResponse() != null) {
                        data.getResponse().setContent(respMessage.getContent());
                        requestCache.put(data.getRequestId(), data);
                    }
                }
            }
        }
    }
}

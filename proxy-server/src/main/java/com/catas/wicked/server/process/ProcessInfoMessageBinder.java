package com.catas.wicked.server.process;

import com.catas.wicked.common.bean.ProcessInfo;
import com.catas.wicked.common.bean.ProxyRequestInfo;
import com.catas.wicked.common.bean.message.BaseMessage;
import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.pipeline.MessageQueue;
import com.catas.wicked.common.pipeline.Topic;
import com.catas.wicked.server.handler.server.ProcessInfoLookupHandler;
import io.netty.channel.ChannelHandlerContext;

import java.util.concurrent.CompletableFuture;

public class ProcessInfoMessageBinder {

    private final MessageQueue messageQueue;

    public ProcessInfoMessageBinder(MessageQueue messageQueue) {
        this.messageQueue = messageQueue;
    }

    public void bind(ChannelHandlerContext ctx, ProxyRequestInfo requestInfo) {
        CompletableFuture<ProcessInfo> future = ctx.channel()
                .attr(ProcessInfoLookupHandler.PROCESS_INFO_FUTURE_KEY).get();
        ProcessInfo processInfo = ctx.channel().attr(ProcessInfoLookupHandler.PROCESS_INFO_KEY).get();
        if (future != null && future.isDone()) {
            processInfo = future.getNow(ProcessInfo.unknown());
        }
        requestInfo.setProcessInfo(processInfo == null ? ProcessInfo.unknown() : processInfo);

        if (future == null || future.isDone()) {
            return;
        }
        String requestId = requestInfo.getRequestId();
        boolean recording = requestInfo.isRecording();
        future.thenAccept(resolved -> ctx.executor().execute(() -> {
            if (requestId.equals(requestInfo.getRequestId())) {
                requestInfo.setProcessInfo(resolved);
            }
            if (recording) {
                RequestMessage update = new RequestMessage();
                update.setType(BaseMessage.MessageType.UPDATE);
                update.setRequestId(requestId);
                update.setProcessInfo(resolved);
                messageQueue.pushMsg(Topic.UPDATE_MSG, update);
            }
        }));
    }
}

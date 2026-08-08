package com.catas.wicked.server.process;

import com.catas.wicked.common.bean.ProcessInfo;
import com.catas.wicked.common.bean.ProxyRequestInfo;
import com.catas.wicked.common.bean.message.BaseMessage;
import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.pipeline.Topic;
import com.catas.wicked.server.handler.server.ProcessInfoLookupHandler;
import com.catas.wicked.server.support.CapturingMessageQueue;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;

public class ProcessInfoMessageBinderUnitTest {

    @Test
    public void usesAnAlreadyCompletedLookupWithoutPublishingAnUpdate() {
        CapturingMessageQueue queue = new CapturingMessageQueue();
        ProcessInfo resolved = foundProcess(42);
        CompletableFuture<ProcessInfo> future = CompletableFuture.completedFuture(resolved);
        ProxyRequestInfo requestInfo = requestInfo("request-1");
        EmbeddedChannel channel = channel(queue, requestInfo);
        channel.attr(ProcessInfoLookupHandler.PROCESS_INFO_FUTURE_KEY).set(future);

        channel.writeInbound("bind");

        Assert.assertSame(resolved, requestInfo.getProcessInfo());
        Assert.assertTrue(queue.getMessages().isEmpty());
        channel.finishAndReleaseAll();
    }

    @Test
    public void publishesOneUpdateForALateLookupResult() {
        CapturingMessageQueue queue = new CapturingMessageQueue();
        CompletableFuture<ProcessInfo> future = new CompletableFuture<>();
        ProxyRequestInfo requestInfo = requestInfo("request-2");
        EmbeddedChannel channel = channel(queue, requestInfo);
        channel.attr(ProcessInfoLookupHandler.PROCESS_INFO_KEY).set(ProcessInfo.unknown());
        channel.attr(ProcessInfoLookupHandler.PROCESS_INFO_FUTURE_KEY).set(future);

        channel.writeInbound("bind");
        Assert.assertEquals(ProcessInfo.LookupStatus.UNKNOWN, requestInfo.getProcessInfo().getLookupStatus());

        ProcessInfo resolved = foundProcess(84);
        future.complete(resolved);
        channel.runPendingTasks();

        Assert.assertSame(resolved, requestInfo.getProcessInfo());
        Assert.assertEquals(1, queue.getMessages().size());
        Assert.assertEquals(Topic.UPDATE_MSG, queue.getMessages().get(0).topic());
        RequestMessage update = (RequestMessage) queue.getMessages().get(0).message();
        Assert.assertEquals(BaseMessage.MessageType.UPDATE, update.getType());
        Assert.assertEquals("request-2", update.getRequestId());
        Assert.assertSame(resolved, update.getProcessInfo());
        channel.finishAndReleaseAll();
    }

    @Test
    public void publishesAnErrorUpdateWhenALateLookupFails() {
        CapturingMessageQueue queue = new CapturingMessageQueue();
        CompletableFuture<ProcessInfo> future = new CompletableFuture<>();
        ProxyRequestInfo requestInfo = requestInfo("request-error");
        EmbeddedChannel channel = channel(queue, requestInfo);
        channel.attr(ProcessInfoLookupHandler.PROCESS_INFO_KEY).set(ProcessInfo.unknown());
        channel.attr(ProcessInfoLookupHandler.PROCESS_INFO_FUTURE_KEY).set(future);

        channel.writeInbound("bind");
        future.completeExceptionally(new IllegalStateException("lookup failed"));
        channel.runPendingTasks();

        Assert.assertEquals(ProcessInfo.LookupStatus.ERROR,
                requestInfo.getProcessInfo().getLookupStatus());
        Assert.assertEquals(1, queue.getMessages().size());
        RequestMessage update = (RequestMessage) queue.getMessages().get(0).message();
        Assert.assertEquals(ProcessInfo.LookupStatus.ERROR,
                update.getProcessInfo().getLookupStatus());
        channel.finishAndReleaseAll();
    }

    @Test
    public void reusesTheCompletedChannelLookupForKeepAliveRequests() {
        CapturingMessageQueue queue = new CapturingMessageQueue();
        ProcessInfo resolved = foundProcess(126);
        EmbeddedChannel channel = channel(queue);
        channel.attr(ProcessInfoLookupHandler.PROCESS_INFO_FUTURE_KEY)
                .set(CompletableFuture.completedFuture(resolved));
        ProxyRequestInfo first = requestInfo("request-3");
        ProxyRequestInfo second = requestInfo("request-4");

        channel.writeInbound(first);
        channel.writeInbound(second);

        Assert.assertSame(resolved, first.getProcessInfo());
        Assert.assertSame(resolved, second.getProcessInfo());
        Assert.assertTrue(queue.getMessages().isEmpty());
        channel.finishAndReleaseAll();
    }

    private EmbeddedChannel channel(CapturingMessageQueue queue, ProxyRequestInfo requestInfo) {
        ProcessInfoMessageBinder binder = new ProcessInfoMessageBinder(queue);
        return new EmbeddedChannel(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                binder.bind(ctx, requestInfo);
                ctx.fireChannelRead(msg);
            }
        });
    }

    private EmbeddedChannel channel(CapturingMessageQueue queue) {
        ProcessInfoMessageBinder binder = new ProcessInfoMessageBinder(queue);
        return new EmbeddedChannel(new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                binder.bind(ctx, (ProxyRequestInfo) msg);
            }
        });
    }

    private ProxyRequestInfo requestInfo(String requestId) {
        ProxyRequestInfo requestInfo = new ProxyRequestInfo();
        requestInfo.setRequestId(requestId);
        requestInfo.setRecording(true);
        return requestInfo;
    }

    private ProcessInfo foundProcess(long pid) {
        return ProcessInfo.builder()
                .ownerPid(pid)
                .ownerProcessName("test-process")
                .applicationPid(pid)
                .applicationName("Test")
                .lookupStatus(ProcessInfo.LookupStatus.FOUND)
                .build();
    }
}

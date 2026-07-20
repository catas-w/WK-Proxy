package com.catas.wicked.server.client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;


@Slf4j
public class MinimalClientHandler extends ChannelInboundHandlerAdapter {

    private final MinimalHttpClient client;
    private HttpResponse response;

    public MinimalClientHandler(MinimalHttpClient client) {
        this.client = client;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        synchronized (client) {
            client.responsePromise = ctx.executor().newPromise();
            client.msgList.add(client.responsePromise);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        // System.out.println("close client");
        synchronized (client) {
            if (client.responsePromise != null && !client.responsePromise.isDone()) {
                client.responsePromise.setFailure(new IOException("Minimal httpClient error"));
            }
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpResponse fullHttpResponse) {
            response = fullHttpResponse.copy();
            ReferenceCountUtil.release(msg);
            completeResponse();
        } else if (msg instanceof HttpResponse httpResponse){
            DefaultFullHttpResponse copiedResponse = new DefaultFullHttpResponse(
                    httpResponse.protocolVersion(), httpResponse.status());
            copiedResponse.headers().set(httpResponse.headers());
            response = copiedResponse;
        } else if (msg instanceof HttpContent httpContent) {
            if (response instanceof FullHttpResponse fullResponse && httpContent.content().isReadable()) {
                fullResponse.content().writeBytes(httpContent.content(), httpContent.content().readerIndex(),
                        httpContent.content().readableBytes());
            }
            if (response instanceof FullHttpResponse fullResponse && msg instanceof LastHttpContent lastContent) {
                fullResponse.trailingHeaders().set(lastContent.trailingHeaders());
            }
            boolean last = msg instanceof LastHttpContent;
            ReferenceCountUtil.release(msg);
            if (last) {
                completeResponse();
            }
        } else {
            ReferenceCountUtil.release(msg);
        }
    }

    private void completeResponse() {
        synchronized (client) {
            if (client.responsePromise != null && !client.responsePromise.isDone()) {
                client.responsePromise.setSuccess(response);
            } else if (response instanceof FullHttpResponse fullResponse) {
                fullResponse.release();
            }
        }
        client.close();
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        if (response instanceof FullHttpResponse fullResponse
                && (client.responsePromise == null || !client.responsePromise.isSuccess())) {
            if (fullResponse.refCnt() > 0) {
                fullResponse.release();
            }
        }
        super.handlerRemoved(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Error in minimal client handler", cause);
        synchronized (client) {
            if (client.responsePromise != null) {
                client.responsePromise.setFailure(cause);
            }
        }
        throw new RuntimeException(cause);
    }
}

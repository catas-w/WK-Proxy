package com.catas.wicked.server.client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
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
        super.channelRead(ctx, msg);
        if (msg instanceof FullHttpResponse fullHttpResponse) {
            response = fullHttpResponse;
            synchronized (client) {
                if (client.responsePromise != null) {
                    client.responsePromise.setSuccess(response);
                }
            }
            client.close();
        } else if (msg instanceof HttpResponse httpResponse){
            // System.out.println("Receiving: " + httpResponse);
            response = new DefaultFullHttpResponse(httpResponse.protocolVersion(), httpResponse.status());
        } else if (msg instanceof LastHttpContent) {
            // notify to get response
            synchronized (client) {
                if (client.responsePromise != null) {
                    client.responsePromise.setSuccess(response);
                }
            }
            client.close();
        }
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

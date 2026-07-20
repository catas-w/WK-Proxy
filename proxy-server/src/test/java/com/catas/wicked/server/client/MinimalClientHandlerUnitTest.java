package com.catas.wicked.server.client;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class MinimalClientHandlerUnitTest {

    @Test
    public void completesWithAnOwnedCopyOfAFullResponse() throws Exception {
        MinimalHttpClient client = new MinimalHttpClient();
        EmbeddedChannel channel = new EmbeddedChannel(new MinimalClientHandler(client));
        DefaultFullHttpResponse inbound = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                Unpooled.copiedBuffer("payload", CharsetUtil.UTF_8));

        channel.writeInbound(inbound);
        HttpResponse response = client.response();

        Assert.assertTrue(response instanceof FullHttpResponse);
        FullHttpResponse fullResponse = (FullHttpResponse) response;
        Assert.assertEquals(1, fullResponse.refCnt());
        Assert.assertEquals("payload", fullResponse.content().toString(CharsetUtil.UTF_8));

        fullResponse.release();
        channel.finishAndReleaseAll();
    }

    @Test
    public void assemblesAChunkedResponseAndPreservesHeaders() throws Exception {
        MinimalHttpClient client = new MinimalHttpClient();
        EmbeddedChannel channel = new EmbeddedChannel(new MinimalClientHandler(client));
        DefaultHttpResponse headers = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        headers.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
        DefaultLastHttpContent last = new DefaultLastHttpContent(
                Unpooled.copiedBuffer("load", CharsetUtil.UTF_8));
        last.trailingHeaders().set("X-Checksum", "ok");

        channel.writeInbound(headers);
        channel.writeInbound(new DefaultHttpContent(Unpooled.copiedBuffer("pay", CharsetUtil.UTF_8)));
        channel.writeInbound(last);
        FullHttpResponse response = (FullHttpResponse) client.response();

        Assert.assertEquals("text/plain", response.headers().get(HttpHeaderNames.CONTENT_TYPE));
        Assert.assertEquals("payload", response.content().toString(CharsetUtil.UTF_8));
        Assert.assertEquals("ok", response.trailingHeaders().get("X-Checksum"));

        response.release();
        channel.finishAndReleaseAll();
    }

    @Test
    public void failsAnIncompleteResponseWithoutThrowingFromThePipeline() throws Exception {
        MinimalHttpClient client = new MinimalHttpClient();
        EmbeddedChannel channel = new EmbeddedChannel(new MinimalClientHandler(client));

        channel.writeInbound(new DefaultLastHttpContent());

        try {
            client.response();
            Assert.fail("Expected the response promise to fail");
        } catch (ExecutionException exception) {
            Assert.assertTrue(exception.getCause() instanceof IOException);
        }
        channel.finishAndReleaseAll();
    }

    @Test
    public void ignoresALateFailureAfterThePromiseIsComplete() throws Exception {
        MinimalHttpClient client = new MinimalHttpClient();
        EmbeddedChannel channel = new EmbeddedChannel(new MinimalClientHandler(client));
        DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        RuntimeException failure = new RuntimeException("connection failed");
        client.responsePromise.trySuccess(response);

        channel.pipeline().fireExceptionCaught(failure);

        Assert.assertSame(response, client.response());
        channel.finishAndReleaseAll();
    }
}

package com.catas.wicked.server.handler;

import com.catas.wicked.common.bean.ProxyRequestInfo;
import com.catas.wicked.common.constant.ProxyConstant;
import com.catas.wicked.server.component.OversizeHttpMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

import static com.catas.wicked.common.constant.ProxyConstant.OVERSIZE_MSG;
import static io.netty.handler.codec.http.LastHttpContent.EMPTY_LAST_CONTENT;

/**
 * Customized httpObjectAggregator
 * Generate default error message when message oversized
 */
@Slf4j
public class RearHttpAggregator extends HttpObjectAggregator {

    private final AttributeKey<ProxyRequestInfo> requestInfoAttributeKey =
            AttributeKey.valueOf(ProxyConstant.REQUEST_INFO);

    public RearHttpAggregator(int maxContentLength) {
        super(maxContentLength);
    }

    /**
     * 将完整的请求头和默认错误信息的 content 发送给下一个 handler
     * currentMessage 在 MessageAggregator 中被置为 null, 新请求到来时再重新 aggregate, 此处无需处理
     * @param ctx the {@link ChannelHandlerContext}
     * @param oversized the accumulated message up to this point, whose type is {@code S} or {@code O}
     * @throws Exception
     */
    @Override
    protected void handleOversizedMessage(ChannelHandlerContext ctx, HttpMessage oversized) throws Exception {
        if (oversized instanceof HttpRequest httpRequest) {
            log.info("Handling oversized http request.");
            String uri = httpRequest.uri();
            HttpHeaders headers = httpRequest.headers();
            HttpMethod method = httpRequest.method();
            HttpVersion httpVersion = httpRequest.protocolVersion();
            HttpHeaders trailingHeaders = EmptyHttpHeaders.INSTANCE;
            if (httpRequest instanceof FullHttpMessage fullHttpMessage) {
                trailingHeaders = fullHttpMessage.trailingHeaders();
            }
            DefaultFullHttpRequest errRequest = new OversizeHttpRequest(httpVersion, method, uri,
                    Unpooled.wrappedBuffer(OVERSIZE_MSG.getBytes()), headers, trailingHeaders);

            try {
                ctx.fireChannelRead(errRequest);
            } catch (Exception e) {
                log.error("Error in processing oversized http request.", e);
            }
        } else if (oversized instanceof HttpResponse httpResponse) {
            log.info("Handling oversized http response.");
            HttpResponseStatus status = httpResponse.status();
            HttpVersion httpVersion = httpResponse.protocolVersion();
            HttpHeaders headers = httpResponse.headers();
            HttpHeaders trailingHeaders = EmptyHttpHeaders.INSTANCE;
            if (httpResponse instanceof FullHttpResponse fullHttpResponse) {
                trailingHeaders = fullHttpResponse.trailingHeaders();
            }
            DefaultFullHttpResponse errResponse = new OversizeHttpResponse(httpVersion, status,
                    Unpooled.wrappedBuffer(OVERSIZE_MSG.getBytes()), headers, trailingHeaders);

            try {
                ctx.fireChannelRead(errResponse);
            } catch (Exception e) {
                log.error("Error in processing oversized http response");
            }
        } else {
            throw new IllegalStateException();
        }
    }

    @Override
    public boolean acceptInboundMessage(Object msg) throws Exception {
        return super.acceptInboundMessage(msg);
    }

    @Override
    protected FullHttpMessage beginAggregation(HttpMessage start, ByteBuf content) throws Exception {
        // Bug-fix: HttpAggregator removes Transfer-Encoding header
        // boolean isChunked = HttpUtil.isTransferEncodingChunked(start);
        // FullHttpMessage res = super.beginAggregation(start, content);
        // if (isChunked) {
        //     HttpUtil.setTransferEncodingChunked(start, true);
        // }
        // return res;
        return super.beginAggregation(start, content);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        // fix: httpContent unReadable
        if (msg instanceof HttpContent httpContent) {
            // System.out.println("Readable " + httpContent.content().isReadable());
            if (!httpContent.content().isReadable() && httpContent != EMPTY_LAST_CONTENT) {
                httpContent.content().resetReaderIndex();
            }
        }
        super.channelRead(ctx, msg);
    }

    public static class OversizeHttpRequest extends DefaultFullHttpRequest implements OversizeHttpMessage {

        public OversizeHttpRequest(HttpVersion httpVersion, HttpMethod method, String uri,
                                   ByteBuf content, HttpHeaders headers, HttpHeaders trailingHeader) {
            super(httpVersion, method, uri, content, headers, trailingHeader);
        }
    }

    public static class OversizeHttpResponse extends DefaultFullHttpResponse implements OversizeHttpMessage {

        public OversizeHttpResponse(HttpVersion version, HttpResponseStatus status, ByteBuf content) {
            super(version, status, content);
        }

        public OversizeHttpResponse(HttpVersion version, HttpResponseStatus status, ByteBuf content, HttpHeaders headers, HttpHeaders trailingHeaders) {
            super(version, status, content, headers, trailingHeaders);
        }
    }
}

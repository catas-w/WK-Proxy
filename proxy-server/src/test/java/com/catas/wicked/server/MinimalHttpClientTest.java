package com.catas.wicked.server;

import com.catas.wicked.server.client.MinimalHttpClient;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class MinimalHttpClientTest {

    @Test
    public void test() throws Exception {
        String url = "https://github.com/catas-w/WK-Proxy/releases/latest";
        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("Accept-Encoding", "gzip, deflate, br");
        headerMap.put("Accept", "*/*");
        headerMap.put("Host", "GitHub.com");
        headerMap.put("Connection", "keep-alive");
        headerMap.put("User-Agent", "PostmanRuntime/7.43.0");

        MinimalHttpClient client = MinimalHttpClient.builder()
            .uri(url)
            .method(HttpMethod.GET)
            .headers(headerMap)
            .fullResponse(true)
            .build();
        client.execute();
        HttpResponse response = client.response();
        HttpHeaders headers = response.headers();
        headers.forEach(item -> {
            System.out.println(item.getKey() + ": " + item.getValue());
        });
    }

    @Test
    public void testHttps() throws Exception {
        String url = "https://mdl.artvee.com/sdl/204900fgsdl.jpg";
        // String url = "https://mdl.artvee.com";

        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("Accept-Encoding", "gzip, deflate, br");
        headerMap.put("Accept", "*/*");
        headerMap.put("Host", "mdl.artvee.com");

        MinimalHttpClient client = MinimalHttpClient.builder()
                .uri(url)
                .method(HttpMethod.GET)
                .fullResponse(true)
                .headers(headerMap)
                .timeout(5000)
                .build();
        client.execute();
        HttpResponse response = client.response();

        System.out.println("Accept response.");
        HttpHeaders headers = response.headers();
        headers.forEach(item -> {
            System.out.println(item.getKey() + ": " + item.getValue());
        });
    }
}

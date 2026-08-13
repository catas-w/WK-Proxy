package com.catas.wicked.server.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.junit.Assert;
import org.junit.Test;

public class UpstreamSslHandlerFactoryUnitTest {

    @Test
    public void startsDirectTlsWithAClientHello() throws Exception {
        SslContext context = clientContext();
        EmbeddedChannel channel = new EmbeddedChannel();
        SslHandler handler = UpstreamSslHandlerFactory.create(
                context, channel, "example.com", 443);
        channel.pipeline().addFirst("ssl", handler);
        channel.runPendingTasks();

        ByteBuf firstWrite = channel.readOutbound();
        Assert.assertNotNull(firstWrite);
        Assert.assertEquals(22, firstWrite.getUnsignedByte(firstWrite.readerIndex()));
        firstWrite.release();
        channel.finishAndReleaseAll();
    }

    @Test
    public void usesOriginDomainAsPeerHostAndSni() throws Exception {
        SslContext context = clientContext();
        EmbeddedChannel channel = new EmbeddedChannel();
        SslHandler handler = UpstreamSslHandlerFactory.create(
                context, channel, "Origin.Example.", 8443);

        Assert.assertEquals("Origin.Example", handler.engine().getPeerHost());
        Assert.assertEquals(8443, handler.engine().getPeerPort());
        Assert.assertEquals("Origin.Example",
                channel.attr(UpstreamSslHandlerFactory.TLS_PEER_HOST).get());
        Assert.assertEquals("Origin.Example",
                channel.attr(UpstreamSslHandlerFactory.TLS_SNI_HOST).get());
        Assert.assertEquals(Integer.valueOf(8443),
                channel.attr(UpstreamSslHandlerFactory.TLS_PEER_PORT).get());

        handler.engine().closeOutbound();
        channel.finishAndReleaseAll();
    }

    @Test
    public void omitsSniForIpLiterals() throws Exception {
        SslContext context = clientContext();
        EmbeddedChannel channel = new EmbeddedChannel();
        SslHandler handler = UpstreamSslHandlerFactory.create(
                context, channel, "[2001:db8::1]", 443);

        Assert.assertNull(handler.engine().getPeerHost());
        Assert.assertEquals("2001:db8::1",
                channel.attr(UpstreamSslHandlerFactory.TLS_PEER_HOST).get());
        Assert.assertNull(channel.attr(UpstreamSslHandlerFactory.TLS_SNI_HOST).get());

        handler.engine().closeOutbound();
        channel.finishAndReleaseAll();
    }

    @Test
    public void rejectsMissingOrInvalidEndpoints() throws Exception {
        SslContext context = clientContext();
        EmbeddedChannel channel = new EmbeddedChannel();

        assertInvalid(() -> UpstreamSslHandlerFactory.create(context, channel, " ", 443));
        assertInvalid(() -> UpstreamSslHandlerFactory.create(context, channel, "example.com", 0));
        channel.finishAndReleaseAll();
    }

    private static SslContext clientContext() throws Exception {
        return SslContextBuilder.forClient()
                .sslProvider(SslProvider.JDK)
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();
    }

    private static void assertInvalid(Runnable operation) {
        try {
            operation.run();
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}

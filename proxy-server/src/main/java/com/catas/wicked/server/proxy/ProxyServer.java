package com.catas.wicked.server.proxy;

import com.catas.wicked.common.constant.ServerStatus;
import com.catas.wicked.common.executor.ThreadPoolService;
import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.util.WebUtils;
import com.catas.wicked.server.handler.server.ServerChannelInitializer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Singleton
public class ProxyServer {

    public static boolean standalone;

    @Inject
    private ApplicationConfig applicationConfig;

    @Inject
    private ServerChannelInitializer proxyServerInitializer;

    private ChannelFuture channelFuture;

    private final AtomicBoolean initialStartScheduled = new AtomicBoolean(false);

    public void setStatus(ServerStatus status) {
        applicationConfig.getObservableConfig().setServerStatus(status);
    }

    public void start() {
        if (!applicationConfig.clientSslContextReady().toCompletableFuture().isDone()) {
            throw new IllegalStateException("Proxy server cannot start before upstream TLS initialization completes");
        }
        if (applicationConfig.getSettings().isHandleSsl()
                && applicationConfig.getClientSslCtx() == null) {
            throw new IllegalStateException("HTTPS interception is enabled without an upstream TLS context");
        }
        log.info("--- Proxy server Starting on: {} ---", applicationConfig.getSettings().getPort());
        setStatus(ServerStatus.INIT);
        NioEventLoopGroup workGroup = new NioEventLoopGroup(2);
        NioEventLoopGroup bossGroup = new NioEventLoopGroup();

        try {
            // InternalLoggerFactory.setDefaultFactory(JdkLoggerFactory.INSTANCE);
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(proxyServerInitializer);
            channelFuture = bootstrap.bind(applicationConfig.getSettings().getPort()).sync();
            channelFuture.channel().closeFuture().addListener(future -> {
                log.info("--- Proxy server stopping ---");
                setStatus(ServerStatus.HALTED);
                if (!(bossGroup.isShutdown() || bossGroup.isShuttingDown())) {
                    bossGroup.shutdownGracefully();
                }
                if (!(workGroup.isShutdown() || workGroup.isShuttingDown())) {
                    workGroup.shutdownGracefully();
                }
            });
            channelFuture.addListener(future -> {
                if (future.isSuccess()) {
                    log.info("--- Proxy server running on: {} ---", applicationConfig.getSettings().getPort());
                    setStatus(ServerStatus.RUNNING);
                } else {
                    log.info("--- Proxy server failed on starting: {} ---", future.cause().getMessage());
                    setStatus(ServerStatus.HALTED);
                }
            });
        } catch (InterruptedException e) {
            log.info("Proxy server interrupt: {}", e.getMessage());
        }
    }

    public void shutdown() {
        log.info("--- Shutting down proxy server ---");
        if (channelFuture != null) {
            channelFuture.channel().close();
        }
    }

    public synchronized void restart() {
        log.info("--- Restarting proxy server ---");
        if (channelFuture != null) {
            channelFuture.channel().close().syncUninterruptibly();
        }
        start();
    }

    @PostConstruct
    private void init() {
        setStatus(ServerStatus.INIT);
        if (!initialStartScheduled.compareAndSet(false, true)) {
            return;
        }
        applicationConfig.clientSslContextReady().whenComplete((sslContext, error) -> {
            try {
                ThreadPoolService.getInstance().run(() -> startAfterTlsInitialization(error));
            } catch (RuntimeException rejected) {
                log.error("Unable to schedule proxy server startup.", rejected);
                setStatus(ServerStatus.HALTED);
            }
        });
    }

    private void startAfterTlsInitialization(Throwable tlsError) {
        if (tlsError != null) {
            Throwable cause = tlsError instanceof CompletionException && tlsError.getCause() != null
                    ? tlsError.getCause() : tlsError;
            log.warn("Upstream TLS context initialization failed; starting with HTTPS interception disabled: {}",
                    cause.getMessage());
            log.debug("Upstream TLS context initialization failure", cause);
        }
        try {
            if (!standalone && !WebUtils.isPortAvailable(applicationConfig.getSettings().getPort())) {
                throw new IllegalStateException(
                        "Proxy port is unavailable: " + applicationConfig.getSettings().getPort());
            }
            start();
        } catch (Exception e) {
            log.error("Error in starting proxy server.", e);
            setStatus(ServerStatus.HALTED);
        }
    }
}

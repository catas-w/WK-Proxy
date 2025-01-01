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

@Slf4j
@Singleton
public class ProxyServer {

    public static boolean standalone;

    @Inject
    private ApplicationConfig applicationConfig;

    @Inject
    private ServerChannelInitializer proxyServerInitializer;

    private ChannelFuture channelFuture;

    public void setStatus(ServerStatus status) {
        applicationConfig.getObservableConfig().setServerStatus(status);
    }

    public void start() {
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

    @PostConstruct
    private void init() {
        setStatus(ServerStatus.INIT);
        if (standalone) {
            start();
        } else {
            ThreadPoolService.getInstance().run(() -> {
                System.out.println("Starting");
                try {
                    boolean portAvailable = WebUtils.isPortAvailable(applicationConfig.getSettings().getPort());
                    if (!portAvailable) {
                        throw new RuntimeException();
                    }
                    start();
                } catch (Exception e) {
                    log.error("Error in starting proxy server.", e);
                    setStatus(ServerStatus.HALTED);
                    // String msg = "Port: " + applicationConfig.getSettings().getPort() + " is unavailable, change port in settings";
                    // AlertUtils.alertLater(Alert.AlertType.ERROR, msg);
                }
            });
        }
    }
}

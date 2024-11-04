package com.catas.wicked.common.config;

import com.catas.wicked.common.bean.message.RetryMessage;
import com.catas.wicked.common.executor.ScheduledThreadPoolService;
import com.catas.wicked.common.pipeline.MessageQueue;
import com.catas.wicked.common.executor.ThreadPoolService;
import com.catas.wicked.common.pipeline.Topic;
import com.catas.wicked.common.util.AlertUtils;
import com.catas.wicked.common.util.SystemUtils;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Slf4j
@Data
@Singleton
public class ApplicationConfig implements AutoCloseable {

    private String host = "127.0.0.1";

    private Integer defaultThreadNumber = 2;

    private EventLoopGroup proxyLoopGroup;

    private Settings settings;

    /**
     * ssl configs
     */
    private SslContext clientSslCtx;
    private String issuer;
    private Date caNotBefore;
    private Date caNotAfter;
    private PrivateKey caPriKey;
    private PrivateKey serverPriKey;
    private PublicKey serverPubKey;

    private final ObservableConfig observableConfig = new ObservableConfig();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicBoolean shutDownFlag = new AtomicBoolean(false);

    @Inject
    private MessageQueue messageQueue;

    @PostConstruct
    public void init() {
        this.proxyLoopGroup = new NioEventLoopGroup(defaultThreadNumber);

        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        try {
            loadSettings();
        } catch (Exception e) {
            log.error("Error loading local configuration.", e);
        }

        loadSslContext();
        // System.setProperty("https.protocols", "TLSv1,TLSv1.1,TLSv1.2");

        // update settings file
        messageQueue.subscribe(Topic.UPDATE_SETTING_FILE, msg -> {
            if (msg == null) {
                return;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {}
            // TODO
            System.out.println("update settings File " + System.currentTimeMillis());
            updateSettingFile();
        });
    }

    public void loadSslContext() {
        ThreadPoolService.getInstance().submit(() -> {
            try {
                long start = System.currentTimeMillis();
                SslContextBuilder contextBuilder = SslContextBuilder.forClient()
                        .sslProvider(SslProvider.OPENSSL)
                        .startTls(true)
                        .protocols("TLSv1.1", "TLSv1.2", "TLSv1")
                        .trustManager(InsecureTrustManagerFactory.INSTANCE);
                setClientSslCtx(contextBuilder.build());
                long end = System.currentTimeMillis();
                log.info("loadSslContext finished, time cost: {} ms", end - start);
            } catch (Exception e) {
                log.error("loadSslContext error: ", e);
                settings.setHandleSsl(false);
            }
        });
    }

    private File getLocalConfigFile() throws IOException {
        Path configPath = SystemUtils.getStoragePath("config.json");
        return configPath.toFile();
    }

    public void loadSettings() throws IOException {
        File file = getLocalConfigFile();
        if (!file.exists()) {
            log.info("Settings file not exist.");
            settings = new Settings();
            return;
        }

        settings = objectMapper.readValue(file, Settings.class);
        if (settings.isEnableSysProxyOnLaunch()) {
            // force update systemProxy
            settings.setSystemProxy(true);
            messageQueue.pushMsg(Topic.SET_SYS_PROXY, new RetryMessage());
        }
    }

    public void updateSettingFile() {
        try {
            File file = getLocalConfigFile();
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
            objectMapper.writeValue(file, settings);
        } catch (IOException e) {
            log.error("Error updating local config.", e);
            AlertUtils.alertWarning("Error in updating settings.");
        }
    }

    public void updateSettingsAsync() {
        ThreadPoolService.getInstance().run(this::updateSettingFile);
    }

    public int getMaxContentSize() {
        if (settings == null || settings.getMaxContentSize() <= 0) {
            return 1024 * 1024;
        }
        return settings.getMaxContentSize() * 1024 * 1024;
    }

    /**
     * set root certificate
     * @param issuer issuer
     * @param caCert X509Certificate
     * @param caPriKey privateKey
     */
    public void updateRootCertConfigs(String issuer, X509Certificate caCert, PrivateKey caPriKey) {
        this.issuer = issuer;
        this.caNotBefore = caCert.getNotBefore();
        this.caNotAfter = caCert.getNotAfter();
        this.caPriKey = caPriKey;
    }

    public void shutDownApplication() {
        shutDownFlag.compareAndSet(false, true);
        if (!(proxyLoopGroup.isShutdown() || proxyLoopGroup.isShuttingDown())) {
            proxyLoopGroup.shutdownGracefully();
        }

        messageQueue.shutdown();
        ThreadPoolService.getInstance().shutdown();
        ScheduledThreadPoolService.getInstance().shutdown();
    }

    @PreDestroy
    @Override
    public void close() {
        shutDownApplication();
    }

}

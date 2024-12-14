package com.catas.wicked.proxy;

import app.supernaut.fx.ApplicationDelegate;
import app.supernaut.fx.FxLauncher;
import app.supernaut.fx.fxml.FxmlLoaderFactory;
import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.proxy.gui.controller.AppController;
import com.catas.wicked.proxy.message.MessageService;
import com.catas.wicked.common.provider.StageProvider;
import com.catas.wicked.server.proxy.ProxyServer;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import jakarta.inject.Singleton;

import java.util.Locale;
import java.util.ResourceBundle;

@Slf4j
// @Import(packages = {
//         "com.catas.wicked.server.proxy",
//         "com.catas.wicked.server.cert",
//         "com.catas.wicked.server.cert.spi",
//         "com.catas.wicked.server.handler.server"
// })
@Singleton
public class WickedProxyApplication implements ApplicationDelegate {

    @Inject
    private ApplicationConfig applicationConfig;

    @Inject
    private FxmlLoaderFactory loaderFactory;

    @Inject
    private MessageService messageService;

    @Inject
    private ProxyServer proxyServer;

    @Inject
    private AppController appController;

    @Inject
    @Nullable
    private StageProvider stageProvider;

    public static void main(String[] args) {
        FxLauncher.find().launch(args, WickedProxyApplication.class);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        Locale locale = applicationConfig.getSettings().getLanguage().getLocale();
        ResourceBundle bundle = ResourceBundle.getBundle("lang.messages", locale);

        FXMLLoader loader = loaderFactory.get(WickedProxyApplication.class.getResource("/fxml/application.fxml"));
        loader.setResources(bundle);

        Parent root = loader.load();
        // Scene scene = new Scene(root, 1000, 680);
        Scene scene = new Scene(root, 1100, 750);
        scene.setFill(Color.TRANSPARENT);

        if (stageProvider != null) {
            stageProvider.initStage(primaryStage);
        }

        primaryStage.getIcons().add(new Image(String.valueOf(WickedProxyApplication.class.getResource("/image/wk-proxy.2.ico"))));
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    @Override
    public void stop() throws Exception {
        log.info("---- Stopping Application ----");
        proxyServer.shutdown();
        applicationConfig.shutDownApplication();
    }
}

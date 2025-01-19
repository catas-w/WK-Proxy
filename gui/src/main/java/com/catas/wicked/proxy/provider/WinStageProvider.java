package com.catas.wicked.proxy.provider;

import com.catas.wicked.common.provider.StageProvider;
import com.catas.wicked.common.provider.WinCondition;
import io.micronaut.context.annotation.Requires;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Requires(condition = WinCondition.class)
@Singleton
public class WinStageProvider implements StageProvider {

    private Image appIcon;

    @PostConstruct
    public void init() {
        try {
            appIcon = new Image(String.valueOf(getClass().getResource("/image/wk-proxy.2.png")));
        } catch (Exception e) {
            log.error("Error in loading app icon.", e);
        }
    }

    @Override
    public void initStage(Stage primaryStage) {
        log.info("init stage for windows");
        primaryStage.setTitle("WK Proxy");
        if (appIcon != null) {
            primaryStage.getIcons().add(appIcon);
        }
    }

    @Override
    public void setAppIcon(Stage stage) {
        if (stage != null && appIcon != null && stage.getIcons().isEmpty()) {
            stage.getIcons().add(appIcon);
        }
    }
}

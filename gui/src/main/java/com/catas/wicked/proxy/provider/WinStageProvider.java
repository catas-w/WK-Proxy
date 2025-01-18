package com.catas.wicked.proxy.provider;

import com.catas.wicked.common.provider.StageProvider;
import com.catas.wicked.common.provider.WinCondition;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Requires(condition = WinCondition.class)
@Singleton
public class WinStageProvider implements StageProvider {


    @Override
    public void initStage(Stage primaryStage) {
        log.info("init stage for windows");
        primaryStage.setTitle("WK Proxy");
        primaryStage.getIcons().add(new Image(String.valueOf(getClass().getResource("/image/wk-proxy.2.png"))));
    }
}

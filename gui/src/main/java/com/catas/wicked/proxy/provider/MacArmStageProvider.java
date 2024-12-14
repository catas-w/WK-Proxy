package com.catas.wicked.proxy.provider;

import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.constant.BlurOption;
import com.catas.wicked.common.jna.FoundationLibrary;
import com.catas.wicked.common.jna.WindowBlurLibrary;
import com.catas.wicked.common.provider.MacArmCondition;
import com.catas.wicked.common.provider.StageProvider;
import com.catas.wicked.proxy.gui.controller.AppController;
import com.sun.javafx.stage.WindowHelper;
import com.sun.javafx.tk.quantum.WindowStage;
import com.sun.jna.NativeLong;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@Requires(condition = MacArmCondition.class)
public class MacArmStageProvider implements StageProvider {

    @Inject
    private ApplicationConfig appConfig;

    @Inject
    private AppController appController;

    private HBox customTitleBar;

    private double xOffset = 0;
    private double yOffset = 0;
    private double titleBarHeight;

    private static final String TITLE_BAR_STYLE = "custom-title-bar";

    @Override
    public void initStage(Stage primaryStage) {
        // create custom titleBar
        customTitleBar = new HBox();
        customTitleBar.getStyleClass().add(TITLE_BAR_STYLE);
        appController.getRootVBox().getChildren().add(0, customTitleBar);

        customTitleBar.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });

        // listen on dragged event
        customTitleBar.setOnMouseDragged(event -> {
            primaryStage.setX(event.getScreenX() - xOffset);
            primaryStage.setY(event.getScreenY() - yOffset);
        });

        // set NSVisualEffectView
        primaryStage.initStyle(StageStyle.UNIFIED);
        primaryStage.setOnShown((windowEvent -> {
            setBlurWindow(getNativeHandleOfStage(primaryStage), BlurOption.VIBRANT_LIGHT);
        }));

        primaryStage.fullScreenProperty().addListener((observable, oldValue, newValue) -> {
            displayOnTitleBar(newValue);
        });
    }

    /**
     * play animation
     */
    private void displayOnTitleBar(boolean fullScreen) {
        if (customTitleBar == null) {
            return;
        }

        double target;
        double duration;
        if (fullScreen) {
            // hide
            titleBarHeight = customTitleBar.getHeight();
            target = 0;
            duration = 200.0;
        } else {
            // show
            target = titleBarHeight;
            duration = 50.0;
        }
        Timeline timeline = new Timeline();
        KeyValue keyValue = new KeyValue(customTitleBar.prefHeightProperty(), target);
        KeyFrame keyFrame = new KeyFrame(Duration.millis(duration), keyValue);

        timeline.getKeyFrames().add(keyFrame);
        timeline.play();
    }

    private void setBlurWindow(long windowPtr, BlurOption blurOption) {
        if (blurOption == null) {
            throw new IllegalArgumentException();
        }
        setBlurWindow(windowPtr, blurOption.getNativeName());
    }

    private void setBlurWindow(long windowPtr, String nativeAppearanceName) {
        NativeLong nsWindow = new NativeLong(windowPtr);
        WindowBlurLibrary.INSTANCE.setBlurWindow(nsWindow, FoundationLibrary.fromJavaString(nativeAppearanceName));
    }

    private long getNativeHandleOfStage(Window stage) {
        try {
            WindowStage peer = (WindowStage) WindowHelper.getPeer(stage);
            com.sun.glass.ui.Window platformWindow = peer.getPlatformWindow();
            return platformWindow.getNativeWindow();
        } catch (Exception ex) {
            log.error("Error in getting native window pointer: ", ex);
            return 0L;
        }
    }
}

package com.catas.wicked.proxy.gui.controller;

import com.catas.wicked.proxy.gui.componet.skin.CustomJFXProgressBarSkin;
import com.jfoenix.animation.alert.JFXAlertAnimation;
import com.jfoenix.controls.JFXAlert;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.controls.JFXProgressBar;
import jakarta.inject.Singleton;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.fxml.Initializable;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.util.Duration;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.net.URL;
import java.util.Objects;
import java.util.ResourceBundle;

@Slf4j
@Singleton
public class AppUpdateController implements Initializable {

    @Getter
    public JFXDialogLayout dialogLayout;

    public JFXProgressBar updateProgressBar;

    public JFXButton closeUpdateDialogBtn;

    private Timeline timeline;

    @Setter
    @Getter
    private JFXAlert<Void> alert;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        updateProgressBar.setSkin(new CustomJFXProgressBarSkin(updateProgressBar));
        dialogLayout.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/update-dialog.css")).toExternalForm());
        dialogLayout.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/app.css")).toExternalForm());

        timeline = new Timeline(
                new KeyFrame(
                        Duration.ZERO,
                        new KeyValue(updateProgressBar.secondaryProgressProperty(), 0),
                        new KeyValue(updateProgressBar.progressProperty(), 0)),
                new KeyFrame(
                        Duration.seconds(1),
                        new KeyValue(updateProgressBar.secondaryProgressProperty(), 1)),
                new KeyFrame(
                        Duration.seconds(2),
                        new KeyValue(updateProgressBar.progressProperty(), 1)));

        timeline.setCycleCount(Timeline.INDEFINITE);
    }

    public void initAlert(Window window) {
        JFXAlert<Void> alert = new JFXAlert<>(window);
        alert.setOverlayClose(true);
        alert.setAnimation(JFXAlertAnimation.CENTER_ANIMATION);
        alert.setContent(dialogLayout);
        alert.initModality(Modality.NONE);
        this.alert = alert;

        closeUpdateDialogBtn.setOnAction(e -> {
            timeline.stop();
            alert.close();
        });
    }

    public void showAlert() {
        if (alert != null) {
            timeline.play();
            alert.showAndWait();
        }
    }
}

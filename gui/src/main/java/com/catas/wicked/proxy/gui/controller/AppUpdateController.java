package com.catas.wicked.proxy.gui.controller;

import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.executor.ThreadPoolService;
import com.catas.wicked.common.provider.DesktopProvider;
import com.catas.wicked.common.provider.ResourceMessageProvider;
import com.catas.wicked.common.provider.VersionCheckProvider;
import com.catas.wicked.common.util.AlertUtils;
import com.catas.wicked.common.util.CommonUtils;
import com.catas.wicked.proxy.gui.componet.skin.CustomJFXProgressBarSkin;
import com.jfoenix.animation.alert.JFXAlertAnimation;
import com.jfoenix.controls.JFXAlert;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.controls.JFXProgressBar;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

import java.net.URL;
import java.util.Objects;
import java.util.ResourceBundle;

@Slf4j
@Singleton
public class AppUpdateController implements Initializable {

    @Getter
    @FXML
    private JFXDialogLayout dialogLayout;

    @FXML
    private JFXProgressBar updateProgressBar;

    @FXML
    private JFXButton closeUpdateDialogBtn;

    private Timeline timeline;

    @Setter
    @Getter
    private JFXAlert<Void> alert;

    @Inject
    private ApplicationConfig appConfig;

    @Inject
    private ResourceMessageProvider messageProvider;

    @Inject
    private VersionCheckProvider versionCheckProvider;

    @Inject
    private DesktopProvider desktopProvider;

    public static final String RELEASE_URL = "https://github.com/catas-w/WK-Proxy/releases/latest";

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

        closeUpdateDialogBtn.setOnAction(e -> closeAlert());
    }

    public void checkUpdateAndShowAlert() {
        if (alert != null) {
            timeline.play();
            ThreadPoolService.getInstance().submit(this::checkUpdate);
            alert.showAndWait();
        }
    }

    public void closeAlert() {
        timeline.stop();
        alert.close();
    }

    private void checkUpdate() {
        try {
            // make animation run for a while
            Thread.sleep(500);

            Pair<String, String> res = versionCheckProvider.fetchLatestVersion();
            String version = res.getLeft();
            String appVersion = "Wk-Proxy " + version;
            if (CommonUtils.compareVersions(appConfig.getAppVersion(), version) >= 0) {
                AlertUtils.alertLater(Alert.AlertType.INFORMATION,
                        appVersion + " " + messageProvider.getMessage("check-update.latest-version.label"));
            } else {
                String info = messageProvider.getMessage("check-update.new-release.label") + " " + version;
                Platform.runLater(() -> {
                    displayDownloadDialog(Alert.AlertType.INFORMATION, info, res.getRight());
                });
            }
        } catch (Exception e) {
            log.error("Error in fetching update information.", e);
            String msg = messageProvider.getMessage("check-update.error.label");
            Platform.runLater(() -> {
                displayDownloadDialog(Alert.AlertType.WARNING, msg, RELEASE_URL);
            });
        } finally {
            closeAlert();
        }
    }

    private void displayDownloadDialog(Alert.AlertType alertType, String info, String url) {
        // Create the Alert
        Alert alert = new Alert(alertType);
        alert.setTitle("");
        alert.setHeaderText(null);
        alert.setContentText(null);

        Label label = new Label(info);
        Hyperlink hyperlink = new Hyperlink(url);

        // Set an action for the Hyperlink
        hyperlink.setOnAction(event -> {
            try {
                // java.awt.Desktop.getDesktop().browse(new java.net.URI(hyperlink.getText()));
                desktopProvider.browseOnLocal(hyperlink.getText());
            } catch (Exception e) {
                log.error("Error in opening github link", e);
            }
        });

        // Add the Label and Hyperlink to a VBox
        VBox content = new VBox(10, label, hyperlink);
        Stage alertStage = (Stage) alert.getDialogPane().getScene().getWindow();
        alertStage.setAlwaysOnTop(true);

        alert.getDialogPane().setContent(content);
        alert.getDialogPane().getStyleClass().add("custom-alert");
        alert.getDialogPane().getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/app.css")).toExternalForm());
        // alert.getDialogPane().requestFocus();
        alert.showAndWait();
    }
}

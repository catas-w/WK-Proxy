package com.catas.wicked.proxy.gui.controller;

import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.executor.ThreadPoolService;
import com.catas.wicked.common.provider.ResourceMessageProvider;
import com.catas.wicked.common.util.AlertUtils;
import com.catas.wicked.common.util.CommonUtils;
import com.catas.wicked.proxy.gui.componet.skin.CustomJFXProgressBarSkin;
import com.catas.wicked.server.client.MinimalHttpClient;
import com.jfoenix.animation.alert.JFXAlertAnimation;
import com.jfoenix.controls.JFXAlert;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.controls.JFXProgressBar;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
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

import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    public void showAlert() {
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
        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("Accept-Encoding", "gzip, deflate, br");
        headerMap.put("Accept", "*/*");
        headerMap.put("Host", "GitHub.com");
        headerMap.put("Connection", "keep-alive");
        LinkedHashMap<String, String> headersMap = new LinkedHashMap<>();

        try {
            MinimalHttpClient client = MinimalHttpClient.builder()
                    .uri(RELEASE_URL)
                    .method(HttpMethod.GET)
                    .headers(headerMap)
                    .fullResponse(true)
                    .build();
            client.execute();
            HttpResponse response = client.response();
            HttpHeaders headers = response.headers();
            headers.forEach(item -> {
                headersMap.put(item.getKey(), item.getValue());
            });
        } catch (Exception e) {
            log.error("Error in fetching update information.", e);
            AlertUtils.alertLater(Alert.AlertType.ERROR, messageProvider.getMessage("check-update.error.label"));
        } finally {
            closeAlert();
        }

        String location = headersMap.getOrDefault("Location", "");
        Pattern pattern = Pattern.compile("tag/(.+)");
        Matcher matcher = pattern.matcher(location);

        if (matcher.find()) {
            String version = matcher.group(1);
            String appVersion = "Wk-Proxy " + matcher.group(1);
            if (CommonUtils.compareVersions(appConfig.getAppVersion(), version) >= 0) {
                AlertUtils.alertLater(Alert.AlertType.INFORMATION,
                        appVersion + messageProvider.getMessage("check-update.latest-version.label"));
            } else {
                String info = messageProvider.getMessage("check-update.new-release.label") + " " + version;
                Platform.runLater(() -> {
                    displayDownloadDialog(info, location);
                });
            }
        } else {
            AlertUtils.alertWarning(messageProvider.getMessage("check-update.error.label"));
        }
    }

    private void displayDownloadDialog(String info, String url) {
        // Create the Alert
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("");
        alert.setHeaderText(null);
        alert.setContentText(null);

        Label label = new Label(info);
        Hyperlink hyperlink = new Hyperlink(url);

        // Set an action for the Hyperlink
        hyperlink.setOnAction(event -> {
            try {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(hyperlink.getText()));
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

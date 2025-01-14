package com.catas.wicked.proxy.gui.controller;

import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.constant.ServerStatus;
import com.catas.wicked.common.provider.ResourceMessageProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.ResourceBundle;

@Slf4j
@Singleton
public class AppController implements Initializable {

    @FXML
    private FontIcon certStatusIcon;
    @FXML
    private Label certStatusLabel;
    @FXML
    private FontIcon serverStatusIcon;
    @FXML
    private Label serverStatusLabel;
    @FXML
    @Getter
    private VBox rootVBox;

    @Inject
    private ApplicationConfig appConfig;

    @Inject
    private ButtonBarController buttonBarController;

    @Inject
    private ResourceMessageProvider resourceMessageProvider;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // versionLabel.setText("version " + ApplicationConfig.APP_VERSION);

        // update server status label
        refreshServerStatusDisplay(appConfig.getObservableConfig().getServerStatus());
        appConfig.getObservableConfig().serverStatusProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                return;
            }
            // System.out.println("refresh server status: " + newValue);
            refreshServerStatusDisplay(newValue);
        });

        serverStatusLabel.setOnMouseClicked(event -> {
            if (!serverStatusLabel.getStyleClass().contains("error")) {
                return;
            }

            buttonBarController.displayProxySettingPage();
        });

        // cert status
        appConfig.getObservableConfig().certInstalledStatusProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                return;
            }
            boolean handleSsl = appConfig.getObservableConfig().isHandlingSSL();
            certStatusIcon.setVisible(!newValue && handleSsl);
            certStatusLabel.setVisible(!newValue && handleSsl);
        });
        appConfig.getObservableConfig().handlingSSLProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                return;
            }
            boolean certInstalled = appConfig.getObservableConfig().isCertInstalledStatus();
            certStatusIcon.setVisible(newValue && !certInstalled);
            certStatusLabel.setVisible(newValue && !certInstalled);
        });

        certStatusLabel.setOnMouseClicked(event -> {
            buttonBarController.displaySSlSettingPage();
        });
    }

    public void refreshServerStatusDisplay(ServerStatus serverStatus) {
        if (serverStatus == null) {
            return;
        }
        switch (serverStatus) {
            case INIT -> {
                serverStatusIcon.getStyleClass().removeIf(style -> style.equals("error") || style.equals("healthy"));
                setStatusLabel(false);
            }
            case RUNNING -> {
                serverStatusIcon.getStyleClass().removeIf(style -> style.equals("error"));
                serverStatusIcon.getStyleClass().add("healthy");

                serverStatusLabel.getStyleClass().removeIf(style -> style.equals("error"));
                setStatusLabel(false);
            }
            case HALTED -> {
                serverStatusIcon.getStyleClass().removeIf(style -> style.equals("healthy"));
                serverStatusIcon.getStyleClass().add("error");

                serverStatusLabel.getStyleClass().add("error");
                setStatusLabel(true);
            }
        }
    }

    private void setStatusLabel(boolean isError) {
        String portLabel = resourceMessageProvider.getMessage("port.label") + ": ";
        String msg = resourceMessageProvider.getMessage("port-unavailable.alert");
        String label = isError ? portLabel + appConfig.getSettings().getPort() + msg:
                appConfig.getHost() + ":" + appConfig.getSettings().getPort();
        Platform.runLater(() -> serverStatusLabel.setText(label));
    }
}

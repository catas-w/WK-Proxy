package com.catas.wicked.proxy.gui.controller;

import com.catas.wicked.common.bean.message.DeleteMessage;
import com.catas.wicked.common.bean.message.RenderMessage;
import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.config.ExternalProxyConfig;
import com.catas.wicked.common.constant.ProxyProtocol;
import com.catas.wicked.common.constant.SystemProxyStatus;
import com.catas.wicked.common.constant.WorkerConstant;
import com.catas.wicked.common.pipeline.MessageQueue;
import com.catas.wicked.common.pipeline.Topic;
import com.catas.wicked.common.executor.ThreadPoolService;
import com.catas.wicked.common.worker.ScheduledManager;
import com.catas.wicked.proxy.message.MessageService;
import com.catas.wicked.proxy.service.RequestMockService;
import com.catas.wicked.server.client.MinimalHttpClient;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXToggleNode;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.ehcache.Cache;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;

import static com.catas.wicked.common.constant.StyleConstant.COLOR_ACTIVE;
import static com.catas.wicked.common.constant.StyleConstant.COLOR_INACTIVE;
import static com.catas.wicked.common.constant.StyleConstant.COLOR_RED;
import static com.catas.wicked.common.constant.StyleConstant.COLOR_SUSPEND;

@Slf4j
@Singleton
public class ButtonBarController implements Initializable {

    public Node settingScene;
    public JFXButton markerBtn;
    public JFXToggleNode recordBtn;
    public JFXToggleNode sslBtn;
    public JFXButton locateBtn;
    public JFXButton resendBtn;
    public JFXToggleNode throttleBtn;
    public JFXToggleNode sysProxyBtn;
    public JFXButton clearBtn;

    private Dialog<Node> settingPage;

    @Inject
    private MessageQueue messageQueue;

    @Inject
    private ApplicationConfig appConfig;

    @Inject
    private Cache<String, RequestMessage> requestCache;

    @Inject
    private RequestMockService requestMockService;

    @Inject
    private RequestViewController requestViewController;

    @Inject
    private ScheduledManager scheduledManager;

    @Inject
    private SettingController settingController;

    @Setter
    private MessageService messageService;

    private String settingDialogTitle = "Settings";

    @SneakyThrows
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        settingDialogTitle = resourceBundle.getString("setting-dialog.title");
        // listen on current request
        appConfig.getObservableConfig().currentRequestIdProperty().addListener((observable, oldValue, newValue) -> {
            // System.out.println("reqId: " + newValue);
            resendBtn.setDisable(newValue == null || newValue.startsWith(RenderMessage.PATH_MSG));
            locateBtn.setDisable(newValue == null);
        });
        locateBtn.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                requestViewController.updateFocusPseudoClass(newValue);
            }
        });

        // clear request event
        messageService.getRequestCntProperty().addListener((observable, oldValue, newValue) -> {
            // System.out.println("current count: " + newValue.intValue());
            if (newValue.intValue() < 0) {
                clearBtn.setDisable(true);
            } else {
                clearBtn.setDisable(false);
                FontIcon icon = (FontIcon) clearBtn.getGraphic();
                String targetIcon = newValue.intValue() == 0 ? "fas-broom": "fas-quidditch";
                icon.setIconLiteral(targetIcon);
            }
        });

        // toggle record button
        recordBtn.selectedProperty().addListener((observable, oldValue, newValue) -> {
            FontIcon icon = (FontIcon) recordBtn.getGraphic();
            if (newValue) {
                icon.setIconLiteral("fas-record-vinyl");
                icon.setIconColor(COLOR_RED);
            } else {
                icon.setIconLiteral("far-play-circle");
                icon.setIconColor(COLOR_INACTIVE);
            }
            appConfig.getSettings().setRecording(newValue);

            // String toolTip = newValue ? "Stop Recording" : "Record Requests";
            String toolTip = resourceBundle.getString(newValue ? "record-btn.disable.tooltip": "record-btn.enable.tooltip");
            recordBtn.getTooltip().setText(toolTip);
        });
        // System.out.println(resourceBundle.getString("record-btn.tooltip"));
        recordBtn.setSelected(true);

        // toggle handle ssl button
        sslBtn.selectedProperty().addListener((observable, oldValue, newValue) -> {
            FontIcon icon = (FontIcon) sslBtn.getGraphic();
            Color color = newValue ? COLOR_ACTIVE : COLOR_INACTIVE;
            icon.setIconColor(color);
            appConfig.setHandleSSL(newValue);
            appConfig.updateSettingsAsync();

            // String toolTip = newValue ? "Stop Handling SSL" : "Handle SSL";
            String toolTip = resourceBundle.getString(newValue ? "ssl-btn.disable.tooltip": "ssl-btn.enable.tooltip");
            sslBtn.getTooltip().setText(toolTip);
        });
        sslBtn.setSelected(appConfig.getSettings().isHandleSsl());

        // init throttle button
        throttleBtn.selectedProperty().addListener((observable, oldValue, newValue) -> {
            FontIcon icon = (FontIcon) throttleBtn.getGraphic();
            Color color = newValue ? COLOR_ACTIVE : COLOR_INACTIVE;
            icon.setIconColor(color);
            appConfig.getSettings().setThrottle(newValue);
            appConfig.updateSettingsAsync();

            // String toolTip = newValue ? "Stop Throttling" : "Start Throttling";
            String toolTip = resourceBundle.getString(newValue ? "throttle-btn.disable.tooltip": "throttle-btn.enable.tooltip");
            throttleBtn.getTooltip().setText(toolTip);
        });
        throttleBtn.setSelected(appConfig.getSettings().isThrottle());

        // init sysProxyBtn
        sysProxyBtn.selectedProperty().addListener((observable, oldValue, newValue) -> {
            FontIcon icon = (FontIcon) sysProxyBtn.getGraphic();
            Color color = newValue ? COLOR_ACTIVE : COLOR_INACTIVE;
            icon.setIconColor(color);
        });
        appConfig.getObservableConfig().systemProxyStatusProperty().addListener((observable, oldValue, newValue) -> {
            sysProxyBtn.setDisable(newValue == SystemProxyStatus.DISABLED);
            sysProxyBtn.setSelected(newValue == SystemProxyStatus.ON);

            // SUSPENDED 状态视为 unselected, 只能流转为 selected
            if (newValue == SystemProxyStatus.SUSPENDED) {
                FontIcon icon = (FontIcon) sysProxyBtn.getGraphic();
                icon.setIconColor(COLOR_SUSPEND);
            }
        });
    }

    public void mockTreeItem() {
        markerBtn.setOnAction(event -> {
            requestMockService.mockRequest();
        });
    }

    public void displayProxySettingPage() {
        if (settingPage == null) {
            initSettingPage();
        }
        settingController.getSettingTabPane().getSelectionModel().select(settingController.getProxySettingTab());
        displaySettingPage();
    }

    public void displayAboutPage() {
        if (settingPage == null) {
            initSettingPage();
        }
        settingController.getSettingTabPane().getSelectionModel().select(settingController.getInfoSettingTab());
        displaySettingPage(320);
    }

    public void displaySSlSettingPage() {
        if (settingPage == null) {
            initSettingPage();
        }
        settingController.getSettingTabPane().getSelectionModel().select(settingController.getSslSettingTab());
        displaySettingPage(500);
    }

    public void displaySettingPage() {
        displaySettingPage(400);
    }

    public void displaySettingPage(long targetHeight) {
        if (settingPage == null) {
            initSettingPage();
        }

        settingController.initValues();
        adjustSettingDialogHeight(targetHeight);
        settingPage.showAndWait();
    }

    /**
     * init settings page
     */
    private void initSettingPage() {
        settingController.setButtonBarController(this);

        settingPage = new Dialog<>();
        settingPage.setTitle(this.settingDialogTitle);
        settingPage.initModality(Modality.APPLICATION_MODAL);
        DialogPane dialogPane = settingPage.getDialogPane();
        dialogPane.setContent(settingScene);
        dialogPane.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/dialog.css")).toExternalForm());
        dialogPane.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/app.css")).toExternalForm());
        dialogPane.getStyleClass().add("myDialog");
        Window window = dialogPane.getScene().getWindow();
        window.setOnCloseRequest(e -> window.hide());
    }

    public void adjustSettingDialogHeight(double targetHeight) {
        if (settingPage == null || settingPage.getDialogPane() == null) {
            return;
        }

        settingPage.getDialogPane().setPrefHeight(targetHeight);
        settingPage.getDialogPane().setMaxHeight(targetHeight);
        Stage stage = (Stage) settingPage.getDialogPane().getScene().getWindow();
        stage.sizeToScene();
    }

    /**
     * scroll to selected item
     */
    public void locateToSelectedItem() {
        requestViewController.focus();
    }

    /**
     * resend selected request
     */
    public void resendRequest() {
        String requestId = appConfig.getObservableConfig().getCurrentRequestId();
        if (StringUtils.isBlank(requestId)) {
            return;
        }
        RequestMessage requestMessage = requestCache.get(requestId);
        if (requestMessage == null || requestMessage.isEncrypted() || requestMessage.isOversize()) {
            log.warn("Not integrated http request, unable to resend");
            return;
        }

        ThreadPoolService.getInstance().run(() -> {
            String url = requestMessage.getRequestUrl();
            String method = requestMessage.getMethod();
            String protocol = requestMessage.getProtocol();
            Map<String, String> headers = requestMessage.getHeaders();
            byte[] content = requestMessage.getBody();

            ExternalProxyConfig proxyConfig = new ExternalProxyConfig();
            proxyConfig.setProtocol(ProxyProtocol.HTTP);
            proxyConfig.setProxyAddress(appConfig.getHost(), appConfig.getSettings().getPort());

            try (MinimalHttpClient client = MinimalHttpClient.builder()
                    .uri(url)
                    .method(HttpMethod.valueOf(method))
                    .httpVersion(protocol)
                    .headers(headers)
                    .content(content)
                    .proxyConfig(proxyConfig)
                    .build()) {
                client.execute();
                HttpResponse response = client.response();
                log.info("Get response in resending: {}", response);
            } catch (Exception e) {
                log.error("Error in resending request: {}", requestMessage.getRequestUrl());
            }
        });
    }

    public void exit() {
        Platform.exit();
    }

    /**
     * clear or deleteAll
     */
    public void clearLeafNode(ActionEvent event) {
        if (messageService.getRequestCntProperty().get() == 0) {
            deleteAll();
        } else {
            requestViewController.clearLeafNode();
        }
    }

    /**
     * delete all requests
     */
    public void deleteAll() {
        DeleteMessage deleteMessage = new DeleteMessage();
        deleteMessage.setRemoveAll(true);
        messageQueue.pushMsg(Topic.RECORD, deleteMessage);
    }

    public void onSysProxy(ActionEvent actionEvent) {
        appConfig.getSettings().setSystemProxy(sysProxyBtn.selectedProperty().get());
        scheduledManager.invoke(WorkerConstant.SYS_PROXY_WORKER);
    }

    public void updateThrottleBtn(boolean selected) {
        throttleBtn.setSelected(selected);
    }

    public void updateSSlBtn(boolean selected) {
        sslBtn.setSelected(selected);
    }
}

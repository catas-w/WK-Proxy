package com.catas.wicked.proxy.gui.controller;

import com.catas.wicked.common.bean.message.DeleteMessage;
import com.catas.wicked.common.bean.message.RenderMessage;
import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.constant.SystemProxyStatus;
import com.catas.wicked.common.constant.WorkerConstant;
import com.catas.wicked.common.pipeline.MessageQueue;
import com.catas.wicked.common.pipeline.Topic;
import com.catas.wicked.common.worker.ScheduledManager;
import com.catas.wicked.proxy.gui.componet.button.UnderLabelWrapper;
import com.catas.wicked.proxy.gui.componet.button.WKToggleNode;
import com.catas.wicked.proxy.gui.componet.button.WkButton;
import com.catas.wicked.proxy.message.MessageService;
import com.catas.wicked.proxy.service.RequestMockService;
import com.jfoenix.controls.JFXButton;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.ehcache.Cache;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.Objects;
import java.util.ResourceBundle;

import static com.catas.wicked.common.constant.StyleConstant.COLOR_SUSPEND;

@Slf4j
@Singleton
public class ButtonBarController implements Initializable {

    @FXML
    private VBox buttonBox;
    @FXML
    private Node settingScene;
    @FXML
    private WKToggleNode recordBtn;
    @FXML
    private WKToggleNode sslBtn;
    @FXML
    private JFXButton locateBtn;
    @FXML
    private JFXButton resendBtn;
    @FXML
    private WKToggleNode throttleBtn;
    @FXML
    private WKToggleNode sysProxyBtn;
    @FXML
    private WkButton clearBtn;
    @FXML
    private MenuItem checkUpdateBtn;

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
    @Inject
    private AppUpdateController appUpdateController;

    @Setter
    private MessageService messageService;
    private Dialog<Node> settingPage;
    private String settingDialogTitle = "Settings";

    @SneakyThrows
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        settingDialogTitle = resourceBundle.getString("setting-dialog.title");
        // listen on current request
        appConfig.getObservableConfig().currentRequestIdProperty().addListener((observable, oldValue, newValue) -> {
            boolean disableResend = newValue == null || newValue.startsWith(RenderMessage.PATH_MSG);
            resendBtn.setDisable(disableResend);
            locateBtn.setDisable(newValue == null);

            // disable resendBtn when request is encrypted or oversize
            if (!disableResend) {
                RequestMessage requestMessage = requestCache.get(newValue);
                if (requestMessage == null || requestMessage.isOversize() || requestMessage.isEncrypted()) {
                    resendBtn.setDisable(true);
                }
            }
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
                System.out.println(11111);
                String targetIcon = newValue.intValue() == 0 ? "fas-broom": "fas-quidditch";
                clearBtn.setIconLiteral(targetIcon);
            }
        });

        // toggle record button
        recordBtn.selectedProperty().addListener((observable, oldValue, newValue) -> {
            appConfig.getSettings().setRecording(newValue);
            String toolTip = resourceBundle.getString(newValue ? "record-btn.disable.tooltip": "record-btn.enable.tooltip");
            recordBtn.getTooltip().setText(toolTip);
        });
        recordBtn.setSelected(true);

        // toggle handle ssl button
        sslBtn.selectedProperty().addListener((observable, oldValue, newValue) -> {
            appConfig.setHandleSSL(newValue);
            appConfig.updateSettingsAsync();
            String toolTip = resourceBundle.getString(newValue ? "ssl-btn.disable.tooltip": "ssl-btn.enable.tooltip");
            sslBtn.getTooltip().setText(toolTip);
        });
        sslBtn.setSelected(appConfig.getSettings().isHandleSsl());

        // init throttle button
        throttleBtn.selectedProperty().addListener((observable, oldValue, newValue) -> {
            appConfig.getSettings().setThrottle(newValue);
            appConfig.updateSettingsAsync();
            String toolTip = resourceBundle.getString(newValue ? "throttle-btn.disable.tooltip": "throttle-btn.enable.tooltip");
            throttleBtn.getTooltip().setText(toolTip);
        });
        throttleBtn.setSelected(appConfig.getSettings().isThrottle());

        // init sysProxyBtn
        appConfig.getObservableConfig().systemProxyStatusProperty().addListener((observable, oldValue, newValue) -> {
            sysProxyBtn.setDisable(newValue == SystemProxyStatus.DISABLED);
            sysProxyBtn.setSelected(newValue == SystemProxyStatus.ON);

            // SUSPENDED 状态视为 unselected, 只能流转为 selected
            if (newValue == SystemProxyStatus.SUSPENDED) {
                sysProxyBtn.setIconColor(COLOR_SUSPEND);
            }
        });

        // listen on display button label
        for (Node node : buttonBox.getChildren()) {
            if (node instanceof ButtonBase buttonBase) {
                if (buttonBase.getGraphic() instanceof UnderLabelWrapper underLabelWrapper) {
                    underLabelWrapper.getLabelVisibleProperty().bind(appConfig.getObservableConfig().showButtonLabelProperty());
                }
            }
        }

        bindUpdateBadge();
    }

    public void mockTreeItem() {
        requestMockService.mockRequest();
    }

    public void bindUpdateBadge() {
        Node updateBadge = checkUpdateBtn.getGraphic().lookup(".check-update-badge");
        if (updateBadge != null) {
            appConfig.getObservableConfig().hasNewVersionProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue == null) {
                    return;
                }
                updateBadge.setVisible(newValue);
            });
        }
    }

    public void displayProxySettingPage() {
        if (settingPage == null) {
            initSettingPage();
        }
        settingController.getSettingTabPane().getSelectionModel().select(settingController.getProxySettingTab());
        displaySettingPage();
        settingController.focusOnPortField();
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
        displaySettingPage(450);
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

    public void checkUpdate() {
        if (appUpdateController.getAlert() == null) {
            appUpdateController.initAlert(recordBtn.getScene().getWindow());
        }
        appConfig.getObservableConfig().setHasNewVersion(false);
        appUpdateController.checkUpdateAndShowAlert();
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
        requestViewController.resendRequest();
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

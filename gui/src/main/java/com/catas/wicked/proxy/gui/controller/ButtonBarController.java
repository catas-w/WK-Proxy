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
import com.catas.wicked.proxy.gui.componet.CustomMenuButton;
import com.catas.wicked.proxy.gui.componet.button.UnderLabelWrapper;
import com.catas.wicked.proxy.gui.componet.button.WKToggleNode;
import com.catas.wicked.proxy.gui.componet.button.WkButton;
import com.catas.wicked.proxy.message.MessageService;
import com.catas.wicked.proxy.service.RequestMockService;
import com.catas.wicked.proxy.service.LocalizationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.AnchorPane;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.ehcache.Cache;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

import static com.catas.wicked.common.constant.StyleConstant.COLOR_INACTIVE;
import static com.catas.wicked.common.constant.StyleConstant.COLOR_RED;
import static com.catas.wicked.common.constant.StyleConstant.COLOR_SUSPEND;

@Slf4j
@Singleton
public class ButtonBarController implements Initializable {

    private static final double LABELED_WIDTH = 60.0;
    private static final double COMPACT_WIDTH = 52.0;
    private static final boolean SHOW_SELECTION_INDICATOR = true;
    private static final PseudoClass COMPACT = PseudoClass.getPseudoClass("compact");
    private static final PseudoClass SELECTION_INDICATOR = PseudoClass.getPseudoClass("selection-indicator");
    private static final PseudoClass WARNING = PseudoClass.getPseudoClass("warning");
    private static final PseudoClass SUSPENDED = PseudoClass.getPseudoClass("suspended");

    @FXML
    private AnchorPane buttonBarRoot;
    @FXML
    private WKToggleNode recordBtn;
    @FXML
    private WKToggleNode sslBtn;
    @FXML
    private WkButton locateBtn;
    @FXML
    private WkButton resendBtn;
    @FXML
    private WKToggleNode throttleBtn;
    @FXML
    private WKToggleNode sysProxyBtn;
    @FXML
    private WkButton clearBtn;
    @FXML
    private CustomMenuButton settingsMenuBtn;
    @FXML
    private Label recordingBadge;
    @FXML
    private Label sslWarningBadge;
    @FXML
    private Label settingsUpdateBadge;
    @FXML
    private FontIcon menuUpdateBadge;
    @FXML
    private MenuItem checkUpdateBtn;
    @FXML private MenuItem settingBtn;
    @FXML private MenuItem aboutBtn;
    @FXML private MenuItem quitBtn;
    @FXML private Tooltip recordTooltip;
    @FXML private Tooltip sslTooltip;
    @FXML private Tooltip systemProxyTooltip;
    @FXML private Tooltip throttleTooltip;
    @FXML private Tooltip clearTooltip;
    @FXML private Tooltip resendTooltip;
    @FXML private Tooltip locateTooltip;
    @FXML private Tooltip settingsTooltip;

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
    private SettingsDialogCoordinator settingsDialogCoordinator;
    @Inject
    private AppUpdateController appUpdateController;
    @Inject
    private LocalizationService localization;

    @Setter
    private MessageService messageService;

    @SneakyThrows
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        buttonBarRoot.pseudoClassStateChanged(SELECTION_INDICATOR, SHOW_SELECTION_INDICATOR);
        localization.bind(recordBtn.labelTextProperty(), "record-btn.label");
        localization.bind(sslBtn.labelTextProperty(), "ssl-btn.label");
        localization.bind(sysProxyBtn.labelTextProperty(), "sys-proxy-btn.label");
        localization.bind(throttleBtn.labelTextProperty(), "throttle-btn.label");
        localization.bind(clearBtn.labelTextProperty(), "clear-btn.label");
        localization.bind(resendBtn.labelTextProperty(), "resend-btn.label");
        localization.bind(locateBtn.labelTextProperty(), "locate-btn.label");
        localization.bind(settingsMenuBtn.labelTextProperty(), "setting-btn.label");
        localization.bind(systemProxyTooltip.textProperty(), "sys-proxy-btn.tooltip");
        localization.bind(clearTooltip.textProperty(), "clear-btn.tooltip");
        localization.bind(resendTooltip.textProperty(), "resend-btn.tooltip");
        localization.bind(locateTooltip.textProperty(), "locate-btn.tooltip");
        localization.bind(settingsTooltip.textProperty(), "setting-btn.label");
        localization.bind(settingBtn.textProperty(), "setting-btn.label");
        localization.bind(checkUpdateBtn.textProperty(), "release-btn.label");
        localization.bind(aboutBtn.textProperty(), "about-btn.label");
        localization.bind(quitBtn.textProperty(), "quit-btn.label");
        bindButtonAccessibility();
        localization.languageProperty().addListener((observable, oldValue, newValue) ->
                refreshDynamicTooltips());
        // listen on current request
        appConfig.getObservableConfig().currentRequestIdProperty().addListener((observable, oldValue, newValue) -> {
            boolean disableResend = newValue == null || RenderMessage.isOverviewOnly(newValue);
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
        clearBtn.hoverProperty().addListener((observable, oldValue, hovered) ->
                clearBtn.setIconColor(hovered && !clearBtn.isDisabled() ? COLOR_RED : COLOR_INACTIVE));
        clearBtn.disabledProperty().addListener((observable, oldValue, disabled) -> {
            if (disabled) {
                clearBtn.setIconColor(COLOR_INACTIVE);
            }
        });

        // clear request event
        messageService.getRequestCntProperty().addListener((observable, oldValue, newValue) -> {
            // System.out.println("current count: " + newValue.intValue());
            if (newValue.intValue() < 0) {
                clearBtn.setDisable(true);
            } else {
                clearBtn.setDisable(false);
                String targetIcon = newValue.intValue() == 0 ? "fas-broom": "fas-quidditch";
                clearBtn.setIconLiteral(targetIcon);
            }
        });

        // toggle record button
        recordBtn.selectedProperty().addListener((observable, oldValue, newValue) -> {
            appConfig.getSettings().setRecording(newValue);
            refreshRecordingState();
            refreshDynamicTooltips();
        });
        recordBtn.setSelected(appConfig.getSettings().isRecording());
        refreshRecordingState();

        // toggle handle ssl button
        sslBtn.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (appConfig.getSettings().isHandleSsl() != newValue) {
                appConfig.setHandleSSL(newValue);
                appConfig.updateSettingsAsync();
            }
            refreshDynamicTooltips();
        });
        sslBtn.setSelected(appConfig.getSettings().isHandleSsl());
        appConfig.getObservableConfig().handlingSSLProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && sslBtn.isSelected() != newValue) {
                runOnFxThread(() -> sslBtn.setSelected(newValue));
            }
            refreshSslWarningState();
        });
        appConfig.getObservableConfig().certInstalledStatusProperty().addListener((observable, oldValue, newValue) ->
                refreshSslWarningState());
        refreshSslWarningState();

        // init throttle button
        throttleBtn.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (appConfig.getSettings().isThrottle() != newValue) {
                appConfig.getSettings().setThrottle(newValue);
                appConfig.updateSettingsAsync();
            }
            refreshDynamicTooltips();
        });
        throttleBtn.setSelected(appConfig.getSettings().isThrottle());
        appConfig.getObservableConfig().throttlingProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && throttleBtn.isSelected() != newValue) {
                runOnFxThread(() -> throttleBtn.setSelected(newValue));
            }
        });

        // init sysProxyBtn
        appConfig.getObservableConfig().systemProxyStatusProperty().addListener((observable, oldValue, newValue) -> {
            refreshSystemProxyState(newValue);
        });
        refreshSystemProxyState(appConfig.getObservableConfig().getSystemProxyStatus());

        // listen on display button label
        appConfig.getObservableConfig().showButtonLabelProperty().addListener((observable, oldValue, newValue) ->
                applyButtonBarMode(newValue));
        applyButtonBarMode(appConfig.getObservableConfig().isShowButtonLabel());

        bindUpdateBadge();
        refreshDynamicTooltips();
    }

    private void refreshDynamicTooltips() {
        recordTooltip.setText(localization.getMessage(recordBtn.isSelected()
                ? "record-btn.disable.tooltip" : "record-btn.enable.tooltip"));
        sslTooltip.setText(localization.getMessage(sslBtn.isSelected()
                ? "ssl-btn.disable.tooltip" : "ssl-btn.enable.tooltip"));
        throttleTooltip.setText(localization.getMessage(throttleBtn.isSelected()
                ? "throttle-btn.disable.tooltip" : "throttle-btn.enable.tooltip"));
    }

    public void mockTreeItem() {
        requestMockService.mockRequest();
    }

    public void bindUpdateBadge() {
        appConfig.getObservableConfig().hasNewVersionProperty().addListener((observable, oldValue, newValue) ->
                refreshUpdateBadge(Boolean.TRUE.equals(newValue)));
        refreshUpdateBadge(appConfig.getObservableConfig().isHasNewVersion());
    }

    private void applyButtonBarMode(boolean showLabels) {
        runOnFxThread(() -> {
            double width = showLabels ? LABELED_WIDTH : COMPACT_WIDTH;
            buttonBarRoot.setMinWidth(width);
            buttonBarRoot.setPrefWidth(width);
            buttonBarRoot.setMaxWidth(width);
            buttonBarRoot.pseudoClassStateChanged(COMPACT, !showLabels);
            buttonControls().forEach(button -> {
                if (button.getGraphic() instanceof UnderLabelWrapper wrapper) {
                    wrapper.setLabelVisible(showLabels);
                }
            });
        });
    }

    private List<ButtonBase> buttonControls() {
        return List.of(recordBtn, sslBtn, sysProxyBtn, throttleBtn,
                clearBtn, resendBtn, locateBtn, settingsMenuBtn);
    }

    private void bindButtonAccessibility() {
        buttonControls().forEach(button -> {
            if (button.getGraphic() instanceof UnderLabelWrapper wrapper) {
                button.accessibleTextProperty().bind(wrapper.labelTextPropertyProperty());
            }
        });
    }

    private void refreshRecordingState() {
        runOnFxThread(() -> setBadgeVisible(recordingBadge, recordBtn.isSelected()));
    }

    private void refreshSslWarningState() {
        boolean warning = appConfig.getObservableConfig().isHandlingSSL()
                && !appConfig.getObservableConfig().isCertInstalledStatus();
        runOnFxThread(() -> {
            setBadgeVisible(sslWarningBadge, warning);
            sslBtn.pseudoClassStateChanged(WARNING, warning);
        });
    }

    private void refreshSystemProxyState(SystemProxyStatus status) {
        if (status == null) {
            return;
        }
        runOnFxThread(() -> {
            boolean suspended = status == SystemProxyStatus.SUSPENDED;
            sysProxyBtn.setDisable(status == SystemProxyStatus.DISABLED);
            sysProxyBtn.setSelected(status == SystemProxyStatus.ON);
            sysProxyBtn.pseudoClassStateChanged(SUSPENDED, suspended);
            if (suspended) {
                sysProxyBtn.setIconColor(COLOR_SUSPEND);
            } else if (status != SystemProxyStatus.ON) {
                sysProxyBtn.setIconColor(COLOR_INACTIVE);
            }
        });
    }

    private void refreshUpdateBadge(boolean visible) {
        runOnFxThread(() -> {
            setBadgeVisible(settingsUpdateBadge, visible);
            menuUpdateBadge.setManaged(visible);
            menuUpdateBadge.setVisible(visible);
        });
    }

    private void setBadgeVisible(Node badge, boolean visible) {
        badge.setManaged(visible);
        badge.setVisible(visible);
    }

    private void runOnFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    public void displayProxySettingPage() {
        settingsDialogCoordinator.open(SettingsTab.PROXY, recordBtn.getScene().getWindow());
    }

    public void displayAboutPage() {
        settingsDialogCoordinator.open(SettingsTab.ABOUT, recordBtn.getScene().getWindow());
    }

    public void displaySSlSettingPage() {
        settingsDialogCoordinator.open(SettingsTab.SSL, recordBtn.getScene().getWindow());
    }

    public void displaySettingPage() {
        settingsDialogCoordinator.open(SettingsTab.GENERAL, recordBtn.getScene().getWindow());
    }

    public void checkUpdate() {
        if (appUpdateController.getAlert() == null) {
            appUpdateController.initAlert(recordBtn.getScene().getWindow());
        }
        appConfig.getObservableConfig().setHasNewVersion(false);
        appUpdateController.checkUpdateAndShowAlert();
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

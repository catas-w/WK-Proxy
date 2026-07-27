package com.catas.wicked.proxy.gui.controller;

import app.supernaut.fx.fxml.FxmlLoaderFactory;
import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.provider.ResourceMessageProvider;
import com.catas.wicked.common.util.AlertUtils;
import com.catas.wicked.proxy.gui.controller.settings.ProxySettingsPageController;
import com.catas.wicked.proxy.gui.controller.settings.SettingsPageController;
import com.catas.wicked.proxy.service.settings.SettingsApplyFailureType;
import com.catas.wicked.proxy.service.settings.SettingsApplyResult;
import com.catas.wicked.proxy.service.settings.SettingsCommitService;
import com.catas.wicked.proxy.service.settings.SettingsDraft;
import com.jfoenix.controls.JFXButton;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import lombok.extern.slf4j.Slf4j;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

@Slf4j
@Singleton
public class SettingController implements Initializable {

    @FXML private BorderPane root;
    @FXML private TabPane settingTabPane;
    @FXML private Tab generalSettingTab;
    @FXML private Tab proxySettingTab;
    @FXML private Tab sslSettingTab;
    @FXML private Tab externalSettingTab;
    @FXML private Tab infoSettingTab;
    @FXML private JFXButton applyButton;
    @FXML private JFXButton cancelButton;
    @FXML private Label applyStatusLabel;

    @Inject private FxmlLoaderFactory loaderFactory;
    @Inject private ApplicationConfig applicationConfig;
    @Inject private SettingsCommitService commitService;
    @Inject private ResourceMessageProvider messages;

    private final Map<SettingsTab, Tab> tabs = new EnumMap<>(SettingsTab.class);
    private final Map<Tab, String> pageResources = new LinkedHashMap<>();
    private final Map<Tab, SettingsPageController> loadedPages = new LinkedHashMap<>();
    private SettingsDraft draft;
    private Runnable closeAction = () -> {};
    private boolean applying;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        tabs.put(SettingsTab.GENERAL, generalSettingTab);
        tabs.put(SettingsTab.PROXY, proxySettingTab);
        tabs.put(SettingsTab.SSL, sslSettingTab);
        tabs.put(SettingsTab.EXTERNAL_PROXY, externalSettingTab);
        tabs.put(SettingsTab.ABOUT, infoSettingTab);

        pageResources.put(generalSettingTab, "/fxml/setting-page/general.fxml");
        pageResources.put(proxySettingTab, "/fxml/setting-page/proxy.fxml");
        pageResources.put(sslSettingTab, "/fxml/setting-page/ssl.fxml");
        pageResources.put(externalSettingTab, "/fxml/setting-page/external-proxy.fxml");
        pageResources.put(infoSettingTab, "/fxml/setting-page/about.fxml");

        configTabStyle(generalSettingTab, "fas-sliders-h");
        configTabStyle(proxySettingTab, "fas-hat-cowboy");
        configTabStyle(sslSettingTab, "fas-key");
        configTabStyle(externalSettingTab, "fas-monument");
        configTabStyle(infoSettingTab, "fas-info-circle");

        settingTabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldTab, newTab) -> {
            if (newTab != null && draft != null) {
                SettingsPageController page = ensurePageLoaded(newTab);
                if (page != null) {
                    page.onShown();
                }
            }
        });
        applyButton.setOnAction(event -> apply());
        cancelButton.setOnAction(event -> {
            if (!applying) {
                closeAction.run();
            }
        });
    }

    public void beginSession(SettingsTab initialTab, Runnable closeAction) {
        this.closeAction = closeAction == null ? () -> {} : closeAction;
        draft = SettingsDraft.from(applicationConfig.snapshotSettings());
        applyStatusLabel.setText("");
        setApplying(false);

        SettingsPageController general = ensurePageLoaded(generalSettingTab);
        if (general != null) {
            general.load(draft, this::onDraftChanged);
        }
        for (Map.Entry<Tab, SettingsPageController> entry : loadedPages.entrySet()) {
            if (entry.getKey() != generalSettingTab) {
                entry.getValue().load(draft, this::onDraftChanged);
            }
        }
        Tab target = tabs.getOrDefault(initialTab, generalSettingTab);
        settingTabPane.getSelectionModel().select(target);
        SettingsPageController selected = ensurePageLoaded(target);
        if (selected != null) {
            selected.onShown();
        }
        updateDirtyState();

        if (initialTab == SettingsTab.PROXY && selected instanceof ProxySettingsPageController proxyPage) {
            Platform.runLater(proxyPage::focusPort);
        }
    }

    public boolean isDirty() {
        return draft != null && draft.isDirty();
    }

    public boolean isApplying() {
        return applying;
    }

    private SettingsPageController ensurePageLoaded(Tab tab) {
        SettingsPageController existing = loadedPages.get(tab);
        if (existing != null) {
            return existing;
        }
        String resource = pageResources.get(tab);
        if (resource == null) {
            return null;
        }
        try {
            URL location = getClass().getResource(resource);
            FXMLLoader loader = loaderFactory.get(location);
            Locale locale = applicationConfig.getSettings().getLanguage().getLocale();
            loader.setResources(ResourceBundle.getBundle("lang.messages", locale));
            Parent content = loader.load();
            SettingsPageController controller = loader.getController();
            tab.setContent(content);
            loadedPages.put(tab, controller);
            if (draft != null) {
                controller.load(draft, this::onDraftChanged);
            }
            return controller;
        } catch (Exception error) {
            log.error("Unable to load settings page: {}", resource, error);
            applyStatusLabel.setText(error.getMessage());
            return null;
        }
    }

    private void apply() {
        if (applying || draft == null) {
            return;
        }
        for (Map.Entry<Tab, SettingsPageController> entry : loadedPages.entrySet()) {
            if (!entry.getValue().validate()) {
                settingTabPane.getSelectionModel().select(entry.getKey());
                entry.getValue().focusFirstError();
                AlertUtils.alertWarning(messages.getMessage("alert.type.warning"),
                        messages.getMessage("alert.msg.illegal-settings"));
                return;
            }
        }

        setApplying(true);
        applyStatusLabel.setText(messages.getMessage("settings.applying"));
        commitService.apply(draft).whenComplete((result, error) ->
                Platform.runLater(() -> finishApply(result, error)));
    }

    private void finishApply(SettingsApplyResult result, Throwable error) {
        setApplying(false);
        if (error != null || result == null || !result.success()) {
            if (error == null && result != null
                    && result.failureType() == SettingsApplyFailureType.PORT_UNAVAILABLE) {
                showPortUnavailable(result.rejectedPort());
                updateDirtyState();
                return;
            }
            String message = error != null ? error.getMessage()
                    : result == null ? messages.getMessage("alert.msg.settings-update-error")
                    : result.errorMessage();
            applyStatusLabel.setText(message);
            AlertUtils.alertWarning(messages.getMessage("alert.type.warning"), message);
            updateDirtyState();
            return;
        }

        draft.markApplied();
        syncObservableSettings();
        applyStatusLabel.setText(messages.getMessage("settings.apply-success"));
        updateDirtyState();
    }

    private void showPortUnavailable(Integer port) {
        int rejectedPort = port == null ? draft.value().getPort() : port;
        Tab proxyTab = tabs.get(SettingsTab.PROXY);
        SettingsPageController page = ensurePageLoaded(proxyTab);
        settingTabPane.getSelectionModel().select(proxyTab);
        if (page instanceof ProxySettingsPageController proxyPage) {
            proxyPage.showPortUnavailable(rejectedPort);
            Platform.runLater(proxyPage::focusPort);
        }
        String message = String.format(
                messages.getMessage("validation.port-unavailable"), rejectedPort);
        applyStatusLabel.setText(message);
        AlertUtils.alertWarning(messages.getMessage("alert.type.warning"), message);
    }

    private void syncObservableSettings() {
        var settings = applicationConfig.getSettings();
        var observable = applicationConfig.getObservableConfig();
        observable.setHandlingSSL(settings.isHandleSsl());
        observable.setShowButtonLabel(settings.isShowButtonLabel());
        observable.setShowApplicationRequestCount(settings.isShowApplicationRequestCount());
        observable.setThrottling(settings.isThrottle());
    }

    private void onDraftChanged() {
        applyStatusLabel.setText("");
        updateDirtyState();
    }

    private void updateDirtyState() {
        applyButton.setDisable(applying || !isDirty());
    }

    private void setApplying(boolean applying) {
        this.applying = applying;
        settingTabPane.setDisable(applying);
        cancelButton.setDisable(applying);
        updateDirtyState();
    }

    private void configTabStyle(Tab tab, String iconCode) {
        FontIcon icon = new FontIcon(iconCode);
        Label label = new Label(tab.getText());
        BorderPane graphic = new BorderPane();
        graphic.setPrefWidth(90);
        graphic.setCenter(icon);
        graphic.setBottom(label);
        graphic.getStyleClass().add("setting-icon-pane");
        tab.setText(null);
        tab.setGraphic(graphic);
    }
}

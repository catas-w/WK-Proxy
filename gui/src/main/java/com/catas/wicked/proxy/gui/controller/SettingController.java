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
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import lombok.extern.slf4j.Slf4j;

import java.net.URL;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

@Slf4j
@Singleton
public class SettingController implements Initializable {

    @FXML private BorderPane root;
    @FXML private ToggleGroup settingsNavigationGroup;
    @FXML private ToggleButton generalNavigationButton;
    @FXML private ToggleButton proxyNavigationButton;
    @FXML private ToggleButton sslNavigationButton;
    @FXML private ToggleButton aboutNavigationButton;
    @FXML private Label pageTitleLabel;
    @FXML private StackPane pageHost;
    @FXML private JFXButton applyButton;
    @FXML private JFXButton cancelButton;
    @FXML private Label applyStatusLabel;

    @Inject private FxmlLoaderFactory loaderFactory;
    @Inject private ApplicationConfig applicationConfig;
    @Inject private SettingsCommitService commitService;
    @Inject private ResourceMessageProvider messages;

    private final Map<SettingsTab, ToggleButton> navigationButtons = new EnumMap<>(SettingsTab.class);
    private final Map<SettingsTab, String> pageResources = new EnumMap<>(SettingsTab.class);
    private final Map<SettingsTab, SettingsPageController> loadedPages = new EnumMap<>(SettingsTab.class);
    private SettingsDraft draft;
    private Runnable closeAction = () -> {};
    private SettingsTab selectedTab = SettingsTab.GENERAL;
    private boolean applying;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        navigationButtons.put(SettingsTab.GENERAL, generalNavigationButton);
        navigationButtons.put(SettingsTab.PROXY, proxyNavigationButton);
        navigationButtons.put(SettingsTab.SSL, sslNavigationButton);
        navigationButtons.put(SettingsTab.ABOUT, aboutNavigationButton);

        pageResources.put(SettingsTab.GENERAL, "/fxml/setting-page/general.fxml");
        pageResources.put(SettingsTab.PROXY, "/fxml/setting-page/proxy.fxml");
        pageResources.put(SettingsTab.SSL, "/fxml/setting-page/ssl.fxml");
        pageResources.put(SettingsTab.ABOUT, "/fxml/setting-page/about.fxml");

        navigationButtons.forEach((tab, button) -> button.setOnAction(event -> selectPage(tab)));
        settingsNavigationGroup.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
            if (newToggle == null) {
                navigationButtons.get(selectedTab).setSelected(true);
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

        SettingsPageController general = ensurePageLoaded(SettingsTab.GENERAL);
        if (general != null) {
            general.load(draft, this::onDraftChanged);
        }
        loadedPages.forEach((tab, page) -> {
            if (tab != SettingsTab.GENERAL) {
                page.load(draft, this::onDraftChanged);
            }
        });

        SettingsTab target = navigationButtons.containsKey(initialTab) ? initialTab : SettingsTab.GENERAL;
        selectPage(target);
        updateDirtyState();
        if (target == SettingsTab.PROXY) {
            SettingsPageController page = loadedPages.get(SettingsTab.PROXY);
            if (page instanceof ProxySettingsPageController proxyPage) {
                Platform.runLater(proxyPage::focusPort);
            }
        }
    }

    public boolean isDirty() {
        return draft != null && draft.isDirty();
    }

    public boolean isApplying() {
        return applying;
    }

    private void selectPage(SettingsTab tab) {
        selectedTab = tab;
        ToggleButton button = navigationButtons.get(tab);
        if (button != null && !button.isSelected()) {
            button.setSelected(true);
        }
        pageTitleLabel.setText(button == null ? "" : button.getText());
        SettingsPageController page = ensurePageLoaded(tab);
        if (page != null) {
            Parent content = (Parent) pageHost.getProperties().get(tab);
            if (content != null) {
                pageHost.getChildren().setAll(content);
            }
            page.onShown();
        }
    }

    private SettingsPageController ensurePageLoaded(SettingsTab tab) {
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
            loadedPages.put(tab, controller);
            pageHost.getProperties().put(tab, content);
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
        for (Map.Entry<SettingsTab, SettingsPageController> entry : loadedPages.entrySet()) {
            if (!entry.getValue().validate()) {
                selectPage(entry.getKey());
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
        selectPage(SettingsTab.PROXY);
        SettingsPageController page = loadedPages.get(SettingsTab.PROXY);
        if (page instanceof ProxySettingsPageController proxyPage) {
            proxyPage.showPortUnavailable(rejectedPort);
            Platform.runLater(proxyPage::focusPort);
        }
        applyStatusLabel.setText("");
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
        root.getCenter().setDisable(applying);
        cancelButton.setDisable(applying);
        updateDirtyState();
    }
}

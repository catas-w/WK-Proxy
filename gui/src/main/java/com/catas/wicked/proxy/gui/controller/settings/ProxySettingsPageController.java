package com.catas.wicked.proxy.gui.controller.settings;

import com.catas.wicked.common.config.Settings;
import com.catas.wicked.common.constant.ThrottlePreset;
import com.catas.wicked.proxy.gui.componet.EnumLabel;
import com.catas.wicked.proxy.gui.componet.validator.PortValidator;
import com.catas.wicked.proxy.service.LocalizationService;
import com.catas.wicked.proxy.service.settings.SettingsCommitService;
import com.catas.wicked.proxy.service.settings.SettingsDraft;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.controls.JFXToggleButton;
import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Inject;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.Tooltip;

import java.net.URL;
import java.util.ResourceBundle;

@Prototype
public class ProxySettingsPageController implements SettingsPageController, Initializable {

    private static final PseudoClass ERROR_PSEUDO_CLASS = PseudoClass.getPseudoClass("error");

    @Inject private SettingsCommitService commitService;
    @Inject private LocalizationService localization;

    @FXML private Label localProxySectionLabel;
    @FXML private Label listenPortLabel;
    @FXML private JFXTextField portField;
    @FXML private Label portUnavailableLabel;
    @FXML private Label systemProxySectionLabel;
    @FXML private Label enableSystemProxyLabel;
    @FXML private Label systemProxyBypassLabel;
    @FXML private Tooltip systemProxyBypassTooltip;
    @FXML private Label throttleSectionLabel;
    @FXML private Label throttleLabel;
    @FXML private Label throttlePresetLabel;
    @FXML private JFXToggleButton throttleBtn;
    @FXML private JFXComboBox<EnumLabel<ThrottlePreset>> throttleComboBox;
    @FXML private JFXCheckBox sysProxyOnLaunchBtn;
    @FXML private TextArea sysProxyExcludeArea;
    @FXML private ExternalProxySettingsPageController upstreamProxySectionController;

    private SettingsDraft draft;
    private Runnable changeListener = () -> {};
    private boolean loading;
    private PortValidator portValidator;
    private String portUnavailableMessage;
    private long portCheckSequence;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        localization.bind(localProxySectionLabel.textProperty(), "local-proxy-sep.label");
        localization.bind(listenPortLabel.textProperty(), "listen-port.label");
        localization.bind(systemProxySectionLabel.textProperty(), "sys-proxy-sep.label");
        localization.bind(enableSystemProxyLabel.textProperty(), "enable-sys-proxy.label");
        localization.bind(systemProxyBypassLabel.textProperty(), "sys-proxy-bypass.label");
        localization.bind(systemProxyBypassTooltip.textProperty(), "host-path.tooltip");
        localization.bind(throttleSectionLabel.textProperty(), "network-throttle-sep.label");
        localization.bind(throttleLabel.textProperty(), "throttle.label");
        localization.bind(throttlePresetLabel.textProperty(), "throttle-preset.label");
        portValidator = new PortValidator(resources.getString("validation.port-range"));
        portUnavailableMessage = resources.getString("validation.port-unavailable");
        portField.getValidators().add(portValidator);
        portField.setTextFormatter(new TextFormatter<>(change ->
                PortValidator.isAllowedInput(change.getControlNewText()) ? change : null));
        for (ThrottlePreset preset : ThrottlePreset.values()) {
            throttleComboBox.getItems().add(new EnumLabel<>(preset, preset::getDesc));
        }
        portField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!loading) {
                clearPortUnavailable();
                portCheckSequence++;
            }
            Integer port = PortValidator.parse(newValue);
            if (!loading && draft != null && port != null) {
                draft.value().setPort(port);
                changed();
            }
        });
        portField.focusedProperty().addListener((observable, wasFocused, focused) -> {
            if (wasFocused && !focused) {
                Integer port = PortValidator.parse(portField.getText());
                if (port == null) {
                    clearPortUnavailable();
                    portField.validate();
                } else {
                    preflightPort(port);
                }
            }
        });
        throttleBtn.selectedProperty().addListener((observable, oldValue, newValue) -> {
            throttleComboBox.setDisable(!newValue);
            if (!loading && draft != null) {
                draft.value().setThrottle(newValue);
                changed();
            }
        });
        throttleComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (!loading && draft != null && newValue != null) {
                draft.value().setThrottlePreset(newValue.getEnum());
                changed();
            }
        });
        sysProxyOnLaunchBtn.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (!loading && draft != null) {
                draft.value().setEnableSysProxyOnLaunch(newValue);
                changed();
            }
        });
        sysProxyExcludeArea.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!loading && draft != null) {
                draft.value().setSysProxyBypassList(SettingsFormSupport.parseList(newValue));
                changed();
            }
        });
    }

    @Override
    public void load(SettingsDraft draft, Runnable changeListener) {
        portCheckSequence++;
        clearPortUnavailable();
        this.draft = draft;
        this.changeListener = changeListener == null ? () -> {} : changeListener;
        Settings settings = draft.value();
        loading = true;
        try {
            portField.setText(String.valueOf(settings.getPort()));
            throttleBtn.setSelected(settings.isThrottle());
            throttleComboBox.setDisable(!settings.isThrottle());
            ThrottlePreset selected = settings.getThrottlePreset();
            if (selected == null && !throttleComboBox.getItems().isEmpty()) {
                throttleComboBox.getSelectionModel().selectFirst();
            } else {
                throttleComboBox.getItems().stream()
                        .filter(item -> item.getEnum() == selected)
                        .findFirst().ifPresent(throttleComboBox.getSelectionModel()::select);
            }
            sysProxyOnLaunchBtn.setSelected(settings.isEnableSysProxyOnLaunch());
            sysProxyExcludeArea.setText(SettingsFormSupport.formatList(settings.getSysProxyBypassList()));
        } finally {
            loading = false;
        }
        upstreamProxySectionController.load(draft, changeListener);
    }

    @Override
    public boolean validate() {
        boolean valid = portField.validate();
        if (valid && portUnavailableLabel.isVisible()) {
            portField.pseudoClassStateChanged(ERROR_PSEUDO_CLASS, true);
        }
        return valid && upstreamProxySectionController.validate();
    }

    @Override
    public void focusFirstError() {
        if (!portField.validate()) {
            portField.requestFocus();
            portField.selectAll();
        } else {
            upstreamProxySectionController.focusFirstError();
        }
    }

    @Override
    public void dispose() {
        upstreamProxySectionController.dispose();
    }

    @Override
    public void onLocaleChanged() {
        portValidator.setRangeMessage(localization.getMessage("validation.port-range"));
        portUnavailableMessage = localization.getMessage("validation.port-unavailable");
        if (portUnavailableLabel.isVisible()) {
            Integer port = PortValidator.parse(portField.getText());
            if (port != null) {
                showPortUnavailable(port);
            }
        }
        upstreamProxySectionController.onLocaleChanged();
    }

    public void focusPort() {
        portField.requestFocus();
        portField.selectAll();
    }

    public void showPortUnavailable(int port) {
        portUnavailableLabel.setText(String.format(portUnavailableMessage, port));
        portUnavailableLabel.setManaged(true);
        portUnavailableLabel.setVisible(true);
        portField.pseudoClassStateChanged(ERROR_PSEUDO_CLASS, true);
    }

    public void clearPortUnavailable() {
        if (portUnavailableLabel != null) {
            portUnavailableLabel.setManaged(false);
            portUnavailableLabel.setVisible(false);
            portUnavailableLabel.setText("");
        }
        if (portField != null) {
            portField.resetValidation();
            portField.pseudoClassStateChanged(ERROR_PSEUDO_CLASS, false);
        }
    }

    private void preflightPort(Integer port) {
        if (draft == null) {
            return;
        }
        if (port.equals(draft.baseline().getPort())) {
            clearPortUnavailable();
            return;
        }

        long sequence = ++portCheckSequence;
        commitService.checkPortAvailability(port).whenComplete((available, error) ->
                Platform.runLater(() -> {
                    if (sequence != portCheckSequence
                            || draft == null
                            || !port.equals(PortValidator.parse(portField.getText()))) {
                        return;
                    }
                    if (error == null && Boolean.FALSE.equals(available)) {
                        showPortUnavailable(port);
                    } else if (error == null) {
                        clearPortUnavailable();
                    }
                }));
    }

    private void changed() {
        changeListener.run();
    }
}

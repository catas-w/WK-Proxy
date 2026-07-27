package com.catas.wicked.proxy.gui.controller.settings;

import com.catas.wicked.common.config.Settings;
import com.catas.wicked.common.constant.ThrottlePreset;
import com.catas.wicked.proxy.gui.componet.EnumLabel;
import com.catas.wicked.proxy.gui.componet.validator.PortValidator;
import com.catas.wicked.proxy.service.settings.SettingsCommitService;
import com.catas.wicked.proxy.service.settings.SettingsDraft;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.controls.JFXToggleButton;
import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Inject;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TextArea;

import java.net.URL;
import java.util.ResourceBundle;

@Prototype
public class ProxySettingsPageController implements SettingsPageController, Initializable {

    @Inject private SettingsCommitService commitService;

    @FXML private JFXTextField portField;
    @FXML private JFXToggleButton throttleBtn;
    @FXML private JFXComboBox<EnumLabel<ThrottlePreset>> throttleComboBox;
    @FXML private JFXCheckBox sysProxyOnLaunchBtn;
    @FXML private TextArea sysProxyExcludeArea;

    private SettingsDraft draft;
    private Runnable changeListener = () -> {};
    private boolean loading;
    private PortValidator portValidator;
    private long portCheckSequence;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        portValidator = new PortValidator(
                resources.getString("validation.port-range"),
                resources.getString("validation.port-unavailable"));
        portField.getValidators().add(portValidator);
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
                preflightPort();
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
    }

    @Override
    public boolean validate() {
        return portField.validate();
    }

    @Override
    public void focusFirstError() {
        if (!portField.validate()) {
            portField.requestFocus();
            portField.selectAll();
        }
    }

    public void focusPort() {
        portField.requestFocus();
        portField.selectAll();
    }

    public void showPortUnavailable(int port) {
        portValidator.setUnavailablePort(port);
        portField.validate();
    }

    public void clearPortUnavailable() {
        if (portValidator != null) {
            portValidator.clearUnavailablePort();
            portField.resetValidation();
        }
    }

    private void preflightPort() {
        if (draft == null) {
            return;
        }
        Integer port = PortValidator.parse(portField.getText());
        if (port == null || port.equals(draft.baseline().getPort())) {
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
                    }
                }));
    }

    private void changed() {
        changeListener.run();
    }
}

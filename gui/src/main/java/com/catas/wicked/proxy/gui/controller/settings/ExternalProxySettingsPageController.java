package com.catas.wicked.proxy.gui.controller.settings;

import com.catas.wicked.common.config.ExternalProxyConfig;
import com.catas.wicked.common.constant.ProxyProtocol;
import com.catas.wicked.proxy.gui.componet.EnumLabel;
import com.catas.wicked.proxy.service.settings.SettingsDraft;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.controls.JFXToggleButton;
import io.micronaut.context.annotation.Prototype;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.layout.GridPane;

import java.net.URL;
import java.util.ResourceBundle;

@Prototype
public class ExternalProxySettingsPageController implements SettingsPageController, Initializable {

    @FXML private GridPane exProxyDetails;
    @FXML private GridPane exProxyAuthDetails;
    @FXML private JFXToggleButton exProxyBtn;
    @FXML private JFXComboBox<EnumLabel<ProxyProtocol>> proxyComboBox;
    @FXML private JFXTextField exProxyHost;
    @FXML private JFXTextField exProxyPort;
    @FXML private JFXToggleButton exProxyAuth;
    @FXML private JFXTextField exUsername;
    @FXML private JFXTextField exPassword;

    private SettingsDraft draft;
    private Runnable changeListener = () -> {};
    private boolean loading;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        SettingsFormSupport.require(exProxyHost, resources.getString("validation.required"));
        SettingsFormSupport.require(exProxyPort, resources.getString("validation.required"));
        SettingsFormSupport.positiveInteger(exProxyPort, resources.getString("validation.positive-integer"));
        for (ProxyProtocol protocol : ProxyProtocol.values()) {
            if (protocol.isActive()) {
                proxyComboBox.getItems().add(new EnumLabel<>(protocol, protocol::getName));
            }
        }
        exProxyBtn.selectedProperty().addListener((observable, oldValue, newValue) -> {
            updateEnabledState(newValue);
            if (!loading && draft != null) {
                draft.value().setEnableExProxy(newValue);
                changed();
            }
        });
        proxyComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (!loading && draft != null && newValue != null) {
                draft.value().getExternalProxy().setProtocol(newValue.getEnum());
                changed();
            }
        });
        exProxyAuth.selectedProperty().addListener((observable, oldValue, newValue) -> {
            updateAuthState(newValue);
            if (!loading && draft != null) {
                draft.value().getExternalProxy().setProxyAuth(newValue);
                changed();
            }
        });
        exProxyHost.textProperty().addListener((o, oldValue, newValue) -> update(config -> config.setHost(newValue)));
        exProxyPort.textProperty().addListener((o, oldValue, newValue) -> {
            if (newValue.matches("[1-9][0-9]*")) {
                update(config -> config.setPort(Integer.parseInt(newValue)));
            }
        });
        exUsername.textProperty().addListener((o, oldValue, newValue) -> update(config -> config.setUsername(newValue)));
        exPassword.textProperty().addListener((o, oldValue, newValue) -> update(config -> config.setPassword(newValue)));
    }

    @Override
    public void load(SettingsDraft draft, Runnable changeListener) {
        this.draft = draft;
        this.changeListener = changeListener == null ? () -> {} : changeListener;
        ExternalProxyConfig config = draft.value().getExternalProxy();
        loading = true;
        try {
            exProxyBtn.setSelected(draft.value().isEnableExProxy());
            proxyComboBox.getItems().stream()
                    .filter(item -> item.getEnum() == config.getProtocol())
                    .findFirst().ifPresentOrElse(proxyComboBox.getSelectionModel()::select,
                            proxyComboBox.getSelectionModel()::selectFirst);
            exProxyHost.setText(config.getHost());
            exProxyPort.setText(String.valueOf(config.getPort()));
            exProxyAuth.setSelected(config.isProxyAuth());
            exUsername.setText(config.getUsername());
            exPassword.setText(config.getPassword());
            updateEnabledState(exProxyBtn.isSelected());
            updateAuthState(exProxyAuth.isSelected());
        } finally {
            loading = false;
        }
    }

    @Override
    public boolean validate() {
        return !exProxyBtn.isSelected() || (exProxyHost.validate() && exProxyPort.validate());
    }

    @Override
    public void focusFirstError() {
        if (exProxyBtn.isSelected() && !exProxyHost.validate()) {
            exProxyHost.requestFocus();
        } else if (exProxyBtn.isSelected() && !exProxyPort.validate()) {
            exProxyPort.requestFocus();
        }
    }

    private void update(java.util.function.Consumer<ExternalProxyConfig> update) {
        if (!loading && draft != null) {
            update.accept(draft.value().getExternalProxy());
            changed();
        }
    }

    private void updateEnabledState(boolean enabled) {
        exProxyDetails.setManaged(enabled);
        exProxyDetails.setVisible(enabled);
        exProxyDetails.setDisable(!enabled);
        updateAuthState(enabled && exProxyAuth.isSelected());
    }

    private void updateAuthState(boolean visible) {
        exProxyAuthDetails.setManaged(visible);
        exProxyAuthDetails.setVisible(visible);
    }

    private void changed() {
        changeListener.run();
    }
}

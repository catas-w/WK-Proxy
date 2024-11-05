package com.catas.wicked.proxy.service.settings;

import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.config.ExternalProxyConfig;
import com.catas.wicked.common.config.Settings;
import com.catas.wicked.common.constant.ProxyProtocol;
import com.catas.wicked.common.pipeline.MessageQueue;
import com.catas.wicked.proxy.gui.componet.ProxyTypeLabel;
import com.jfoenix.controls.JFXComboBox;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.scene.layout.GridPane;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@Singleton
public class ExternalProxySettingService extends AbstractSettingService{

    @Inject
    public void setApplicationConfig(ApplicationConfig applicationConfig) {
        this.applicationConfig = applicationConfig;
    }

    @Inject
    public void setMessageQueue(MessageQueue messageQueue) {
        this.messageQueue = messageQueue;
    }

    @Override
    public void init() {
        Settings settings = applicationConfig.getSettings();

        // enable btn
        settingController.getExProxyBtn().setSelected(true);
        settingController.getExProxyBtn().selectedProperty().addListener(((observable, oldValue, newValue) -> {
            // set disable
            settingController.getExProxyGridPane().getChildren().forEach(node -> {
                Integer rowIndex = GridPane.getRowIndex(node);
                // 当未指定时设置为 0
                if (rowIndex == null) {
                    rowIndex = 0;
                }
                if (rowIndex > 1) {
                    node.setDisable(!newValue);
                }
            });

            // update settings
            settings.setEnableExProxy(newValue);
            refreshAppSettings();
        }));

        // input fields
        addPositiveNumValidator(settingController.getExProxyPort(), "Illegal port!");
        addUnDisableRequiredValidator(settingController.getExProxyPort(), "Illegal port!");
        addUnDisableRequiredValidator(settingController.getExProxyHost(), "Cannot be empty!");
        addUnFocusedEvent(settingController.getExProxyHost(), textField -> {
            if (!textField.validate()) {
                log.warn("external proxy host invalid!");
                return;
            }
            String newHost = textField.getText();
            String oldHost = settings.getExternalProxy().getHost();
            if (!StringUtils.equals(newHost, oldHost)) {
                settings.getExternalProxy().setHost(newHost);
                refreshAppSettings();
            }
        });
        addUnFocusedEvent(settingController.getExProxyPort(), textField -> {
            if (!textField.validate()) {
                log.warn("external proxy port invalid!");
                return;
            }
            int newPort = Integer.parseInt(textField.getText());
            int oldPort = settings.getExternalProxy().getPort();
            if (newPort != oldPort) {
                settings.getExternalProxy().setPort(newPort);
                refreshAppSettings();
            }
        });

        // comboBox
        JFXComboBox<ProxyTypeLabel> proxyComboBox = settingController.getProxyComboBox();
        for (ProxyProtocol proxyType : ProxyProtocol.values()) {
            if (proxyType.isActive()) {
                ProxyTypeLabel label = new ProxyTypeLabel(proxyType.getName()) {
                    @Override
                    public ProxyProtocol getProxyType() {
                        return proxyType;
                    }
                };
                proxyComboBox.getItems().add(label);
            }
        }
        proxyComboBox.getSelectionModel().selectFirst();
        proxyComboBox.valueProperty().addListener(((observable, oldValue, newValue) -> {
            ProxyProtocol protocol = newValue.getProxyType();
            settings.getExternalProxy().setProtocol(protocol);
            refreshAppSettings();
        }));

        // external proxy auth
        settingController.getExProxyAuth().selectedProperty().addListener(((observable, oldValue, newValue) -> {
            settingController.getExUsernameLabel().setVisible(newValue);
            settingController.getExPasswordLabel().setVisible(newValue);
            settingController.getExUsername().setVisible(newValue);
            settingController.getExPassword().setVisible(newValue);

            settings.getExternalProxy().setProxyAuth(newValue);
            refreshAppSettings();
        }));
        addUnFocusedEvent(settingController.getExUsername(), textField -> {
            String newUsername = textField.getText();
            String oldUsername = settings.getExternalProxy().getUsername();
            if (!StringUtils.equals(newUsername, oldUsername)) {
                settings.getExternalProxy().setUsername(newUsername);
                refreshAppSettings();
            }
        });
        addUnFocusedEvent(settingController.getExPassword(), textField -> {
            String newPwd = textField.getText();
            String oldPwd = settings.getExternalProxy().getPassword();
            if (!StringUtils.equals(newPwd, oldPwd)) {
                settings.getExternalProxy().setUsername(newPwd);
                refreshAppSettings();
            }
        });
    }

    @Override
    public void initValues(ApplicationConfig appConfig) {
        // external proxy settings tab
        ExternalProxyConfig externalProxy = appConfig.getSettings().getExternalProxy();
        if (externalProxy != null) {
            settingController.getProxyComboBox().getSelectionModel().select(externalProxy.getProtocol() == null ?
                    0 : externalProxy.getProtocol().getOrdinal());
            settingController.getExProxyHost().setText(externalProxy.getHost());
            settingController.getExProxyPort().setText(String.valueOf(externalProxy.getPort()));
            settingController.getExProxyAuth().setSelected(externalProxy.isProxyAuth());
            settingController.getExUsername().setText(externalProxy.getUsername());
            settingController.getExPassword().setText(externalProxy.getPassword());
        } else {
            settingController.getProxyComboBox().getSelectionModel().select(0);
        }
    }

    @Override
    public void update(ApplicationConfig appConfig) {
        Settings settings = appConfig.getSettings();
        ExternalProxyConfig externalProxy = settings.getExternalProxy();
        if (externalProxy == null) {
            externalProxy = new ExternalProxyConfig();
            settings.setExternalProxy(externalProxy);
        }
        ProxyProtocol protocol = settingController.getProxyComboBox().getValue().getProxyType();
        externalProxy.setUsingExternalProxy(protocol != ProxyProtocol.NONE);
        // TODO: bugfix 切换协议后报错
        //  ProxyConnectException: http, none, /127.0.0.1:10808 => www.google.com/<unresolved>:443, disconnected
        externalProxy.setProtocol(protocol);
        externalProxy.setHost(settingController.getExProxyHost().getText());
        externalProxy.setPort(Integer.parseInt(settingController.getExProxyPort().getText()));
        externalProxy.setProxyAuth(settingController.getExProxyAuth().isSelected());
        externalProxy.setUsername(settingController.getExUsername().getText());
        externalProxy.setPassword(settingController.getExPassword().getText());
    }
}

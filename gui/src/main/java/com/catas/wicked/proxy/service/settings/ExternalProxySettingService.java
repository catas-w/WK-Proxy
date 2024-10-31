package com.catas.wicked.proxy.service.settings;

import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.config.ExternalProxyConfig;
import com.catas.wicked.common.config.Settings;
import com.catas.wicked.common.constant.ProxyProtocol;
import com.catas.wicked.proxy.gui.componet.ProxyTypeLabel;
import com.jfoenix.controls.JFXComboBox;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class ExternalProxySettingService extends AbstractSettingService{

    @Override
    public void init() {
        // setIntegerStringConverter(settingController.getExProxyPort(), 10808);
        addPositiveNumValidator(settingController.getExProxyPort(), "Illegal port!");
        addUnDisableRequiredValidator(settingController.getExProxyPort(), "Illegal port!");
        addUnDisableRequiredValidator(settingController.getExProxyHost(), "Cannot be empty!");

        JFXComboBox<ProxyTypeLabel> proxyComboBox = settingController.getProxyComboBox();
        for (ProxyProtocol proxyType : ProxyProtocol.values()) {
            ProxyTypeLabel label = new ProxyTypeLabel(proxyType.getName()) {
                @Override
                public ProxyProtocol getProxyType() {
                    return proxyType;
                }
            };
            proxyComboBox.getItems().add(label);
        }

        // proxyComboBox.getSelectionModel().selectedIndexProperty().addListener(((observable, oldValue, newValue) -> {
        //     // disable other fields when not using proxy or system proxy
        //     boolean disableFields = newValue.intValue() < 2;
        //     ((Pane) proxyComboBox.getParent()).getChildren().stream()
        //             .skip(1)
        //             .filter(node -> node != proxyComboBox)
        //             .forEach(node -> node.setDisable(disableFields));
        //     if (!disableFields) {
        //         addRequiredValidator(settingController.getExProxyHost());
        //         addRequiredValidator(settingController.getExProxyPort());
        //     } else {
        //         removeRequiredValidator(settingController.getExProxyHost());
        //         removeRequiredValidator(settingController.getExProxyPort());
        //     }
        // }));
        proxyComboBox.getSelectionModel().selectFirst();

        settingController.getExProxyAuth().selectedProperty().addListener(((observable, oldValue, newValue) -> {
            settingController.getExUsernameLabel().setVisible(newValue);
            settingController.getExPasswordLabel().setVisible(newValue);
            settingController.getExUsername().setVisible(newValue);
            settingController.getExPassword().setVisible(newValue);
        }));
    }

    @Override
    public void initValues(ApplicationConfig appConfig) {
        // external proxy settings tab
        ExternalProxyConfig externalProxy = appConfig.getSettings().getExternalProxy();
        if (externalProxy != null) {
            settingController.getProxyComboBox().getSelectionModel().select(externalProxy.getProtocol() == null ?
                    0 : externalProxy.getProtocol().ordinal());
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

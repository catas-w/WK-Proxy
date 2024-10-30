package com.catas.wicked.proxy.service.settings;

import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.config.Settings;
import com.catas.wicked.common.constant.ThrottlePreset;
import com.catas.wicked.common.constant.WorkerConstant;
import com.catas.wicked.common.util.AlertUtils;
import com.catas.wicked.common.util.WebUtils;
import com.jfoenix.controls.JFXComboBox;
import jakarta.inject.Singleton;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class ProxySettingService extends AbstractSettingService{

    @Override
    public void init() {
        // setIntegerStringConverter(settingController.getPortField(), 9624);
        addRequiredValidator(settingController.getPortField());
        addPositiveNumValidator(settingController.getPortField(), "Illegal port format!");

        // throttle
        JFXComboBox<Labeled> throttleComboBox = settingController.getThrottleComboBox();
        for (ThrottlePreset preset : ThrottlePreset.values()) {
            throttleComboBox.getItems().add(new Label(preset.name()));
        }

    }

    @Override
    public void initValues(ApplicationConfig appConfig) {
        Settings settings = appConfig.getSettings();
        settingController.getPortField().setText(String.valueOf(settings.getPort()));
        // settingController.getSysProxyBtn().setSelected(settings.isSystemProxy());
        settingController.getSysProxyExcludeArea().setText(getTextFromList(settings.getSysProxyBypassList()));

        // throttle
        ThrottlePreset preset = settings.getThrottlePreset();
        JFXComboBox<Labeled> throttleComboBox = settingController.getThrottleComboBox();
        if (preset == null) {
            throttleComboBox.getSelectionModel().select(0);
        } else {
            throttleComboBox.getSelectionModel().select(preset.ordinal());
        }
    }

    @Override
    public void update(ApplicationConfig appConfig) {
        Settings settings = appConfig.getSettings();
        int newPort = Integer.parseInt(settingController.getPortField().getText());
        int oldPort = settings.getPort();

        // restart server if port changed
        if (oldPort != newPort) {
            // check pot available
            if (!WebUtils.isPortAvailable(newPort)) {
                AlertUtils.alertWarning("Port " + newPort + " is unavailable");
                return;
            }
            settings.setPort(newPort);
            try {
                settingController.getProxyServer().shutdown();
                settingController.getProxyServer().start();
            } catch (Exception e) {
                log.error("Error in restarting proxy server.", e);
                AlertUtils.alertWarning("Port " + newPort + " is unavailable");
                settings.setPort(oldPort);
                settingController.getProxyServer().start();
                return;
            }
        }

        // settings.setSystemProxy(settingController.getSysProxyBtn().isSelected());
        settings.setSysProxyBypassList(getListFromText(settingController.getSysProxyExcludeArea().getText()));

        // manually invoke systemProxyWorker
        settingController.getScheduledManager().invoke(WorkerConstant.SYS_PROXY_WORKER);
    }
}

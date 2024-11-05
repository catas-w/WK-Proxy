package com.catas.wicked.proxy.service.settings;

import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.config.Settings;
import com.catas.wicked.common.constant.ThrottlePreset;
import com.catas.wicked.common.constant.WorkerConstant;
import com.catas.wicked.common.pipeline.MessageQueue;
import com.catas.wicked.common.util.AlertUtils;
import com.catas.wicked.common.util.WebUtils;
import com.catas.wicked.proxy.gui.componet.ThrottleTypeLabel;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXToggleButton;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;

@Slf4j
@Singleton
public class ProxySettingService extends AbstractSettingService{


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

        // port
        addRequiredValidator(settingController.getPortField());
        addPositiveNumValidator(settingController.getPortField(), "Illegal port format!");
        addUnFocusedEvent(settingController.getPortField(), portField -> {
            if (!portField.validate()) {
                log.warn("port field invalid: {}", portField.getText());
                return;
            }
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
                }
            }
        });

        // throttle
        JFXToggleButton throttleBtn = settingController.getThrottleBtn();
        JFXComboBox<ThrottleTypeLabel> throttleComboBox = settingController.getThrottleComboBox();
        throttleComboBox.setDisable(!settings.isThrottle());
        for (ThrottlePreset preset : ThrottlePreset.values()) {
            throttleComboBox.getItems().add(new ThrottleTypeLabel(preset.getDesc()) {
                @Override
                public ThrottlePreset getThrottlePreset() {
                    return preset;
                }
            });
        }
        throttleComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            settings.setThrottlePreset(newValue.getThrottlePreset());
            refreshAppSettings();
        });

        settingController.getThrottleBtn().setSelected(settings.isThrottle());
        throttleBtn.selectedProperty().addListener(((observable, oldValue, newValue) -> {
            if (newValue != oldValue) {
                throttleComboBox.setDisable(!newValue);
                settings.setThrottle(newValue);
                refreshAppSettings();
                // TODO
                settingController.updateThrottleBtn(settings.isThrottle());
            }
        }));

        // system proxy
        settingController.getSysProxyOnLaunchBtn().selectedProperty().addListener(((observable, oldValue, newValue) -> {
            settings.setEnableSysProxyOnLaunch(newValue);
            refreshAppSettings();
        }));
        // sysProxy bypass
        addUnFocusedEvent(settingController.getSysProxyExcludeArea(), area -> {
            List<String> bypassList = getListFromText(area.getText());
            if (!Objects.equals(bypassList, settings.getSysProxyBypassList())) {
                settings.setSysProxyBypassList(bypassList);
                refreshAppSettings();
            }
        });
    }

    @Override
    public void initValues(ApplicationConfig appConfig) {
        Settings settings = appConfig.getSettings();
        settingController.getPortField().setText(String.valueOf(settings.getPort()));
        settingController.getSysProxyOnLaunchBtn().setSelected(settings.isEnableSysProxyOnLaunch());
        settingController.getSysProxyExcludeArea().setText(getTextFromList(settings.getSysProxyBypassList()));

        // throttle
        settingController.getThrottleBtn().setSelected(settings.isThrottle());
        ThrottlePreset preset = settings.getThrottlePreset();
        JFXComboBox<ThrottleTypeLabel> throttleComboBox = settingController.getThrottleComboBox();
        if (preset == null) {
            throttleComboBox.getSelectionModel().select(0);
        } else {
            throttleComboBox.getSelectionModel().select(preset.ordinal());
        }
    }

    @Override
    public void update(ApplicationConfig appConfig) {
        Settings settings = appConfig.getSettings();


        // settings.setSystemProxy(settingController.getSysProxyBtn().isSelected());
        settings.setSysProxyBypassList(getListFromText(settingController.getSysProxyExcludeArea().getText()));

        // manually invoke systemProxyWorker
        settingController.getScheduledManager().invoke(WorkerConstant.SYS_PROXY_WORKER);
    }
}

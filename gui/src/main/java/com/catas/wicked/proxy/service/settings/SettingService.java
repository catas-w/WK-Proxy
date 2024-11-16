package com.catas.wicked.proxy.service.settings;

import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.proxy.gui.controller.SettingController;

public interface SettingService {

    /**
     * init setting page
     */
    void init();

    /**
     * init setting page values on display
     */
    void initValues(ApplicationConfig appConfig);

    /**
     * perform update setting values
     */
    @Deprecated
    default void update(ApplicationConfig appConfig) {}

    void setSettingController(SettingController settingController);
}

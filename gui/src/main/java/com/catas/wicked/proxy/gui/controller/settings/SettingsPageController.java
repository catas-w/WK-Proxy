package com.catas.wicked.proxy.gui.controller.settings;

import com.catas.wicked.proxy.service.settings.SettingsDraft;

public interface SettingsPageController {

    void load(SettingsDraft draft, Runnable changeListener);

    boolean validate();

    default void focusFirstError() {
    }

    default void onShown() {
    }

    default void onLocaleChanged() {
    }

    default void dispose() {
    }
}

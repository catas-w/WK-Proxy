package com.catas.wicked.proxy.service.settings;

public record SettingsApplyResult(boolean success, SettingsChangeSet changes, String errorMessage) {

    public static SettingsApplyResult success(SettingsChangeSet changes) {
        return new SettingsApplyResult(true, changes, null);
    }

    public static SettingsApplyResult failure(SettingsChangeSet changes, Throwable error) {
        String message = error == null || error.getMessage() == null
                ? "Unable to apply settings"
                : error.getMessage();
        return new SettingsApplyResult(false, changes, message);
    }
}

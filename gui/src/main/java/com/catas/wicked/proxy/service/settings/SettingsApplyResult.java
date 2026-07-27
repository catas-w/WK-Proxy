package com.catas.wicked.proxy.service.settings;

public record SettingsApplyResult(
        boolean success,
        SettingsChangeSet changes,
        SettingsApplyFailureType failureType,
        String errorMessage,
        Integer rejectedPort
) {

    public static SettingsApplyResult success(SettingsChangeSet changes) {
        return new SettingsApplyResult(true, changes, SettingsApplyFailureType.NONE, null, null);
    }

    public static SettingsApplyResult portUnavailable(SettingsChangeSet changes, int port) {
        return new SettingsApplyResult(
                false, changes, SettingsApplyFailureType.PORT_UNAVAILABLE, null, port);
    }

    public static SettingsApplyResult failure(SettingsChangeSet changes, Throwable error) {
        String message = error == null || error.getMessage() == null
                ? "Unable to apply settings"
                : error.getMessage();
        return new SettingsApplyResult(
                false, changes, SettingsApplyFailureType.APPLY_ERROR, message, null);
    }
}

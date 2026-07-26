package com.catas.wicked.proxy.service.settings;

import com.catas.wicked.common.config.Settings;

import java.util.Objects;

public final class SettingsDraft {

    private Settings baseline;
    private final Settings value;

    private SettingsDraft(Settings source) {
        baseline = source.copy();
        value = source.copy();
    }

    public static SettingsDraft from(Settings source) {
        return new SettingsDraft(Objects.requireNonNull(source, "source"));
    }

    public Settings value() {
        return value;
    }

    public Settings baseline() {
        return baseline.copy();
    }

    public Settings snapshot() {
        return value.copy();
    }

    public boolean isDirty() {
        return !baseline.equals(value);
    }

    public void markApplied() {
        baseline = value.copy();
    }
}

package com.catas.wicked.common.constant;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

import java.util.Locale;

@Getter
public enum LanguagePreset {
    ENGLISH("en", "English", Locale.ENGLISH),
    CHINESE("zh_CN", "简体中文", Locale.SIMPLIFIED_CHINESE);

    private final String name;
    private final String desc;
    private final Locale locale;

    LanguagePreset(String name, String desc, Locale locale) {
        this.name = name;
        this.desc = desc;
        this.locale = locale;
    }

    private LanguagePreset getByName(String name) {
        if (name == null) {
            return null;
        }
        for (LanguagePreset value : LanguagePreset.values()) {
            if (value.getName().equals(name)) {
                return value;
            }
        }
        return null;
    }

    @JsonValue
    public String getValue() {
        return name;
    }

    // 使用 @JsonCreator 自定义解析逻辑
    @JsonCreator
    public static LanguagePreset fromValue(String value) {
        for (LanguagePreset status : LanguagePreset.values()) {
            if (status.getName().equalsIgnoreCase(value)) {
                return status;
            }
        }

        // default value
        return ENGLISH;
    }
}

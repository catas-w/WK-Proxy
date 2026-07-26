package com.catas.wicked.proxy.gui.controller.settings;

import com.catas.wicked.proxy.gui.componet.validator.PositiveIntegerValidator;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.validation.RequiredFieldValidator;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;

final class SettingsFormSupport {

    private SettingsFormSupport() {
    }

    static void require(JFXTextField field, String message) {
        RequiredFieldValidator validator = new RequiredFieldValidator();
        validator.setMessage(message);
        field.getValidators().add(validator);
    }

    static void positiveInteger(JFXTextField field, String message) {
        PositiveIntegerValidator validator = new PositiveIntegerValidator();
        validator.setMessage(message);
        field.getValidators().add(validator);
    }

    static List<String> parseList(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        return Arrays.stream(text.split(";"))
                .map(String::strip)
                .filter(StringUtils::isNotBlank)
                .toList();
    }

    static String formatList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream()
                .filter(StringUtils::isNotBlank)
                .map(String::strip)
                .reduce("", (left, right) -> left + right + ";\n");
    }
}

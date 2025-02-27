package com.catas.wicked.proxy.service.settings;

import com.catas.wicked.common.bean.message.BaseMessage;
import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.pipeline.MessageQueue;
import com.catas.wicked.common.pipeline.Topic;
import com.catas.wicked.proxy.gui.componet.validator.PositiveIntegerValidator;
import com.catas.wicked.proxy.gui.componet.validator.RequiredValidator;
import com.catas.wicked.proxy.gui.controller.SettingController;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.validation.RequiredFieldValidator;
import javafx.scene.Node;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextInputControl;
import javafx.util.converter.IntegerStringConverter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

@Slf4j
public abstract class AbstractSettingService implements SettingService {

    protected SettingController settingController;

    protected MessageQueue messageQueue;

    protected ApplicationConfig applicationConfig;

    @Override
    public void setSettingController(SettingController settingController) {
        this.settingController = settingController;
    }

    /**
     * make textInputControl integer-only
     * @param textInputControl text field
     * @param defaultValue default integer value
     */
    protected void setIntegerStringConverter(TextInputControl textInputControl, int defaultValue) {
        UnaryOperator<TextFormatter.Change> textIntegerFilter = change -> {
            String newText = change.getControlNewText();
            if (newText.matches("-?([1-9][0-9]*)?")) {
                return change;
            }
            return null;
        };
        textInputControl.setTextFormatter(
                new TextFormatter<>(new IntegerStringConverter(), defaultValue, textIntegerFilter));
    }

    /**
     * add requiredValidator for jfxTextField
     * @param textField jfxTextField
     */
    protected void addRequiredValidator(JFXTextField textField) {
        if (textField == null) {
            return;
        }
        RequiredFieldValidator validator = new RequiredFieldValidator();
        validator.setMessage("Cannot be empty!");
        // FontIcon warnIcon = new FontIcon(FontAwesomeSolid.EXCLAMATION_TRIANGLE);
        // warnIcon.getStyleClass().add("error");
        // validator.setIcon(warnIcon);
        textField.getValidators().add(validator);
        textField.focusedProperty().addListener((o, oldVal, newVal) -> {
            if (!newVal) {
                textField.validate();
            }
        });
    }

    /**
     * not validate if textField is disabled
     */
    protected void addUnDisableRequiredValidator(JFXTextField textField, String msg) {
        if (textField == null) {
            return;
        }
        RequiredValidator validator = new RequiredValidator(msg);
        textField.getValidators().add(validator);
        textField.focusedProperty().addListener((o, oldVal, newVal) -> {
            if (!newVal) {
                textField.validate();
            }
        });
    }

    protected void addPositiveNumValidator(JFXTextField textField) {
        addPositiveNumValidator(textField, null);
    }

    protected void addPositiveNumValidator(JFXTextField textField, String msg) {
        if (textField == null) {
            return;
        }
        PositiveIntegerValidator validator = new PositiveIntegerValidator();
        if (msg != null) {
            validator.setMessage(msg);
        }
        textField.getValidators().add(validator);
        textField.focusedProperty().addListener((o, oldVal, newVal) -> {
            if (!newVal) {
                textField.validate();
            }
        });
    }

    protected void removeRequiredValidator(JFXTextField textField) {
        if (textField == null) {
            return;
        }
        textField.getValidators().removeIf(validator -> validator instanceof RequiredFieldValidator);
    }

    /**
     * get text from include/exclude list for display
     * @param list include/exclude list
     * @return text
     */
    protected String getTextFromList(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        list.forEach(value -> {
            if (StringUtils.isNotBlank(value)) {
                builder.append(value).append(";\n");
            }
        });
        return builder.toString();
    }

    /**
     * get list from include/exclude text for update settings
     * @param text include/exclude text
     * @return list
     */
    protected List<String> getListFromText(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        List<String> list = Arrays.stream(text.split(";"))
                .map(String::strip)
                .filter(StringUtils::isNotBlank)
                .toList();
        return list;
    }

    /**
     * add unfocused event
     */
    protected <T extends Node> void addUnFocusedEvent(T node, Consumer<T> consumer) {
        if (node == null || consumer == null) {
            return;
        }
        node.focusedProperty().addListener(((observable, oldValue, newValue) -> {
            if (!newValue) {
                consumer.accept(node);
            }
        }));
    }

    /**
     * update settings file by msg
     */
    protected void refreshAppSettings() {
        if (messageQueue != null) {
            messageQueue.clearAndPushMsg(Topic.UPDATE_SETTING_FILE, new BaseMessage());
        } else {
            log.error("messageQueue is null!");
        }
    }
}

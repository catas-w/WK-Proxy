package com.catas.wicked.proxy.service.settings;

import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.config.Settings;
import com.catas.wicked.common.constant.LanguagePreset;
import com.catas.wicked.common.pipeline.MessageQueue;
import com.catas.wicked.proxy.gui.componet.EnumLabel;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.controls.JFXToggleButton;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;

/**
 * setting service for general-page
 */
@Slf4j
@Singleton
public class GeneralSettingService extends AbstractSettingService {

    @Inject
    private ApplicationConfig applicationConfig;

    @Inject
    public void setMessageQueue(MessageQueue messageQueue) {
        this.messageQueue = messageQueue;
    }

    @Override
    public void init() {
        Settings settings = applicationConfig.getSettings();

        // update language
        JFXComboBox<EnumLabel<LanguagePreset>> languageComboBox = settingController.getLanguageComboBox();
        for (LanguagePreset value : LanguagePreset.values()) {
            EnumLabel<LanguagePreset> enumLabel = new EnumLabel<>(value, value::getDesc);
            languageComboBox.getItems().add(enumLabel);
            if (value == settings.getLanguage()) {
                languageComboBox.getSelectionModel().select(enumLabel);
            }
        }
        languageComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                settings.setLanguage(newValue.getEnum());
                settingController.getLangAlertLabel().setVisible(true);
                refreshAppSettings();
            }
        });

        // update display button label
        JFXToggleButton buttonLabelBtn = settingController.getButtonLabelBtn();
        buttonLabelBtn.setSelected(settings.isShowButtonLabel());
        buttonLabelBtn.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                settings.setShowButtonLabel(newValue);
                applicationConfig.getObservableConfig().setShowButtonLabel(newValue);
                refreshAppSettings();
            }
        });

        // update maxSizeField
        JFXTextField maxSizeField = settingController.getMaxSizeField();
        addRequiredValidator(maxSizeField);
        addPositiveNumValidator(maxSizeField, "Illegal value!");
        addUnFocusedEvent(maxSizeField, obj -> {
            if (!maxSizeField.validate()) {
                log.warn("Validation failed: {}", maxSizeField.getText());
                return;
            }

            int maxSize = Integer.parseInt(maxSizeField.getText());
            if (settings.getMaxContentSize() != maxSize) {
                settings.setMaxContentSize(maxSize);
                refreshAppSettings();
            }
        });

        // update bypassField
        addUnFocusedEvent(settingController.getRecordExcludeArea(), area -> {
            List<String> list = getListFromText(area.getText());
            if (!Objects.equals(list, settings.getRecordExcludeList())) {
                settings.setRecordExcludeList(list);
                refreshAppSettings();
            }
        });
    }

    @Override
    public void initValues(ApplicationConfig appConfig) {
        Settings settings = appConfig.getSettings();
        for (EnumLabel<LanguagePreset> item : settingController.getLanguageComboBox().getItems()) {
            if (item.getEnum() == settings.getLanguage()) {
                settingController.getLanguageComboBox().getSelectionModel().select(item);
                break;
            }
        }

        settingController.getMaxSizeField().setText(String.valueOf(settings.getMaxContentSize()));
        settingController.getRecordExcludeArea().setText(getTextFromList(settings.getRecordExcludeList()));
        settingController.getButtonLabelBtn().setSelected(settings.isShowButtonLabel());
    }

    @Override
    public void update(ApplicationConfig appConfig) {
        Settings settings = appConfig.getSettings();
        settings.setLanguage(settingController.getLanguageComboBox().getSelectionModel().getSelectedItem().getEnum());
        settings.setMaxContentSize(Integer.parseInt(settingController.getMaxSizeField().getText()));

        // settings.setRecordIncludeList(getListFromText(settingController.getRecordIncludeArea().getText()));
        settings.setRecordExcludeList(getListFromText(settingController.getRecordExcludeArea().getText()));

    }
}

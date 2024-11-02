package com.catas.wicked.proxy.service.settings;

import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.config.Settings;
import com.catas.wicked.common.pipeline.MessageQueue;
import com.jfoenix.controls.JFXTextField;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.scene.control.Label;
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
        settingController.getLanguageComboBox().getItems().add(new Label("English"));
        settingController.getLanguageComboBox().getItems().add(new Label("简体中文"));
        settingController.getLanguageComboBox().getSelectionModel().select(0);
        Settings settings = applicationConfig.getSettings();

        // update language
        settingController.getLanguageComboBox().valueProperty().addListener((observable, oldValue, newValue) -> {
            String selectLang = newValue.getText();
            settings.setLanguage(selectLang);
            refreshAppSettings();
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
        settingController.getLanguageComboBox().getSelectionModel().select(0);
        settingController.getMaxSizeField().setText(String.valueOf(settings.getMaxContentSize()));

        settingController.getRecordExcludeArea().setText(getTextFromList(settings.getRecordExcludeList()));
    }

    @Override
    public void update(ApplicationConfig appConfig) {
        Settings settings = appConfig.getSettings();
        settings.setLanguage(settingController.getLanguageComboBox().getSelectionModel().getSelectedItem().getText());
        settings.setMaxContentSize(Integer.parseInt(settingController.getMaxSizeField().getText()));

        // settings.setRecordIncludeList(getListFromText(settingController.getRecordIncludeArea().getText()));
        settings.setRecordExcludeList(getListFromText(settingController.getRecordExcludeArea().getText()));

    }
}

package com.catas.wicked.proxy.service.settings;

import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.config.Settings;
import jakarta.inject.Singleton;
import javafx.scene.control.Label;

/**
 * setting service for general-page
 */
@Singleton
public class GeneralSettingService extends AbstractSettingService {

    @Override
    public void init() {
        settingController.getLanguageComboBox().getItems().add(new Label("English"));
        settingController.getLanguageComboBox().getItems().add(new Label("简体中文"));
        settingController.getLanguageComboBox().getSelectionModel().select(0);

        setIntegerStringConverter(settingController.getMaxSizeField(), 10);
        addRequiredValidator(settingController.getMaxSizeField());
    }

    @Override
    public void initValues(ApplicationConfig appConfig) {
        Settings settings = appConfig.getSettings();
        settingController.getLanguageComboBox().getSelectionModel().select(0);
        // settingController.getRecordBtn().setSelected(true);
        settingController.getMaxSizeField().setText(String.valueOf(settings.getMaxContentSize()));

        // settingController.getRecordIncludeArea().setText(getTextFromList(settings.getRecordIncludeList()));
        settingController.getRecordExcludeArea().setText(getTextFromList(settings.getRecordExcludeList()));
    }

    @Override
    public void update(ApplicationConfig appConfig) {
        Settings settings = appConfig.getSettings();
        settings.setLanguage(settingController.getLanguageComboBox().getSelectionModel().getSelectedItem().getText());
        // settings.setRecording(settingController.getRecordBtn().isSelected());
        settings.setMaxContentSize(Integer.parseInt(settingController.getMaxSizeField().getText()));

        // settings.setRecordIncludeList(getListFromText(settingController.getRecordIncludeArea().getText()));
        settings.setRecordExcludeList(getListFromText(settingController.getRecordExcludeArea().getText()));

    }
}

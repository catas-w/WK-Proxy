package com.catas.wicked.proxy.gui.controller.settings;

import com.catas.wicked.common.config.Settings;
import com.catas.wicked.common.constant.LanguagePreset;
import com.catas.wicked.proxy.gui.componet.EnumLabel;
import com.catas.wicked.proxy.service.settings.SettingsDraft;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.controls.JFXToggleButton;
import io.micronaut.context.annotation.Prototype;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

import java.net.URL;
import java.util.ResourceBundle;

@Prototype
public class GeneralSettingsPageController implements SettingsPageController, Initializable {

    @FXML private JFXComboBox<EnumLabel<LanguagePreset>> languageComboBox;
    @FXML private Label langAlertLabel;
    @FXML private JFXToggleButton buttonLabelBtn;
    @FXML private JFXTextField maxSizeField;
    @FXML private TextArea recordExcludeArea;

    private SettingsDraft draft;
    private Runnable changeListener = () -> {};
    private boolean loading;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        for (LanguagePreset value : LanguagePreset.values()) {
            languageComboBox.getItems().add(new EnumLabel<>(value, value::getDesc));
        }
        SettingsFormSupport.require(maxSizeField, resources.getString("validation.required"));
        SettingsFormSupport.positiveInteger(maxSizeField, resources.getString("validation.positive-integer"));

        languageComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (!loading && draft != null && newValue != null) {
                draft.value().setLanguage(newValue.getEnum());
                langAlertLabel.setVisible(true);
                changed();
            }
        });
        buttonLabelBtn.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (!loading && draft != null) {
                draft.value().setShowButtonLabel(newValue);
                changed();
            }
        });
        maxSizeField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!loading && draft != null && newValue.matches("[1-9][0-9]*")) {
                draft.value().setMaxContentSize(Integer.parseInt(newValue));
                changed();
            }
        });
        recordExcludeArea.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!loading && draft != null) {
                draft.value().setRecordExcludeList(SettingsFormSupport.parseList(newValue));
                changed();
            }
        });
    }

    @Override
    public void load(SettingsDraft draft, Runnable changeListener) {
        this.draft = draft;
        this.changeListener = changeListener == null ? () -> {} : changeListener;
        Settings settings = draft.value();
        loading = true;
        try {
            languageComboBox.getItems().stream()
                    .filter(item -> item.getEnum() == settings.getLanguage())
                    .findFirst().ifPresent(languageComboBox.getSelectionModel()::select);
            buttonLabelBtn.setSelected(settings.isShowButtonLabel());
            maxSizeField.setText(String.valueOf(settings.getMaxContentSize()));
            recordExcludeArea.setText(SettingsFormSupport.formatList(settings.getRecordExcludeList()));
            langAlertLabel.setVisible(false);
        } finally {
            loading = false;
        }
    }

    @Override
    public boolean validate() {
        return maxSizeField.validate();
    }

    @Override
    public void focusFirstError() {
        if (!maxSizeField.validate()) {
            maxSizeField.requestFocus();
        }
    }

    private void changed() {
        changeListener.run();
    }
}

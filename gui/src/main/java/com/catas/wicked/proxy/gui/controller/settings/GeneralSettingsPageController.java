package com.catas.wicked.proxy.gui.controller.settings;

import com.catas.wicked.common.config.Settings;
import com.catas.wicked.common.constant.LanguagePreset;
import com.catas.wicked.proxy.gui.componet.EnumLabel;
import com.catas.wicked.proxy.gui.componet.validator.PositiveIntegerValidator;
import com.catas.wicked.proxy.service.LocalizationService;
import com.catas.wicked.proxy.service.settings.SettingsDraft;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.controls.JFXToggleButton;
import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Inject;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import com.jfoenix.validation.RequiredFieldValidator;

import java.net.URL;
import java.util.ResourceBundle;

@Prototype
public class GeneralSettingsPageController implements SettingsPageController, Initializable {

    @FXML private JFXComboBox<EnumLabel<LanguagePreset>> languageComboBox;
    @FXML private Label interfaceSectionLabel;
    @FXML private Label languageLabel;
    @FXML private Label showButtonLabel;
    @FXML private Label recordSectionLabel;
    @FXML private Label recordSizeLabel;
    @FXML private Label retainedPayloadSizeLabel;
    @FXML private Tooltip recordSizeTooltip;
    @FXML private Tooltip retainedPayloadSizeTooltip;
    @FXML private Label recordBypassLabel;
    @FXML private Tooltip recordBypassTooltip;
    @FXML private JFXToggleButton buttonLabelBtn;
    @FXML private JFXTextField maxSizeField;
    @FXML private JFXTextField retainedPayloadSizeField;
    @FXML private TextArea recordExcludeArea;

    @Inject private LocalizationService localization;

    private SettingsDraft draft;
    private Runnable changeListener = () -> {};
    private boolean loading;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        localization.bind(interfaceSectionLabel.textProperty(), "interface-sep.label");
        localization.bind(languageLabel.textProperty(), "language.label");
        localization.bind(showButtonLabel.textProperty(), "show-btn-label.label");
        localization.bind(recordSectionLabel.textProperty(), "record-sep.label");
        localization.bind(recordSizeLabel.textProperty(), "record-size.label");
        localization.bind(retainedPayloadSizeLabel.textProperty(), "retained-payload-size.label");
        localization.bind(recordSizeTooltip.textProperty(), "record-size.tooltip");
        localization.bind(retainedPayloadSizeTooltip.textProperty(), "retained-payload-size.tooltip");
        localization.bind(recordBypassLabel.textProperty(), "record-bypass.label");
        localization.bind(recordBypassTooltip.textProperty(), "ant-path.tooltip");
        for (LanguagePreset value : LanguagePreset.values()) {
            languageComboBox.getItems().add(new EnumLabel<>(value, value::getDesc));
        }
        SettingsFormSupport.require(maxSizeField, resources.getString("validation.required"));
        SettingsFormSupport.positiveInteger(maxSizeField, resources.getString("validation.positive-integer"));
        SettingsFormSupport.require(retainedPayloadSizeField, resources.getString("validation.required"));
        SettingsFormSupport.positiveInteger(
                retainedPayloadSizeField, resources.getString("validation.positive-integer"));

        languageComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (!loading && draft != null && newValue != null) {
                draft.value().setLanguage(newValue.getEnum());
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
        retainedPayloadSizeField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!loading && draft != null && newValue.matches("[1-9][0-9]*")) {
                draft.value().setRetainedPayloadSizeMb(Integer.parseInt(newValue));
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
            retainedPayloadSizeField.setText(String.valueOf(settings.getRetainedPayloadSizeMb()));
            recordExcludeArea.setText(SettingsFormSupport.formatList(settings.getRecordExcludeList()));
        } finally {
            loading = false;
        }
    }

    @Override
    public boolean validate() {
        return maxSizeField.validate() && retainedPayloadSizeField.validate();
    }

    @Override
    public void focusFirstError() {
        if (!maxSizeField.validate()) {
            maxSizeField.requestFocus();
        } else if (!retainedPayloadSizeField.validate()) {
            retainedPayloadSizeField.requestFocus();
        }
    }

    @Override
    public void onLocaleChanged() {
        maxSizeField.getValidators().forEach(validator -> {
            if (validator instanceof RequiredFieldValidator) {
                validator.setMessage(localization.getMessage("validation.required"));
            } else if (validator instanceof PositiveIntegerValidator) {
                validator.setMessage(localization.getMessage("validation.positive-integer"));
            }
        });
        retainedPayloadSizeField.getValidators().forEach(validator -> {
            if (validator instanceof RequiredFieldValidator) {
                validator.setMessage(localization.getMessage("validation.required"));
            } else if (validator instanceof PositiveIntegerValidator) {
                validator.setMessage(localization.getMessage("validation.positive-integer"));
            }
        });
    }

    private void changed() {
        changeListener.run();
    }
}

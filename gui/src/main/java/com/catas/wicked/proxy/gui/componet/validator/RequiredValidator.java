package com.catas.wicked.proxy.gui.componet.validator;

import com.jfoenix.validation.base.ValidatorBase;
import javafx.scene.control.TextInputControl;

public class RequiredValidator extends ValidatorBase {

    public RequiredValidator(String message) {
        super(message);
    }

    public RequiredValidator() {
    }

    @Override
    protected void eval() {
        if (srcControl.get() instanceof TextInputControl) {
            evalTextInputField();
        }
    }

    private void evalTextInputField() {
        TextInputControl textField = (TextInputControl) srcControl.get();
        if (!textField.isDisabled() && (textField.getText() == null || textField.getText().isEmpty())) {
            hasErrors.set(true);
        } else {
            hasErrors.set(false);
        }
    }
}

package com.catas.wicked.proxy.gui.componet.validator;

import com.jfoenix.validation.base.ValidatorBase;
import javafx.scene.control.TextInputControl;

public class PositiveIntegerValidator extends ValidatorBase {

    public PositiveIntegerValidator(String message) {
        super(message);
    }

    public PositiveIntegerValidator() {
        super("Value must be a positive number!");
    }

    @Override
    protected void eval() {
        if (srcControl.get() instanceof TextInputControl) {
            evalTextInputField();
        }
    }

    private void evalTextInputField() {
        TextInputControl textField = (TextInputControl) srcControl.get();
        String text = textField.getText();
        try {
            hasErrors.set(false);
            if (!text.isEmpty()) {
                int num = Integer.parseInt(text);
                if (num <= 0) {
                    hasErrors.set(true);
                }
            }
        } catch (Exception e) {
            hasErrors.set(true);
        }
    }
}

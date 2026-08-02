package com.catas.wicked.proxy.gui.componet.validator;

import com.jfoenix.validation.base.ValidatorBase;
import javafx.scene.control.TextInputControl;

public class PortValidator extends ValidatorBase {

    private String rangeMessage;

    public PortValidator(String rangeMessage) {
        this.rangeMessage = rangeMessage;
    }

    public void setRangeMessage(String rangeMessage) {
        this.rangeMessage = rangeMessage;
        setMessage(rangeMessage);
    }

    @Override
    protected void eval() {
        if (!(srcControl.get() instanceof TextInputControl field)) {
            hasErrors.set(false);
            return;
        }

        Integer port = parse(field.getText());
        if (port == null) {
            setMessage(rangeMessage);
            hasErrors.set(true);
            return;
        }
        hasErrors.set(false);
    }

    public static boolean isAllowedInput(String value) {
        return value != null && value.matches("[0-9]{0,5}");
    }

    public static Integer parse(String value) {
        try {
            int port = Integer.parseInt(value);
            return port >= 1 && port <= 65535 ? port : null;
        } catch (NumberFormatException error) {
            return null;
        }
    }

    public static Integer step(String value, int delta) {
        Integer port = parse(value);
        if (port == null) {
            return null;
        }
        int nextPort = port + delta;
        return nextPort >= 1 && nextPort <= 65535 ? nextPort : null;
    }
}

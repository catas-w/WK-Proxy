package com.catas.wicked.proxy.gui.componet.validator;

import com.jfoenix.validation.base.ValidatorBase;
import javafx.scene.control.TextInputControl;

public class PortValidator extends ValidatorBase {

    private final String rangeMessage;
    private final String unavailableMessage;
    private Integer unavailablePort;

    public PortValidator(String rangeMessage, String unavailableMessage) {
        this.rangeMessage = rangeMessage;
        this.unavailableMessage = unavailableMessage;
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
        if (port.equals(unavailablePort)) {
            setMessage(String.format(unavailableMessage, port));
            hasErrors.set(true);
            return;
        }
        hasErrors.set(false);
    }

    public void setUnavailablePort(int port) {
        unavailablePort = port;
    }

    public void clearUnavailablePort() {
        unavailablePort = null;
    }

    public static Integer parse(String value) {
        try {
            int port = Integer.parseInt(value);
            return port >= 1 && port <= 65535 ? port : null;
        } catch (NumberFormatException error) {
            return null;
        }
    }
}

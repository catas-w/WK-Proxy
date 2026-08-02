package com.catas.wicked.proxy.render;

import javafx.application.Platform;

import java.util.function.BooleanSupplier;

/**
 * A render operation whose data has already been prepared off the JavaFX thread.
 */
public record PreparedRender(String requestId, boolean empty, Runnable fxAction) {

    public void apply() {
        apply(Platform::isFxApplicationThread);
    }

    void apply(BooleanSupplier fxThreadCheck) {
        if (!fxThreadCheck.getAsBoolean()) {
            throw new IllegalStateException("Detail rendering must run on the JavaFX Application Thread");
        }
        fxAction.run();
    }

    public static PreparedRender noop(String requestId, boolean empty) {
        return new PreparedRender(requestId, empty, () -> { });
    }
}

package com.catas.wicked.common.util;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

import java.util.Objects;
import java.util.Optional;

public class AlertUtils {

    public static final String STYLE_CLASS = "custom-alert";

    public static final String CSS_FILE = Objects.requireNonNull(AlertUtils.class.getResource("/css/app.css")).toExternalForm();

    public static Optional<ButtonType> alert(Alert.AlertType type, String title, String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title == null ? type.name(): title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.getDialogPane().setStyle("-fx-font-family: 'MiSans Normal'");

        // 获取 Alert 的 Stage 并设置 AlwaysOnTop
        Stage alertStage = (Stage) alert.getDialogPane().getScene().getWindow();
        alertStage.setAlwaysOnTop(true);
        alert.getDialogPane().getStyleClass().add(STYLE_CLASS);
        alert.getDialogPane().getStylesheets().add(CSS_FILE);
        return alert.showAndWait();
    }

    public static void alertWarning(String msg) {
        alert(Alert.AlertType.WARNING, null, msg);
    }

    public static void alertLater(Alert.AlertType type, String msg) {
        Platform.runLater(() -> {
            alert(type, msg, msg);
        });
    }

    public static boolean confirm(String title, String message) {
        // Show the alert and wait for user response
        Optional<ButtonType> result = alert(Alert.AlertType.CONFIRMATION, title, message);

        // Return true if OK was clicked, false otherwise
        return result.isPresent() && result.get() == ButtonType.OK;
    }
}

package com.catas.wicked.common.util;

import com.jfoenix.animation.alert.JFXAlertAnimation;
import com.jfoenix.controls.JFXAlert;
import com.jfoenix.controls.JFXDialogLayout;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.stage.Modality;

import java.util.Optional;

public class AlertUtils {

    public static void alertWarning(String msg) {
        alert(Alert.AlertType.WARNING, msg);
    }

    public static void alert(Alert.AlertType type, String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(type.name());
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.getDialogPane().setStyle("-fx-font-family: 'MiSans Normal'");
        alert.showAndWait();
    }

    public static void alertLater(Alert.AlertType type, String msg) {
        Platform.runLater(() -> {
            alert(type, msg);
        });
    }

    public static void alertJfx(Alert.AlertType type, String msg) {
        JFXDialogLayout layout = new JFXDialogLayout();
        layout.setHeading(new Label(type.name()));
        layout.setBody(new Label(msg));

        JFXAlert<Void> alert = new JFXAlert<>();
        alert.setOverlayClose(true);
        alert.setAnimation(JFXAlertAnimation.NO_ANIMATION);
        alert.setContent(layout);
        alert.initModality(Modality.WINDOW_MODAL);

        alert.showAndWait();
    }

    public static boolean confirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null); // Optional: You can set a header or keep it null
        alert.setContentText(message);
        alert.getDialogPane().setStyle("-fx-font-family: 'MiSans Normal'");

        // Show the alert and wait for user response
        Optional<ButtonType> result = alert.showAndWait();

        // Return true if OK was clicked, false otherwise
        return result.isPresent() && result.get() == ButtonType.OK;
    }
}

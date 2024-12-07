package com.catas.wicked.proxy.gui.componet.dialog;

import com.catas.wicked.common.provider.ResourceMessageProvider;
import com.catas.wicked.common.util.SystemUtils;
import com.jfoenix.controls.JFXButton;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Pair;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

import static com.catas.wicked.common.constant.ProxyConstant.CERT_FILE_PATTERN;
import static com.catas.wicked.common.constant.ProxyConstant.PRIVATE_FILE_PATTERN;

@Slf4j
public class CertImportDialog extends Dialog<Pair<CertImportDialog.CertImportData, CertImportDialog.CertImportData>> {

    private static final String CSS_FILE = "/css/cert-dialog.css";
    private static final String APP_CSS_FILE = "/css/app.css";
    private final ButtonType okButton;
    private final ButtonType cancelBtn;
    private final TextArea certArea;
    private final TextArea priKeyTextArea;

    private final CertImportData importCertData = new CertImportData();

    private final CertImportData importPriKeyData = new CertImportData();

    private final ResourceMessageProvider resourceMessageProvider;

    public CertImportDialog(ResourceMessageProvider resourceMessageProvider) {
        this.resourceMessageProvider = resourceMessageProvider;
        setTitle(resourceMessageProvider.getMessage("cert-import.title"));

        // buttons
        okButton = new ButtonType(resourceMessageProvider.getMessage("save.label"), ButtonBar.ButtonData.OK_DONE);
        cancelBtn = new ButtonType(resourceMessageProvider.getMessage("cancel.label"), ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(okButton, cancelBtn);

        VBox vBox = new VBox();
        vBox.setPrefWidth(500);
        vBox.setPrefHeight(400);

        // private key input
        certArea = createInputComponent(vBox, resourceMessageProvider.getMessage("cert-paste-pem.label"), importCertData);
        certArea.setWrapText(true);
        certArea.setPromptText(String.format(CERT_FILE_PATTERN, "********"));

        priKeyTextArea = createInputComponent(vBox, resourceMessageProvider.getMessage("pri-key-paste-pem.label"), importPriKeyData);
        priKeyTextArea.setWrapText(true);
        priKeyTextArea.setPromptText(String.format(PRIVATE_FILE_PATTERN, "********"));

        // dialog
        getDialogPane().setContent(vBox);
        getDialogPane().getStyleClass().add("cert-dialog");
        getDialogPane().lookupButton(okButton).getStyleClass().add("ok-btn");
        getDialogPane().lookupButton(cancelBtn).getStyleClass().add("cancel-btn");
        getDialogPane().getStylesheets().add(Objects.requireNonNull(getClass().getResource(CSS_FILE)).toExternalForm());
        getDialogPane().getStylesheets().add(Objects.requireNonNull(getClass().getResource(APP_CSS_FILE)).toExternalForm());

        // listener on text
        getDialogPane().lookupButton(okButton).setDisable(true);
        certArea.textProperty().addListener((observable, oldValue, newValue) ->
                setOkButtonDisable(newValue.trim().isEmpty() || (!priKeyTextArea.isDisabled() && priKeyTextArea.getText().trim().isEmpty())));
        priKeyTextArea.textProperty().addListener((observable, oldValue, newValue) ->
                setOkButtonDisable(newValue.trim().isEmpty() || (!certArea.isDisabled() && certArea.getText().trim().isEmpty())));

        // Convert the result to a Pair<String, String> when OK button is clicked
        setResultConverter(dialogButton -> {
            if (dialogButton == okButton) {
                if (importCertData.dataType == DataType.TEXT) {
                    String certText = Optional.ofNullable(certArea.getText()).map(String::strip).orElse("");
                    if (!certText.startsWith("-----BEGIN CERTIFICATE-----")) {
                        certText = String.format(CERT_FILE_PATTERN, certText);
                    }
                    importCertData.setText(certText);
                }
                if (importPriKeyData.dataType == DataType.TEXT) {
                    String priKeyText = Optional.ofNullable(priKeyTextArea.getText()).map(String::strip).orElse("");
                    if (!priKeyText.startsWith("-----BEGIN PRIVATE KEY-----")) {
                        priKeyText = String.format(PRIVATE_FILE_PATTERN, priKeyText);
                    }
                    importPriKeyData.setText(priKeyText);
                }
                return new Pair<>(importCertData, importPriKeyData);
            }
            return null;
        });
    }

    public void reset() {
        this.certArea.clear();
        this.priKeyTextArea.clear();
    }

    private void setOkButtonDisable(boolean value) {
        getDialogPane().lookupButton(okButton).setDisable(value);
    }

    private TextArea getAnotherArea(TextArea area) {
        if (area == certArea) {
            return priKeyTextArea;
        } else if (area == priKeyTextArea) {
            return certArea;
        } else {
            return null;
        }
    }

    private TextArea createInputComponent(VBox vBox, String title, CertImportData importData) {
        TextArea textArea = new TextArea();

        // select file button
        JFXButton selectBtn = new JFXButton(resourceMessageProvider.getMessage("select.label"));
        FontIcon icon = new FontIcon();
        icon.setIconLiteral("fas-file-upload");
        selectBtn.setGraphic(icon);
        selectBtn.getStyleClass().add("cert-select-btn");
        StackPane.setAlignment(selectBtn, Pos.BOTTOM_LEFT);
        StackPane.setMargin(selectBtn, new Insets(0, 0, 5, 5));

        // display selected file
        FileDisplayComponent fileDisplay = new FileDisplayComponent("...");
        fileDisplay.setVisible(false);
        fileDisplay.visibleProperty().addListener((observable, oldValue, newValue) -> {
            textArea.setDisable(newValue);
            selectBtn.setVisible(!newValue);

            if (newValue) {
                importData.setDataType(DataType.FILE);
                // okButton disable if anotherArea !disabled && empty
                TextArea anotherArea = getAnotherArea(textArea);
                setOkButtonDisable(anotherArea == null || (!anotherArea.isDisabled() && anotherArea.getText().trim().isEmpty()));
            } else {
                importData.setDataType(DataType.TEXT);
            }
        });
        StackPane.setAlignment(fileDisplay, Pos.CENTER);
        StackPane.setMargin(fileDisplay, new Insets(65, 90, 65, 90));

        // select file dialog
        selectBtn.setOnAction(event -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select a File");

            fileChooser.setInitialDirectory(new File(SystemUtils.USER_HOME));
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Certificate", "*.crt", "*.pem", "*.PEM"),
                    new FileChooser.ExtensionFilter("All Files", "*.*")
            );

            File selectedFile = fileChooser.showOpenDialog(getOwner());
            if (selectedFile != null) {
                importData.setFile(selectedFile);

                // display selected file
                fileDisplay.setDisplay(selectedFile.getName());
                fileDisplay.setVisible(true);
            }
        });

        // stackPane
        StackPane stackPane = new StackPane();
        VBox.setMargin(stackPane, new Insets(0, 0, 10, 0));
        stackPane.getChildren().addAll(textArea, selectBtn, fileDisplay);

        Label label = new Label(title);
        vBox.getChildren().addAll(label, stackPane);
        return textArea;
    }

    public enum DataType {
        FILE,
        TEXT
    }

    @Data
    public static class CertImportData {
        /**
         * default to text
         */
        private DataType dataType = DataType.TEXT;
        private File file;
        private String text;

        public InputStream fetchData() throws IOException {
            if (dataType == DataType.FILE) {
                return new FileInputStream(file);
            } else if (dataType == DataType.TEXT) {
                return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
            } else {
                return null;
            }
        }
    }
}

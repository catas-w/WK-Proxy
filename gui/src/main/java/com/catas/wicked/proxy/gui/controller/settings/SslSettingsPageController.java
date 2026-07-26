package com.catas.wicked.proxy.gui.controller.settings;

import com.catas.wicked.common.bean.HeaderEntry;
import com.catas.wicked.common.config.CertificateConfig;
import com.catas.wicked.common.provider.CertManager;
import com.catas.wicked.common.provider.ResourceMessageProvider;
import com.catas.wicked.common.util.AlertUtils;
import com.catas.wicked.common.util.TableUtils;
import com.catas.wicked.proxy.gui.componet.CertSelectComponent;
import com.catas.wicked.proxy.gui.componet.SelectableTableCell;
import com.catas.wicked.proxy.gui.componet.builder.TextAreaEditorNodeBuilder;
import com.catas.wicked.proxy.gui.componet.dialog.CertImportDialog;
import com.catas.wicked.proxy.service.settings.SettingsDraft;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXToggleButton;
import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Inject;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Prototype
public class SslSettingsPageController implements SettingsPageController, Initializable {

    private static final int MAX_CERT_SIZE = 5;

    @FXML private GridPane sslGridPane;
    @FXML private JFXToggleButton sslBtn;
    @FXML private TextArea sslExcludeArea;
    @FXML private HBox importCertBox;
    @FXML private JFXButton importCertBtn;
    @FXML private Label loadingLabel;

    @Inject private CertManager certManager;
    @Inject private ResourceMessageProvider messages;

    private final ToggleGroup certSelectGroup = new ToggleGroup();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(4),
            runnable -> {
                Thread thread = new Thread(runnable, "settings-cert");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.DiscardPolicy());

    private SettingsDraft draft;
    private Runnable changeListener = () -> {};
    private boolean loadingForm;
    private boolean certificatesLoaded;
    private boolean renderingCertificates;

    @Override
    public void initialize(java.net.URL location, java.util.ResourceBundle resources) {
        sslBtn.selectedProperty().addListener((observable, oldValue, newValue) -> {
            updateEnabledState(newValue);
            if (!loadingForm && draft != null) {
                draft.value().setHandleSsl(newValue);
                changeListener.run();
            }
        });
        sslExcludeArea.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!loadingForm && draft != null) {
                draft.value().setSslExcludeList(SettingsFormSupport.parseList(newValue));
                changeListener.run();
            }
        });
        certSelectGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            if (!renderingCertificates && draft != null
                    && newValue instanceof CertSelectComponent.CertRadioButton button) {
                draft.value().setSelectedCert(button.getCertId());
                changeListener.run();
            }
        });
        importCertBtn.setOnAction(event -> displayImportDialog());
    }

    @Override
    public void load(SettingsDraft draft, Runnable changeListener) {
        this.draft = draft;
        this.changeListener = changeListener == null ? () -> {} : changeListener;
        loadingForm = true;
        try {
            sslBtn.setSelected(draft.value().isHandleSsl());
            sslExcludeArea.setText(SettingsFormSupport.formatList(draft.value().getSslExcludeList()));
            updateEnabledState(sslBtn.isSelected());
        } finally {
            loadingForm = false;
        }
        certificatesLoaded = false;
        clearCertificateRows();
    }

    @Override
    public void onShown() {
        if (!certificatesLoaded) {
            reloadCertificates();
        }
    }

    @Override
    public boolean validate() {
        return true;
    }

    @Override
    public void dispose() {
        executor.shutdownNow();
    }

    private void reloadCertificates() {
        certificatesLoaded = true;
        loadingLabel.setVisible(true);
        importCertBtn.setDisable(true);
        CompletableFuture.supplyAsync(() -> {
            List<CertificateRow> rows = new ArrayList<>();
            for (CertificateConfig config : certManager.getCertList()) {
                rows.add(new CertificateRow(config, certManager.checkInstalled(config.getId())));
            }
            return rows;
        }, executor).whenComplete((rows, error) -> Platform.runLater(() -> {
            loadingLabel.setVisible(false);
            if (error != null) {
                certificatesLoaded = false;
                showError(error);
                return;
            }
            renderCertificates(rows);
        }));
    }

    private void renderCertificates(List<CertificateRow> rows) {
        clearCertificateRows();
        renderingCertificates = true;
        try {
            int rowIndex = 3;
            String selectedId = draft.value().getSelectedCert();
            for (CertificateRow row : rows) {
                CertificateConfig config = row.config();
                String operationIcon = config.isDefault() ? "fas-share-square" : "fas-trash-alt";
                CertSelectComponent component = new CertSelectComponent(
                        config.getName(), config.getId(), operationIcon);
                component.setToggleGroup(certSelectGroup);
                component.setSelected(StringUtils.equals(selectedId, config.getId()));
                component.setPreviewTooltip(messages.getMessage("cert-preview.tooltip"));
                component.setPreviewEvent(event -> displayPreviewDialog(config.getId()));
                if (config.isDefault()) {
                    component.setOperateTooltip(messages.getMessage("cert-export.tooltip"));
                    component.setOperateEvent(event -> saveCertificate(config));
                } else {
                    component.setOperateTooltip(messages.getMessage("cert-delete.tooltip"));
                    component.setOperateEvent(event -> deleteCertificate(config.getId()));
                }
                if (!row.installed()) {
                    component.setAlertLabel(messages.getMessage("cert-install-alert.label"),
                            messages.getMessage("cert-install-click.label"));
                    component.setOnClickLabelAction(event -> installCertificate(config.getId()));
                }
                component.setDisable(!sslBtn.isSelected());
                sslGridPane.add(component, 1, rowIndex++);
            }
            sslGridPane.add(importCertBox, 1, rowIndex);
            importCertBtn.setDisable(rows.size() >= MAX_CERT_SIZE);
        } finally {
            renderingCertificates = false;
        }
    }

    private void clearCertificateRows() {
        sslGridPane.getChildren().remove(importCertBox);
        sslGridPane.getChildren().removeIf(node -> node instanceof CertSelectComponent);
    }

    private void updateEnabledState(boolean enabled) {
        sslGridPane.getChildren().forEach(node -> {
            Integer row = GridPane.getRowIndex(node);
            if (row != null && row > 1 && node != loadingLabel) {
                node.setDisable(!enabled);
            }
        });
    }

    private void displayImportDialog() {
        CertImportDialog dialog = new CertImportDialog(messages);
        dialog.showAndWait().ifPresent(data -> {
            loadingLabel.setVisible(true);
            CompletableFuture.runAsync(() ->
                    certManager.importCert(fetch(data.getKey()), fetch(data.getValue())), executor
            ).whenComplete((unused, error) -> Platform.runLater(() -> {
                loadingLabel.setVisible(false);
                if (error != null) {
                    showError(error);
                } else {
                    certificatesLoaded = false;
                    reloadCertificates();
                }
            }));
        });
    }

    private java.io.InputStream fetch(CertImportDialog.CertImportData data) {
        try {
            return data.fetchData();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private void installCertificate(String certId) {
        if (!AlertUtils.confirm(messages.getMessage("alert.type.warning"),
                messages.getMessage("cert-install-confirm.label"))) {
            return;
        }
        runCertificateCommand(() -> certManager.installCert(certId));
    }

    private void deleteCertificate(String certId) {
        if (!AlertUtils.confirm(messages.getMessage("alert.type.warning"),
                messages.getMessage("cert-delete-confirm.label"))) {
            return;
        }
        runCertificateCommand(() -> {
            if (!certManager.deleteCertConfig(certId)) {
                throw new IllegalStateException(messages.getMessage("alert.msg.error"));
            }
        });
    }

    private void runCertificateCommand(CheckedRunnable command) {
        loadingLabel.setVisible(true);
        CompletableFuture.runAsync(() -> {
            try {
                command.run();
            } catch (Exception error) {
                throw new IllegalStateException(error);
            }
        }, executor).whenComplete((unused, error) -> Platform.runLater(() -> {
            loadingLabel.setVisible(false);
            if (error != null) {
                showError(error);
            } else {
                certificatesLoaded = false;
                reloadCertificates();
            }
        }));
    }

    @SuppressWarnings("unchecked")
    private void displayPreviewDialog(String certId) {
        CompletableFuture.supplyAsync(() -> {
            try {
                return certManager.getCertInfo(certId);
            } catch (Exception error) {
                throw new IllegalStateException(error);
            }
        }, executor).whenComplete((info, error) -> Platform.runLater(() -> {
            if (error != null) {
                showError(error);
                return;
            }
            Dialog<Void> dialog = new Dialog<>();
            dialog.setTitle("Preview Certificate");
            ButtonType close = new ButtonType("OK", ButtonBar.ButtonData.CANCEL_CLOSE);
            dialog.getDialogPane().getButtonTypes().add(close);
            TableView<HeaderEntry> table = new TableView<>();
            TableColumn<HeaderEntry, String> key = new TableColumn<>("Name");
            key.setCellValueFactory(new PropertyValueFactory<>("key"));
            key.setPrefWidth(120);
            TableColumn<HeaderEntry, String> value = new TableColumn<>("Value");
            value.setCellValueFactory(new PropertyValueFactory<>("val"));
            value.setCellFactory(column -> new SelectableTableCell<>(
                    new TextAreaEditorNodeBuilder(value), value));
            table.getColumns().setAll(key, value);
            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
            ObservableList<HeaderEntry> entries = TableUtils.headersConvert(new LinkedHashMap<>(info));
            table.setItems(entries);
            VBox content = new VBox(8, new Label(info.getOrDefault("CN", "Certificate")), table);
            content.setPrefSize(500, 460);
            dialog.getDialogPane().setContent(content);
            dialog.showAndWait();
        }));
    }

    private void saveCertificate(CertificateConfig config) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(messages.getMessage("cert-export.tooltip"));
        chooser.setInitialFileName(config.getName());
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Certificate", "*.crt", "*.pem"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        File file = chooser.showSaveDialog(importCertBtn.getScene().getWindow());
        if (file == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                String pem = certManager.getCertPEM(config.getId());
                FileUtils.writeByteArrayToFile(file, pem.getBytes(StandardCharsets.UTF_8));
            } catch (Exception error) {
                throw new IllegalStateException(error);
            }
        }, executor).exceptionally(error -> {
            Platform.runLater(() -> showError(error));
            return null;
        });
    }

    private void showError(Throwable error) {
        Throwable cause = error.getCause() == null ? error : error.getCause();
        log.error("Certificate settings operation failed", cause);
        AlertUtils.alertWarning(messages.getMessage("alert.type.warning"),
                Objects.toString(cause.getMessage(), messages.getMessage("alert.msg.error")));
    }

    private record CertificateRow(CertificateConfig config, boolean installed) {
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }
}

package com.catas.wicked.proxy.gui.controller;

import app.supernaut.fx.fxml.FxmlLoaderFactory;
import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.provider.ResourceMessageProvider;
import com.catas.wicked.proxy.service.LocalizationService;
import com.catas.wicked.common.util.AlertUtils;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.stage.Modality;
import javafx.stage.Window;

import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;

@Singleton
public class SettingsDialogCoordinator {

    @Inject private FxmlLoaderFactory loaderFactory;
    @Inject private ApplicationConfig applicationConfig;
    @Inject private ResourceMessageProvider messages;
    @Inject private LocalizationService localization;

    private Dialog<Void> dialog;
    private SettingController controller;
    private boolean discardWithoutPrompt;

    public void open(SettingsTab tab, Window owner) {
        if (dialog == null) {
            initialize(owner);
        }
        controller.beginSession(tab, this::cancel);
        dialog.showAndWait();
    }

    private void cancel() {
        discardWithoutPrompt = true;
        dialog.close();
    }

    private void initialize(Window owner) {
        try {
            Locale locale = applicationConfig.getSettings().getLanguage().getLocale();
            FXMLLoader loader = loaderFactory.get(getClass().getResource("/fxml/setting-page/settings.fxml"));
            loader.setResources(ResourceBundle.getBundle("lang.messages", locale));
            Parent content = loader.load();
            controller = loader.getController();

            dialog = new Dialog<>();
            dialog.setTitle(messages.getMessage("setting-dialog.title"));
            localization.languageProperty().addListener((observable, oldValue, newValue) ->
                    dialog.setTitle(localization.getMessage("setting-dialog.title")));
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(owner);
            DialogPane pane = dialog.getDialogPane();
            pane.setContent(content);
            pane.getButtonTypes().add(ButtonType.CANCEL);
            Node hiddenCancelButton = pane.lookupButton(ButtonType.CANCEL);
            hiddenCancelButton.setVisible(false);
            hiddenCancelButton.setManaged(false);
            pane.setPrefSize(780, 540);
            pane.setMinSize(720, 460);
            pane.getStylesheets().add(Objects.requireNonNull(
                    getClass().getResource("/css/dialog.css")).toExternalForm());
            pane.getStylesheets().add(Objects.requireNonNull(
                    getClass().getResource("/css/app.css")).toExternalForm());
            pane.getStyleClass().add("myDialog");
            dialog.setOnCloseRequest(event -> {
                if (controller.isApplying()) {
                    event.consume();
                } else if (!discardWithoutPrompt && controller.isDirty() && !AlertUtils.confirm(
                        messages.getMessage("setting-dialog.title"),
                        messages.getMessage("settings.discard-confirm"))) {
                    event.consume();
                }
                discardWithoutPrompt = false;
            });
        } catch (Exception error) {
            throw new IllegalStateException("Unable to initialize settings dialog", error);
        }
    }
}

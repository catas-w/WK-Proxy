package com.catas.wicked.proxy.gui.controller;

import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.util.AlertUtils;
import com.catas.wicked.common.worker.ScheduledManager;
import com.catas.wicked.proxy.gui.componet.CertSelectComponent;
import com.catas.wicked.proxy.gui.componet.ProxyTypeLabel;
import com.catas.wicked.proxy.gui.componet.ThrottleTypeLabel;
import com.catas.wicked.proxy.service.settings.ExternalProxySettingService;
import com.catas.wicked.proxy.service.settings.GeneralSettingService;
import com.catas.wicked.proxy.service.settings.ProxySettingService;
import com.catas.wicked.proxy.service.settings.SettingService;
import com.catas.wicked.proxy.service.settings.SslSettingService;
import com.catas.wicked.server.proxy.ProxyServer;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.controls.JFXToggleButton;
import io.micronaut.core.util.CollectionUtils;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import javafx.stage.Window;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Consumer;

@Getter
@Slf4j
@Singleton
// @RequiredArgsConstructor(onConstructor_={@Inject})
public class SettingController implements Initializable {

    public JFXToggleButton exProxyBtn;
    public JFXComboBox<ProxyTypeLabel> proxyComboBox;
    public JFXTextField exProxyHost;
    public JFXTextField exProxyPort;
    public JFXTextField exUsername;
    public JFXTextField exPassword;
    public JFXToggleButton exProxyAuth;
    public Label exUsernameLabel;
    public Label exPasswordLabel;
    public JFXComboBox<Labeled> languageComboBox;
    public TextArea recordExcludeArea;
    public TextArea sysProxyExcludeArea;
    public JFXToggleButton sslBtn;
    public TextArea sslExcludeArea;
    public JFXToggleButton throttleBtn;
    public JFXComboBox<ThrottleTypeLabel> throttleComboBox;

    public Tab generalSettingTab;
    public Tab proxySettingTab;
    public Tab sslSettingTab;
    public Tab externalSettingTab;
    public Tab infoSettingTab;
    public HBox importCertBox;
    public Button importCertBtn;
    public GridPane sslGridPane;
    public GridPane exProxyGridPane;
    @FXML
    private JFXCheckBox sysProxyOnLaunchBtn;
    @FXML
    private JFXButton cancelBtn;
    @FXML
    private TabPane settingTabPane;
    @FXML
    private JFXTextField portField;
    @FXML
    private JFXTextField maxSizeField;
    @FXML
    private JFXButton saveBtn;

    @Inject
    private ApplicationConfig appConfig;
    @Setter
    private ButtonBarController buttonBarController;
    @Inject
    private ProxyServer proxyServer;
    @Inject
    private ScheduledManager scheduledManager;
    @Inject
    private List<SettingService> settingServiceList;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // initServers();
        settingServiceList.forEach(settingService -> settingService.setSettingController(this));
        settingServiceList.forEach(SettingService::init);

        // set icons
        configTabStyle(generalSettingTab, "fas-sliders-h");
        configTabStyle(proxySettingTab, "fas-hat-cowboy");
        configTabStyle(sslSettingTab, "fas-key");
        configTabStyle(externalSettingTab, "fas-monument");
        configTabStyle(infoSettingTab, "fas-info-circle");

        // scroll pane
        for (Tab tab : List.of(generalSettingTab, proxySettingTab, sslSettingTab)) {
            AnchorPane sslAnchorPane = (AnchorPane) tab.getContent();
            Node child = sslAnchorPane.getChildren().get(0);
            if (child instanceof ScrollPane scrollPane) {
                // ScrollPane scrollPane = (ScrollPane) ;
                // sslGridPane.prefWidthProperty().bind(scrollPane.widthProperty());
                scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                scrollPane.setFitToWidth(true);
            }
        }
    }

    private void configTabStyle(Tab tab, String iconCode) {
        if (tab == null) {
            return;
        }

        final String styleClass = "setting-icon-pane";
        FontIcon icon = new FontIcon();
        icon.setIconLiteral(iconCode);
        // icon.setIconSize(36);

        Label label = new Label(tab.getText());

        BorderPane tabPane = new BorderPane();
        tabPane.setPrefWidth(90);
        tabPane.setCenter(icon);
        tabPane.setBottom(label);
        tabPane.getStyleClass().add(styleClass);

        tab.setText(null);
        tab.setGraphic(tabPane);
    }

    @Deprecated
    public void save(ActionEvent event) {
        // valid textFields
        boolean isValidated = true;
        for (Tab tab : settingTabPane.getTabs()) {
            Pane tabContent = (Pane) tab.getContent();
            Set<Node> textFields = tabContent.lookupAll(".jfx-text-field");
            if (isValidated && textFields != null && !textFields.isEmpty()) {
                for (Node textField : textFields) {
                    if (textField instanceof JFXTextField jfxTextField && !jfxTextField.validate()) {
                        isValidated = false;
                        break;
                    }
                }
            }
        }

        if (!isValidated) {
            AlertUtils.alertWarning("Illegal settings!");
            return;
        }

        settingServiceList.forEach(settingService -> settingService.update(appConfig));

        // update config file
        appConfig.updateSettingFile();
        cancel(event);
    }

    @Deprecated
    public void cancel(ActionEvent event) {
        if (event == null) {
            return;
        }
        List<Window> windows = Stage.getWindows().stream().filter(Window::isShowing).filter(Window::isFocused).toList();
        for (Window window : windows) {
            Parent root = window.getScene().getRoot();
            if (root instanceof DialogPane dialogPane) {
                window.hide();
            }
        }
    }

    @Deprecated
    public void apply(ActionEvent event) {
        save(null);
    }

    /**
     * initialize form values
     */
    public void initValues() {
        if (appConfig == null) {
            return;
        }

        settingServiceList.forEach(settingService -> settingService.initValues(appConfig));
    }

    /**
     * reset current page
     */
    public void reset() {
        Tab selectedTab = settingTabPane.getSelectionModel().getSelectedItem();
        List<String> styleList = selectedTab.getStyleClass()
                .stream()
                .filter(style -> style.startsWith("setting-") && !style.startsWith("setting-tab"))
                .toList();
        Class targetServiceType = null;
        switch (styleList.get(0)) {
            case "setting-general" -> targetServiceType = GeneralSettingService.class;
            case "setting-server" -> targetServiceType = ProxySettingService.class;
            case "setting-ssl" -> targetServiceType = SslSettingService.class;
            case "setting-external" -> targetServiceType = ExternalProxySettingService.class;
        }

        if (targetServiceType != null) {
            for (SettingService service : settingServiceList) {
                if (service.getClass().equals(targetServiceType)) {
                    service.initValues(appConfig);
                }
            }
        }
    }

    public void updateThrottleBtn(boolean selected) {
        buttonBarController.updateThrottleBtn(selected);
    }

    public void updateSslBtn(boolean selected) {
        buttonBarController.updateSSlBtn(selected);
    }

    public void setSelectableCert(List<? extends Node> certList) {
        if (CollectionUtils.isEmpty(certList)) {
            return;
        }

        // clean old
        final int startRowIndex = 3;
        sslGridPane.getChildren().remove(importCertBox);
        sslGridPane.getChildren().removeIf(item -> item instanceof CertSelectComponent);

        // add
        int rowIndex = startRowIndex;
        for (Node node : certList) {
            sslGridPane.add(node, 1, rowIndex);
            rowIndex ++;
        }

        sslGridPane.add(importCertBox, 1, rowIndex);
    }

    public void setImportCertEvent(Consumer<ActionEvent> consumer) {
        if (consumer != null) {
            importCertBtn.setOnAction(consumer::accept);
        }
    }

    public void setImportCertBtnStatus(boolean disabled) {
        importCertBtn.setDisable(disabled);
    }
}

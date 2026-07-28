package com.catas.wicked.proxy.gui.controller.settings;

import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.provider.DesktopProvider;
import com.catas.wicked.common.provider.ResourceMessageProvider;
import com.catas.wicked.proxy.service.settings.SettingsDraft;
import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Inject;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import lombok.extern.slf4j.Slf4j;

import java.net.URL;
import java.util.ResourceBundle;

@Slf4j
@Prototype
public class AboutSettingsPageController implements SettingsPageController, Initializable {

    static final String REPOSITORY_URL = "https://github.com/catas-w/HumBird-Proxy/";
    static final String LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.html";
    static final String EMAIL_ADDRESS = "catasw@foxmail.com";
    static final String EMAIL_URI = "mailto:" + EMAIL_ADDRESS;

    @FXML private Label appVersionLabel;
    @FXML private Hyperlink githubLink;
    @FXML private Hyperlink emailLink;
    @FXML private Label licenseLink;

    @Inject private ApplicationConfig applicationConfig;
    @Inject private DesktopProvider desktopProvider;
    @Inject private ResourceMessageProvider resourceMessageProvider;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        appVersionLabel.setText(resourceMessageProvider.getMessage("version.label")
                + " " + applicationConfig.getAppVersion());
        githubLink.setText(REPOSITORY_URL);
        githubLink.setOnAction(event -> openRepository());
        emailLink.setText(EMAIL_ADDRESS);
        emailLink.setOnAction(event -> openEmail());
        licenseLink.setOnMouseClicked(event -> openLicense());
    }

    @Override
    public void load(SettingsDraft draft, Runnable changeListener) {
    }

    @Override
    public boolean validate() {
        return true;
    }

    void openRepository() {
        browse(REPOSITORY_URL);
    }

    void openLicense() {
        browse(LICENSE_URL);
    }

    void openEmail() {
        browse(EMAIL_URI);
    }

    private void browse(String url) {
        try {
            desktopProvider.browseOnLocal(url);
        } catch (Exception error) {
            log.error("Error opening {}", url, error);
        }
    }
}

package com.catas.wicked.proxy.service.settings;

import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.pipeline.MessageQueue;
import com.catas.wicked.common.provider.ResourceMessageProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.awt.Desktop;
import java.net.URI;

@Slf4j
@Singleton
public class AboutSettingService extends AbstractSettingService{

    public static final String REPO_LINK = "https://github.com/catas-w/HumBird-Proxy";
    public static final String X_LINK = "https://x.com/Catas43595337";

    private ResourceMessageProvider resourceMessageProvider;

    @Inject
    public void setResourceMessageProvider(ResourceMessageProvider resourceMessageProvider) {
        this.resourceMessageProvider = resourceMessageProvider;
    }

    @Inject
    public void setApplicationConfig(ApplicationConfig applicationConfig) {
        this.applicationConfig = applicationConfig;
    }

    @Inject
    public void setMessageQueue(MessageQueue messageQueue) {
        this.messageQueue = messageQueue;
    }

    @Override
    public void init() {
        String version = resourceMessageProvider.getMessage("version.label") + " " + applicationConfig.getAppVersion();
        settingController.getAppVersionLabel().setText(version);

        settingController.getGithubLink().setText(REPO_LINK);
        settingController.getGithubLink().setOnAction(event -> {
            try {
                Desktop.getDesktop().browse(new URI(REPO_LINK));
            } catch (Exception e) {
                log.error("Error in opening github link.", e);
            }
        });

        settingController.getTwitterLink().setText(X_LINK);
        settingController.getTwitterLink().setOnAction(event -> {
            try {
                Desktop.getDesktop().browse(new URI(X_LINK));
            } catch (Exception e) {
                log.error("Error in opening github link.", e);
            }
        });
    }

    @Override
    public void initValues(ApplicationConfig appConfig) {


    }
}

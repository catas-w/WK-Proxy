package com.catas.wicked.proxy.service;

import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.config.Settings;
import com.catas.wicked.common.constant.LanguagePreset;
import com.catas.wicked.common.factory.MessageSourceFactory;
import javafx.beans.property.SimpleStringProperty;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;

public class LocalizationServiceTest {

    private Locale originalLocale;

    @Before
    public void setUp() {
        originalLocale = Locale.getDefault();
    }

    @After
    public void tearDown() {
        Locale.setDefault(originalLocale);
        MessageSourceFactory.setCurrentLocal(originalLocale);
    }

    @Test
    public void boundTextChangesWithoutReplacingTheProperty() {
        ApplicationConfig config = config(LanguagePreset.ENGLISH);
        LocalizationService service = new LocalizationService(config);
        SimpleStringProperty text = new SimpleStringProperty();

        service.bind(text, "cancel.label");
        assertEquals("Cancel", text.get());

        service.switchLanguage(LanguagePreset.CHINESE);
        assertEquals("取消", text.get());

        service.switchLanguage(LanguagePreset.ENGLISH);
        assertEquals("Cancel", text.get());
    }

    @Test
    public void boundTextTracksResourceKeyAndLanguageChanges() {
        LocalizationService service = new LocalizationService(config(LanguagePreset.ENGLISH));
        SimpleStringProperty key = new SimpleStringProperty("payload.label");
        SimpleStringProperty text = new SimpleStringProperty();

        service.bind(text, key);
        assertEquals("Payload", text.get());

        key.set("content.label");
        assertEquals("Content", text.get());

        service.switchLanguage(LanguagePreset.CHINESE);
        assertEquals("内容", text.get());

        key.set("query-params.label");
        assertEquals("查询参数", text.get());
    }

    @Test
    public void formatsMessagesAndFallsBackToTheKeyWhenMissing() {
        LocalizationService service = new LocalizationService(config(LanguagePreset.ENGLISH));

        assertEquals("Version 2.0.1", service.getMessage("version-value.label", "2.0.1"));
        assertEquals("missing.localization.key", service.getMessage("missing.localization.key"));
    }

    private ApplicationConfig config(LanguagePreset language) {
        ApplicationConfig config = new ApplicationConfig();
        Settings settings = new Settings();
        settings.setLanguage(language);
        config.setSettings(settings);
        return config;
    }
}

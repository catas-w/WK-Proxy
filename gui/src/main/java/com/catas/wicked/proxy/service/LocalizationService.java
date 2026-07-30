package com.catas.wicked.proxy.service;

import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.constant.LanguagePreset;
import com.catas.wicked.common.factory.MessageSourceFactory;
import jakarta.inject.Singleton;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.StringProperty;
import lombok.extern.slf4j.Slf4j;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

@Slf4j
@Singleton
public class LocalizationService {

    private static final String BUNDLE_NAME = "lang.messages";

    private final ObjectProperty<LanguagePreset> language;

    public LocalizationService(ApplicationConfig applicationConfig) {
        LanguagePreset initialLanguage = applicationConfig.getSettings().getLanguage();
        language = new SimpleObjectProperty<>(
                initialLanguage == null ? LanguagePreset.ENGLISH : initialLanguage);
    }

    public LanguagePreset getLanguage() {
        return language.get();
    }

    public ReadOnlyObjectProperty<LanguagePreset> languageProperty() {
        return language;
    }

    public String getMessage(String key, Object... arguments) {
        String pattern = lookup(key, getLanguage().getLocale());
        return arguments == null || arguments.length == 0
                ? pattern
                : new MessageFormat(pattern, getLanguage().getLocale()).format(arguments);
    }

    public StringBinding binding(String key, Object... arguments) {
        return Bindings.createStringBinding(
                () -> getMessage(key, arguments), language);
    }

    public void bind(StringProperty property, String key, Object... arguments) {
        property.bind(binding(key, arguments));
    }

    public void switchLanguage(LanguagePreset newLanguage) {
        LanguagePreset target = newLanguage == null ? LanguagePreset.ENGLISH : newLanguage;
        Locale locale = target.getLocale();
        Locale.setDefault(locale);
        MessageSourceFactory.setCurrentLocal(locale);
        language.set(target);
    }

    private String lookup(String key, Locale locale) {
        if (key == null || key.isBlank()) {
            return "";
        }
        try {
            return ResourceBundle.getBundle(BUNDLE_NAME, locale).getString(key);
        } catch (MissingResourceException error) {
            if (!Locale.ENGLISH.equals(locale)) {
                try {
                    log.warn("Missing localized message '{}' for {}, using English", key, locale);
                    return ResourceBundle.getBundle(BUNDLE_NAME, Locale.ENGLISH).getString(key);
                } catch (MissingResourceException ignored) {
                    // Report the missing key once below.
                }
            }
            log.warn("Missing localized message '{}'", key);
            return key;
        }
    }
}

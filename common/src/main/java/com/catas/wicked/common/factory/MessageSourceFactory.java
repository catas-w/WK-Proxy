package com.catas.wicked.common.factory;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parallel;
import io.micronaut.context.i18n.ResourceBundleMessageSource;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

@Parallel
@Factory
public class MessageSourceFactory {

    @Bean
    @Singleton
    public ResourceBundleMessageSource messageSource() {
        return new ResourceBundleMessageSource("lang.messages");
    }

    private ResourceBundle getResourceBundle(Locale locale) {
        try {
            return ResourceBundle.getBundle("lang.messages", locale, new UTF8Control());
        } catch (Exception e) {
            return null;
        }
    }

    // Custom Control class to load properties files as UTF-8
    public static class UTF8Control extends ResourceBundle.Control {
        @Override
        public ResourceBundle newBundle(String baseName, Locale locale, String format, ClassLoader loader, boolean reload)
                throws IOException {
            String bundleName = toBundleName(baseName, locale);
            String resourceName = toResourceName(bundleName, "properties");

            try (var stream = loader.getResourceAsStream(resourceName)) {
                if (stream != null) {
                    // Load with UTF-8 encoding
                    try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                        return new PropertyResourceBundle(reader);
                    }
                }
            }
            return null;
        }
    }
}

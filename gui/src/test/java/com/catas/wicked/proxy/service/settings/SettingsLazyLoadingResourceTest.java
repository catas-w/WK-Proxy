package com.catas.wicked.proxy.service.settings;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class SettingsLazyLoadingResourceTest {

    @Test
    public void buttonBarDoesNotEagerlyIncludeSettings() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/fxml/button-bar.fxml")) {
            assertNotNull(input);
            String fxml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertFalse(fxml.contains("setting-page/settings.fxml"));
        }
    }

    @Test
    public void everyLazySettingsPageIsPackaged() {
        for (String page : new String[]{"settings", "general", "proxy", "ssl", "external-proxy", "about"}) {
            assertNotNull(page, getClass().getResource("/fxml/setting-page/" + page + ".fxml"));
        }
    }
}

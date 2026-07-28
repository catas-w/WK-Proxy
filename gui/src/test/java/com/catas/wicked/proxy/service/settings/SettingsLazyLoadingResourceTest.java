package com.catas.wicked.proxy.service.settings;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void proxyPageContainsInlinePortUnavailableLabel() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/fxml/setting-page/proxy.fxml")) {
            assertNotNull(input);
            String fxml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(fxml.contains("fx:id=\"portUnavailableLabel\""));
            assertTrue(fxml.contains("styleClass=\"port-unavailable-label\""));
            assertTrue(fxml.contains("managed=\"false\" visible=\"false\""));
        }
    }

    @Test
    public void aboutPageContainsCompleteProductInformation() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/fxml/setting-page/about.fxml")) {
            assertNotNull(input);
            String fxml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(fxml.contains("wk-proxy.2.png"));
            assertTrue(fxml.contains("text=\"WK Proxy\""));
            assertTrue(fxml.contains("text=\"Http debug proxy tool.\""));
            assertTrue(fxml.contains("fx:id=\"appVersionLabel\""));
            assertTrue(fxml.contains("fx:id=\"licenseLink\""));
            assertTrue(fxml.contains("fx:id=\"githubLink\""));
            assertTrue(fxml.contains("fx:id=\"emailLink\""));
            assertTrue(fxml.contains("text=\"%email-link.label\""));
        }
    }
}

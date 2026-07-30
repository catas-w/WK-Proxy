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
        for (String page : new String[]{"settings", "general", "proxy", "ssl", "about"}) {
            assertNotNull(page, getClass().getResource("/fxml/setting-page/" + page + ".fxml"));
        }
    }

    @Test
    public void settingsShellUsesSidebarNavigationInsteadOfTopTabs() throws Exception {
        String fxml = read("/fxml/setting-page/settings.fxml");
        assertFalse(fxml.contains("JFXTabPane"));
        assertTrue(fxml.contains("fx:id=\"generalNavigationButton\""));
        assertTrue(fxml.contains("fx:id=\"proxyNavigationButton\""));
        assertTrue(fxml.contains("fx:id=\"sslNavigationButton\""));
        assertTrue(fxml.contains("fx:id=\"aboutNavigationButton\""));
        assertTrue(fxml.contains("styleClass=\"settings-sidebar\""));
        assertTrue(fxml.contains("fx:id=\"pageHost\""));
    }

    @Test
    public void settingsLayoutReservesSpaceForLocalizedLabels() throws Exception {
        String shell = read("/fxml/setting-page/settings.fxml");
        assertTrue(shell.contains("minHeight=\"460.0\" minWidth=\"720.0\""));
        assertTrue(shell.contains("prefHeight=\"540.0\" prefWidth=\"780.0\""));
        assertTrue(shell.contains("prefWidth=\"195.0\" minWidth=\"195.0\" maxWidth=\"195.0\""));

        String css = read("/css/setting-page.css");
        assertTrue(css.contains("-fx-pref-width: 175px;"));
        assertTrue(css.contains("-fx-max-width: infinity;"));
        assertTrue(css.contains("-fx-text-alignment: right;"));
        assertTrue(css.contains("-fx-wrap-text: true;"));

        assertLabelColumnWidth("/fxml/setting-page/general.fxml", 1);
        assertLabelColumnWidth("/fxml/setting-page/proxy.fxml", 1);
        assertLabelColumnWidth("/fxml/setting-page/ssl.fxml", 1);
        assertLabelColumnWidth("/fxml/setting-page/external-proxy.fxml", 3);
    }

    @Test
    public void proxyPageIncludesProgressiveUpstreamProxySection() throws Exception {
        String proxyFxml = read("/fxml/setting-page/proxy.fxml");
        String upstreamFxml = read("/fxml/setting-page/external-proxy.fxml");
        assertTrue(proxyFxml.contains("fx:id=\"upstreamProxySection\""));
        assertTrue(proxyFxml.contains("source=\"external-proxy.fxml\""));
        assertTrue(upstreamFxml.contains("fx:id=\"exProxyDetails\""));
        assertTrue(upstreamFxml.contains("fx:id=\"exProxyAuthDetails\""));
    }

    @Test
    public void sslPageShowsCertificateStatusSection() throws Exception {
        String fxml = read("/fxml/setting-page/ssl.fxml");
        assertTrue(fxml.contains("text=\"%certificate-management-sep.label\""));
        assertTrue(fxml.contains("fx:id=\"importCertBtn\""));
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
        String fxml = read("/fxml/setting-page/about.fxml");
        assertTrue(fxml.contains("wk-proxy.2.png"));
        assertTrue(fxml.contains("text=\"WK Proxy\""));
        assertTrue(fxml.contains("text=\"Http debug proxy tool.\""));
        assertTrue(fxml.contains("fx:id=\"appVersionLabel\""));
        assertTrue(fxml.contains("fx:id=\"licenseLink\""));
        assertTrue(fxml.contains("fx:id=\"githubLink\""));
        assertTrue(fxml.contains("fx:id=\"emailLink\""));
        assertTrue(fxml.contains("text=\"%email-link.label\""));
    }

    private String read(String resource) throws Exception {
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void assertLabelColumnWidth(String resource, int expectedCount) throws Exception {
        String fxml = read(resource);
        String constraint = "<ColumnConstraints minWidth=\"205.0\" prefWidth=\"205.0\" maxWidth=\"205.0\"/>";
        assertFalse(fxml.contains("minWidth=\"130.0\" prefWidth=\"130.0\""));
        assertTrue(resource, fxml.split(java.util.regex.Pattern.quote(constraint), -1).length - 1
                == expectedCount);
    }
}

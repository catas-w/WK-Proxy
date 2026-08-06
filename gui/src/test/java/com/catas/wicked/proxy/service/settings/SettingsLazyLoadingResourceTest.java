package com.catas.wicked.proxy.service.settings;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.ResourceBundle;

import static org.junit.Assert.assertEquals;
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
    public void settingsShellProvidesCancelApplyAndOkActions() throws Exception {
        String fxml = read("/fxml/setting-page/settings.fxml");
        int cancelIndex = fxml.indexOf("fx:id=\"cancelButton\"");
        int applyIndex = fxml.indexOf("fx:id=\"applyButton\"");
        int okIndex = fxml.indexOf("fx:id=\"okButton\"");

        assertTrue(cancelIndex >= 0);
        assertTrue(applyIndex > cancelIndex);
        assertTrue(okIndex > applyIndex);
        assertTrue(fxml.contains("text=\"%ok.label\""));
        assertTrue(fxml.contains("defaultButton=\"true\""));
        assertEquals("OK", ResourceBundle.getBundle("lang.messages", Locale.ENGLISH).getString("ok.label"));
        assertEquals("确定", ResourceBundle.getBundle("lang.messages", Locale.SIMPLIFIED_CHINESE).getString("ok.label"));
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
    public void proxyPageProvidesPortStepperControls() throws Exception {
        String fxml = read("/fxml/setting-page/proxy.fxml");
        assertTrue(fxml.contains("fx:id=\"portIncrementButton\""));
        assertTrue(fxml.contains("fx:id=\"portDecrementButton\""));
        assertTrue(fxml.contains("onAction=\"#incrementPort\""));
        assertTrue(fxml.contains("onAction=\"#decrementPort\""));
        assertTrue(fxml.contains("focusTraversable=\"false\""));
    }

    @Test
    public void settingsHelpTooltipsOpenWithoutDelayAndHaveScopedHoverStyle() throws Exception {
        String[] pages = {"general", "proxy", "ssl", "external-proxy"};
        int[] expectedTooltipCounts = {2, 1, 1, 1};
        for (int index = 0; index < pages.length; index++) {
            String page = pages[index];
            String fxml = read("/fxml/setting-page/" + page + ".fxml");
            assertFalse(page, fxml.contains("showDelay=\"500ms\""));
            assertFalse(page, fxml.contains("<toolztip>"));
            assertEquals(page, expectedTooltipCounts[index], countOccurrences(fxml, "showDelay=\"0ms\""));
            assertEquals(page, expectedTooltipCounts[index], countOccurrences(fxml, "styleClass=\"tooltip-icon-host\""));
        }

        String css = read("/css/setting-page.css");
        assertTrue(css.contains(".settings-root .tooltip-icon-host:hover .tooltip-icon"));
        assertTrue(css.contains("-fx-icon-color: -settings-accent;"));
    }

    @Test
    public void portStepperUsesLargeIconsWithoutInternalDivider() throws Exception {
        String css = read("/css/setting-page.css");
        assertTrue(css.contains("-fx-icon-size: 14px;"));
        assertTrue(css.contains("-fx-border-width: 1px 1px 0 1px;"));
        assertTrue(css.contains("-fx-border-width: 0 1px 1px 1px;"));
    }

    @Test
    public void aboutPageContainsCompleteProductInformation() throws Exception {
        String fxml = read("/fxml/setting-page/about.fxml");
        assertTrue(fxml.contains("wk-proxy.2.png"));
        assertTrue(fxml.contains("text=\"%app-name.label\""));
        assertTrue(fxml.contains("text=\"%about-description.label\""));
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

    private int countOccurrences(String value, String needle) {
        return value.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }
}

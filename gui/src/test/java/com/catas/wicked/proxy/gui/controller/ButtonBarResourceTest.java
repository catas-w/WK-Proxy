package com.catas.wicked.proxy.gui.controller;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ButtonBarResourceTest {

    @Test
    public void fxmlContainsDualModeLayoutAndStatusBadges() throws Exception {
        String fxml = read("/fxml/button-bar.fxml");

        assertTrue(fxml.contains("fx:id=\"buttonBarRoot\""));
        assertTrue(fxml.contains("minWidth=\"72.0\""));
        assertTrue(fxml.contains("prefHeight=\"56.0\""));
        assertTrue(fxml.contains("fx:id=\"recordingBadge\""));
        assertTrue(fxml.contains("fx:id=\"sslWarningBadge\""));
        assertTrue(fxml.contains("fx:id=\"settingsUpdateBadge\""));
        assertTrue(fxml.contains("fx:id=\"menuUpdateBadge\""));
        assertTrue(fxml.contains("labelText=\"%setting-btn.label\""));
    }

    @Test
    public void stylesheetDefinesAllInteractiveStates() throws Exception {
        String css = read("/css/button-bar.css");

        assertTrue(css.contains(".main-button-bar:compact"));
        assertTrue(css.contains(".buttonbar-control:selected"));
        assertTrue(css.contains(".buttonbar-control:focused"));
        assertTrue(css.contains(".buttonbar-control:suspended"));
        assertTrue(css.contains(".buttonbar-control:warning"));
        assertTrue(css.contains(".buttonbar-control:disabled"));
        assertEquals("transparent", propertyValue(css, ".main-button-bar", "-fx-background-color"));
        assertEquals("transparent", propertyValue(css, ".main-button-bar .buttonbar-control:selected",
                "-fx-background-color"));
    }

    private String propertyValue(String css, String selector, String property) {
        Pattern rulePattern = Pattern.compile(Pattern.quote(selector) + "\\s*\\{([^}]*)}");
        Matcher ruleMatcher = rulePattern.matcher(css);
        assertTrue("Missing CSS rule: " + selector, ruleMatcher.find());

        Pattern propertyPattern = Pattern.compile(Pattern.quote(property) + "\\s*:\\s*([^;]+)");
        Matcher propertyMatcher = propertyPattern.matcher(ruleMatcher.group(1));
        assertTrue("Missing CSS property " + property + " in " + selector, propertyMatcher.find());
        return propertyMatcher.group(1).trim();
    }

    private String read(String path) throws Exception {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertNotNull(path, input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

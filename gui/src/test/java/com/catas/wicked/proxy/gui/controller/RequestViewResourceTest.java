package com.catas.wicked.proxy.gui.controller;

import org.junit.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class RequestViewResourceTest {

    @Test
    public void viewSwitcherUsesPillLayoutWithInternalDividersOnly() throws Exception {
        String css = read("/css/request-view-pane.css");

        assertTrue(css.contains(".request-view-switcher"));
        assertTrue(css.contains("-fx-background-radius: 14px;"));
        assertTrue(css.contains("#applicationViewToggleNode"));
        assertTrue(css.contains("-fx-background-radius: 14px 0 0 14px;"));
        assertTrue(css.contains("#listViewToggleNode"));
        assertTrue(css.contains("-fx-background-radius: 0 14px 14px 0;"));
        assertTrue(css.contains("#treeViewToggleNode,\n#listViewToggleNode"));
        assertTrue(css.contains("-fx-border-color: transparent transparent transparent #c3c3c3;"));
        assertTrue(css.contains("-fx-border-width: 0 0 0 1px;"));
        assertFalse(css.contains("transparent transparent transparent rgba(133, 133, 133, 0.45)"));
    }

    @Test
    public void requestStatusStylesUseStableThreeStateIndicators() throws Exception {
        String css = read("/css/request-view-pane.css");
        String english = read("/lang/messages_en.properties");
        String chinese = read("/lang/messages_zh_CN.properties");

        assertTrue(css.contains(".request-status-indicator.pending"));
        assertTrue(css.contains(".request-status-indicator.success"));
        assertTrue(css.contains(".request-status-indicator.failed"));
        assertTrue(css.contains(".request-failure-count"));
        assertTrue(english.contains("request-status.waiting-response=Waiting for response"));
        assertTrue(english.contains("request-status.failed-count=Failed requests: {0}"));
        assertTrue(chinese.contains("request-status.failed-count="));
    }

    @Test
    public void requestStatusIconIsEnabledByDefault() throws Exception {
        Field field = RequestViewController.class.getDeclaredField("SHOW_REQUEST_STATUS_ICON");
        field.setAccessible(true);

        assertTrue(field.getBoolean(null));
    }

    @Test
    public void groupFailureCountIsHiddenByDefault() throws Exception {
        Field field = RequestViewController.class.getDeclaredField("SHOW_GROUP_FAILURE_COUNT");
        field.setAccessible(true);

        assertFalse(field.getBoolean(null));
    }

    private String read(String path) throws Exception {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertNotNull(path, input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

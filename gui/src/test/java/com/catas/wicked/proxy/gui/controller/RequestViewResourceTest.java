package com.catas.wicked.proxy.gui.controller;

import org.junit.Test;

import java.io.InputStream;
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

    private String read(String path) throws Exception {
        try (InputStream input = getClass().getResourceAsStream(path)) {
            assertNotNull(path, input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

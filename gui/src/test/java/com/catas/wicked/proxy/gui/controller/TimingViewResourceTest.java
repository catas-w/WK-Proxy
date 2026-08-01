package com.catas.wicked.proxy.gui.controller;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TimingViewResourceTest {

    @Test
    public void timingRowsUseExplicitControls() throws IOException {
        String fxml = readDetailFxml();

        assertTrue(fxml.contains("fx:id=\"requestTimeSplit\""));
        assertTrue(fxml.contains("fx:id=\"waitingTimeSplit\""));
        assertTrue(fxml.contains("fx:id=\"responseTimeSplit\""));
        assertTrue(fxml.contains("fx:id=\"totalTimeBar\""));
        assertTrue(fxml.contains("fx:id=\"requestDurationLabel\""));
        assertTrue(fxml.contains("fx:id=\"waitingDurationLabel\""));
        assertTrue(fxml.contains("fx:id=\"responseDurationLabel\""));
        assertTrue(fxml.contains("fx:id=\"totalDurationLabel\""));
    }

    @Test
    public void mainDetailTabsUseFixedWidthWithoutPaddedLabels() throws IOException {
        String fxml = readDetailFxml();

        assertTrue(fxml.contains("fx:id=\"mainTabPane\""));
        assertTrue(fxml.contains("tabMinWidth=\"104.0\""));
        assertTrue(fxml.contains("tabMaxWidth=\"104.0\""));
        assertTrue(fxml.contains("text=\"Overview\""));
        assertTrue(fxml.contains("text=\"Request\""));
        assertTrue(fxml.contains("text=\"Response\""));
        assertTrue(fxml.contains("text=\"Timing\""));
        assertFalse(fxml.contains("text=\"  Overview  \""));
    }

    private String readDetailFxml() throws IOException {
        try (var stream = getClass().getResourceAsStream("/fxml/detail-tab-pane.fxml")) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

package com.catas.wicked.proxy.gui.controller;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertTrue;

public class TimingViewResourceTest {

    @Test
    public void timingRowsUseExplicitControls() throws IOException {
        String fxml;
        try (var stream = getClass().getResourceAsStream("/fxml/detail-tab-pane.fxml")) {
            fxml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(fxml.contains("fx:id=\"requestTimeSplit\""));
        assertTrue(fxml.contains("fx:id=\"waitingTimeSplit\""));
        assertTrue(fxml.contains("fx:id=\"responseTimeSplit\""));
        assertTrue(fxml.contains("fx:id=\"totalTimeBar\""));
        assertTrue(fxml.contains("fx:id=\"requestDurationLabel\""));
        assertTrue(fxml.contains("fx:id=\"waitingDurationLabel\""));
        assertTrue(fxml.contains("fx:id=\"responseDurationLabel\""));
        assertTrue(fxml.contains("fx:id=\"totalDurationLabel\""));
    }
}

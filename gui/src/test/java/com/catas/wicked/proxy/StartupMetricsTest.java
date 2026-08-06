package com.catas.wicked.proxy;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StartupMetricsTest {

    @Test
    public void formatsAllStartupPhasesInMilliseconds() {
        StartupMetrics.StartupTiming timing = new StartupMetrics.StartupTiming(
                12.25, 34.5, 5.75, 8.0, 60.5);

        assertEquals(
                "Startup timing: launcher/DI=12.3 ms, FXML/CSS=34.5 ms, stage=5.8 ms, first-pulse=8.0 ms, total-from-main=60.5 ms",
                StartupMetrics.format(timing));
    }
}

package com.catas.wicked.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SettingsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void applicationRequestCountIsShownByDefault() throws Exception {
        assertTrue(new Settings().isShowApplicationRequestCount());
        assertTrue(objectMapper.readValue("{}", Settings.class).isShowApplicationRequestCount());
    }

    @Test
    public void applicationRequestCountCanBeDisabled() throws Exception {
        Settings settings = objectMapper.readValue(
                "{\"showApplicationRequestCount\":false}", Settings.class);

        assertFalse(settings.isShowApplicationRequestCount());
    }
}

package com.catas.wicked.proxy.gui.componet;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class RequestLeafLayoutTest {

    @Test
    public void placesStatusBeforeMethodWhenEnabled() {
        assertEquals(List.of("status", "method", "path"),
                RequestLeafLayout.elements(true, "status", "method", "path"));
    }

    @Test
    public void removesStatusAndItsSlotWhenDisabled() {
        assertEquals(List.of("method", "path"),
                RequestLeafLayout.elements(false, "status", "method", "path"));
    }
}

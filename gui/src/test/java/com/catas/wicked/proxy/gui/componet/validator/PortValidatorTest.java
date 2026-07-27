package com.catas.wicked.proxy.gui.componet.validator;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PortValidatorTest {

    @Test
    public void acceptsValidPortRange() {
        assertEquals(Integer.valueOf(1), PortValidator.parse("1"));
        assertEquals(Integer.valueOf(65535), PortValidator.parse("65535"));
    }

    @Test
    public void rejectsInvalidPorts() {
        assertNull(PortValidator.parse(""));
        assertNull(PortValidator.parse("abc"));
        assertNull(PortValidator.parse("-1"));
        assertNull(PortValidator.parse("0"));
        assertNull(PortValidator.parse("65536"));
    }
}

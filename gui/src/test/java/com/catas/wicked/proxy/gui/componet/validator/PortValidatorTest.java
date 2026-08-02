package com.catas.wicked.proxy.gui.componet.validator;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void allowsOnlyUpToFiveAsciiDigitsWhileEditing() {
        assertTrue(PortValidator.isAllowedInput(""));
        assertTrue(PortValidator.isAllowedInput("0"));
        assertTrue(PortValidator.isAllowedInput("65535"));

        assertFalse(PortValidator.isAllowedInput("123456"));
        assertFalse(PortValidator.isAllowedInput("12a"));
        assertFalse(PortValidator.isAllowedInput(" 12"));
        assertFalse(PortValidator.isAllowedInput("-1"));
        assertFalse(PortValidator.isAllowedInput("1.5"));
        assertFalse(PortValidator.isAllowedInput("１２"));
        assertFalse(PortValidator.isAllowedInput(null));
    }

    @Test
    public void stepsValidPortsWithinRange() {
        assertEquals(Integer.valueOf(9967), PortValidator.step("9966", 1));
        assertEquals(Integer.valueOf(9965), PortValidator.step("9966", -1));
    }

    @Test
    public void doesNotStepPastBoundariesOrInvalidInput() {
        assertNull(PortValidator.step("1", -1));
        assertNull(PortValidator.step("65535", 1));
        assertNull(PortValidator.step("", 1));
        assertNull(PortValidator.step("invalid", -1));
    }
}

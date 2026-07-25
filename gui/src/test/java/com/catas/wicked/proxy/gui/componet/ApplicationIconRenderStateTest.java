package com.catas.wicked.proxy.gui.componet;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ApplicationIconRenderStateTest {

    @Test
    public void reloadsWhenSameApplicationOnlyShowsFallbackIcon() {
        assertTrue(ApplicationIconRenderState.shouldLoad("chrome", "chrome", false));
    }

    @Test
    public void keepsResolvedIconForSameApplication() {
        assertFalse(ApplicationIconRenderState.shouldLoad("chrome", "chrome", true));
    }

    @Test
    public void reloadsWhenCellIsReusedForAnotherApplication() {
        assertTrue(ApplicationIconRenderState.shouldLoad("chrome", "safari", true));
    }
}

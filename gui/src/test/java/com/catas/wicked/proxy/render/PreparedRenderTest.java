package com.catas.wicked.proxy.render;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PreparedRenderTest {

    @Test
    public void rejectsApplyingControlsOutsideJavaFxThread() {
        AtomicBoolean applied = new AtomicBoolean();
        PreparedRender render = new PreparedRender("request-a", false, () -> applied.set(true));

        try {
            render.apply(() -> false);
            fail("Expected applying outside the JavaFX thread to fail");
        } catch (IllegalStateException expected) {
            // Expected.
        } finally {
            assertFalse(applied.get());
        }
    }

    @Test
    public void appliesPreparedActionOnJavaFxThread() {
        AtomicBoolean applied = new AtomicBoolean();
        PreparedRender render = new PreparedRender("request-a", false, () -> applied.set(true));

        render.apply(() -> true);

        assertTrue(applied.get());
    }
}

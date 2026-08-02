package com.catas.wicked.proxy.gui.componet.richtext;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TextRenderRevisionTest {

    @Test
    public void onlyLatestRevisionRemainsCurrent() {
        TextRenderRevision revision = new TextRenderRevision();

        long first = revision.next();
        long second = revision.next();

        assertFalse(revision.isCurrent(first));
        assertTrue(revision.isCurrent(second));
    }
}

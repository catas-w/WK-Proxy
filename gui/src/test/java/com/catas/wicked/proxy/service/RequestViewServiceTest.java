package com.catas.wicked.proxy.service;

import com.catas.wicked.common.bean.message.RenderMessage;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RequestViewServiceTest {

    @Test
    public void acceptsOnlyRenderForCurrentSelection() {
        RenderMessage selected = new RenderMessage("request-a", RenderMessage.Tab.REQUEST, 3);

        assertTrue(RequestViewService.matchesSelection(selected, "request-a", 3));
        assertFalse(RequestViewService.matchesSelection(selected, "request-b", 3));
        assertFalse(RequestViewService.matchesSelection(selected, null, 3));
        assertFalse(RequestViewService.matchesSelection(selected, "request-a", 4));
    }

    @Test
    public void acceptsEmptyRenderOnlyAfterSelectionIsCleared() {
        RenderMessage empty = new RenderMessage(RenderMessage.EMPTY_MSG, RenderMessage.Tab.OVERVIEW, 2);

        assertTrue(RequestViewService.matchesSelection(empty, null, 2));
        assertFalse(RequestViewService.matchesSelection(empty, "request-a", 2));
    }
}

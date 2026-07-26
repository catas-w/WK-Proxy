package com.catas.wicked.common.bean.message;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RenderMessageTest {

    @Test
    public void applicationSelectionsAreOverviewOnly() {
        RenderMessage application = new RenderMessage(
                RenderMessage.APPLICATION_MSG + "browser", RenderMessage.Tab.OVERVIEW);
        RenderMessage host = new RenderMessage(
                RenderMessage.APPLICATION_HOST_MSG + "browser\u0000example.test", RenderMessage.Tab.OVERVIEW);

        assertTrue(application.isApplication());
        assertFalse(application.isApplicationHost());
        assertTrue(application.isApplicationGroup());
        assertTrue(host.isApplicationHost());
        assertFalse(host.isApplication());
        assertTrue(host.isApplicationGroup());
        assertTrue(RenderMessage.isOverviewOnly(application.getRequestId()));
        assertTrue(RenderMessage.isOverviewOnly(host.getRequestId()));
    }

    @Test
    public void ordinaryRequestIsNotOverviewOnly() {
        RenderMessage request = new RenderMessage("request-1", RenderMessage.Tab.OVERVIEW);

        assertFalse(request.isApplicationGroup());
        assertFalse(RenderMessage.isOverviewOnly(request.getRequestId()));
    }
}

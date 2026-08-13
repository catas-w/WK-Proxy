package com.catas.wicked.proxy.gui.componet;

import com.catas.wicked.common.bean.RequestCell;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RequestViewTreeCellLayoutTest {

    @Test
    public void reattachesGraphicWhenCrossingApplicationRowBoundary() {
        assertTrue(RequestTreeRowLayout.requiresGraphicReattach(null, RequestCell.NodeType.APPLICATION));
        assertTrue(RequestTreeRowLayout.requiresGraphicReattach(
                RequestCell.NodeType.HOST, RequestCell.NodeType.APPLICATION));
        assertTrue(RequestTreeRowLayout.requiresGraphicReattach(
                RequestCell.NodeType.APPLICATION, RequestCell.NodeType.REQUEST));

        assertFalse(RequestTreeRowLayout.requiresGraphicReattach(
                RequestCell.NodeType.APPLICATION, RequestCell.NodeType.APPLICATION));
        assertFalse(RequestTreeRowLayout.requiresGraphicReattach(
                RequestCell.NodeType.HOST, RequestCell.NodeType.REQUEST));
        assertFalse(RequestTreeRowLayout.requiresGraphicReattach(null, RequestCell.NodeType.HOST));
    }
}

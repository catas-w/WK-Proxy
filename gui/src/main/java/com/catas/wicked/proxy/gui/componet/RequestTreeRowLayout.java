package com.catas.wicked.proxy.gui.componet;

import com.catas.wicked.common.bean.RequestCell;

final class RequestTreeRowLayout {

    private RequestTreeRowLayout() {
    }

    static boolean requiresGraphicReattach(RequestCell.NodeType previous, RequestCell.NodeType current) {
        boolean previousApplication = previous == RequestCell.NodeType.APPLICATION;
        boolean currentApplication = current == RequestCell.NodeType.APPLICATION;
        return (currentApplication && previous == null)
                || (previous != null && previousApplication != currentApplication);
    }
}

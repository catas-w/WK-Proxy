package com.catas.wicked.proxy.message;

import com.catas.wicked.common.bean.RequestCell;
import com.catas.wicked.common.bean.message.BaseMessage;
import lombok.Getter;

/** Internal GUI message for deleting an item from the application view. */
@Getter
final class ApplicationDeleteMessage extends BaseMessage {

    private final RequestCell.NodeType nodeType;
    private final String nodeKey;

    ApplicationDeleteMessage(RequestCell.NodeType nodeType, String nodeKey) {
        this.nodeType = nodeType;
        this.nodeKey = nodeKey;
    }
}

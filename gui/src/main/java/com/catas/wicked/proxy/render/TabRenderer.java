package com.catas.wicked.proxy.render;

import com.catas.wicked.common.bean.message.RenderMessage;

public interface TabRenderer {

    PreparedRender prepare(RenderMessage renderMsg);
}

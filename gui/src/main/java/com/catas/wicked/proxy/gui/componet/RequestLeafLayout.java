package com.catas.wicked.proxy.gui.componet;

import java.util.List;

/** Defines the shared request-leaf order used by tree and list cells. */
final class RequestLeafLayout {

    private RequestLeafLayout() {
    }

    static <T> List<T> elements(boolean showStatus, T status, T method, T path) {
        return showStatus ? List.of(status, method, path) : List.of(method, path);
    }
}

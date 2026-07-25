package com.catas.wicked.proxy.gui.componet;

import java.util.Objects;

final class ApplicationIconRenderState {

    private ApplicationIconRenderState() {
    }

    static boolean shouldLoad(String applicationKey, String displayedApplicationIconKey, boolean imageVisible) {
        return !Objects.equals(applicationKey, displayedApplicationIconKey) || !imageVisible;
    }
}

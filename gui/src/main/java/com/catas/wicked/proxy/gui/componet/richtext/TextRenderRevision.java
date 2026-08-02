package com.catas.wicked.proxy.gui.componet.richtext;

import java.util.concurrent.atomic.AtomicLong;

final class TextRenderRevision {

    private final AtomicLong value = new AtomicLong();

    long next() {
        return value.incrementAndGet();
    }

    boolean isCurrent(long revision) {
        return revision == value.get();
    }
}

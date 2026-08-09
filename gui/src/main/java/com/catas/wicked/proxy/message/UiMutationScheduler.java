package com.catas.wicked.proxy.message;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Drops queued JavaFX mutations that belong to a replaced request-tree generation. */
final class UiMutationScheduler {

    private final Consumer<Runnable> delegate;
    private final AtomicLong generation = new AtomicLong();

    UiMutationScheduler(Consumer<Runnable> delegate) {
        this.delegate = delegate;
    }

    Consumer<Runnable> nextSession() {
        long sessionGeneration = generation.incrementAndGet();
        return action -> delegate.accept(() -> {
            if (generation.get() == sessionGeneration) {
                action.run();
            }
        });
    }
}

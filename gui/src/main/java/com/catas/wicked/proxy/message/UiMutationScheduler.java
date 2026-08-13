package com.catas.wicked.proxy.message;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Drops queued JavaFX mutations that belong to a replaced request-tree generation. */
final class UiMutationScheduler {

    private static final int MAX_ACTIONS_PER_PULSE = 500;
    private static final long MAX_NANOS_PER_PULSE = 8_000_000L;

    private final Consumer<Runnable> delegate;
    private final AtomicLong generation = new AtomicLong();
    private final ConcurrentLinkedQueue<ScheduledAction> queue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean scheduled = new AtomicBoolean();

    UiMutationScheduler(Consumer<Runnable> delegate) {
        this.delegate = delegate;
    }

    Consumer<Runnable> nextSession() {
        long sessionGeneration = generation.incrementAndGet();
        queue.clear();
        return action -> enqueue(sessionGeneration, action);
    }

    int pendingActions() {
        return queue.size();
    }

    private void enqueue(long sessionGeneration, Runnable action) {
        queue.offer(new ScheduledAction(sessionGeneration, action));
        scheduleDrain();
    }

    private void scheduleDrain() {
        if (scheduled.compareAndSet(false, true)) {
            delegate.accept(this::drain);
        }
    }

    private void drain() {
        long started = System.nanoTime();
        int processed = 0;
        ScheduledAction action;
        while (processed < MAX_ACTIONS_PER_PULSE
                && System.nanoTime() - started < MAX_NANOS_PER_PULSE
                && (action = queue.poll()) != null) {
            if (generation.get() == action.generation) {
                action.action.run();
            }
            processed++;
        }
        scheduled.set(false);
        if (!queue.isEmpty()) {
            scheduleDrain();
        }
    }

    private record ScheduledAction(long generation, Runnable action) {
    }
}

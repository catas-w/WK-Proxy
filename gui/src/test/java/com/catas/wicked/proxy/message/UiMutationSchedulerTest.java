package com.catas.wicked.proxy.message;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;

public class UiMutationSchedulerTest {

    @Test
    public void replacingTheTreeGenerationDropsQueuedMutationsFromTheOldTree() {
        Deque<Runnable> pending = new ArrayDeque<>();
        UiMutationScheduler scheduler = new UiMutationScheduler(pending::addLast);
        AtomicInteger mutations = new AtomicInteger();
        Consumer<Runnable> firstTree = scheduler.nextSession();

        firstTree.accept(() -> mutations.addAndGet(1));
        Consumer<Runnable> secondTree = scheduler.nextSession();
        secondTree.accept(() -> mutations.addAndGet(10));

        while (!pending.isEmpty()) {
            pending.removeFirst().run();
        }

        assertEquals(10, mutations.get());
    }
}

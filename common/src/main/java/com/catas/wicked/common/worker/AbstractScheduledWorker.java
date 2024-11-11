package com.catas.wicked.common.worker;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public abstract class AbstractScheduledWorker implements ScheduledWorker {

    private enum LOCK_STATUS {
        FREE,
        LOCKED
    }

    private final AtomicReference<LOCK_STATUS> lock = new AtomicReference<>(LOCK_STATUS.FREE);
    private volatile boolean running;
    protected long spinDelay = 100L;
    protected long lockTimeout = 2 * 1000L;

    private final Semaphore semaphore = new Semaphore(0);

    @Override
    public void start() {
        if (!running) {
            running = true;
            semaphore.release();
        }
    }

    @Override
    public void pause() {
        running = false;
        semaphore.drainPermits();
    }

    @Override
    public boolean invoke() {
        return getLockAndRun(true);
    }

    @Override
    public void run() {
        try {
            semaphore.acquire();
            getLockAndRun(false);
        } catch (InterruptedException ignored) {
        } finally {
            if (this.running) {
                semaphore.release();
            }
        }
    }

    private boolean getLockAndRun(boolean manually) {
        long timeout = manually ? getLockTimeout() : 0;
        if (!tryLock(timeout)) {
            log.warn("Worker failed to get lock for: {} ms", timeout);
            return false;
        }
        try {
            return doWork(manually);
        } catch (Exception e) {
            log.error("Error in executing worker: ", e);
            return false;
        } finally {
            lock.set(LOCK_STATUS.FREE);
        }
    }

    private boolean tryLock(long timeout) {
        if (lock.compareAndSet(LOCK_STATUS.FREE, LOCK_STATUS.LOCKED)) {
            return true;
        }
        // System.out.println(getSpinDelay());
        if (timeout <= getSpinDelay()) {
            return false;
        }
        long endTime = System.currentTimeMillis() + timeout;
        while (System.currentTimeMillis() <= endTime) {
            if (lock.compareAndSet(LOCK_STATUS.FREE, LOCK_STATUS.LOCKED)) {
                return true;
            }
            try {
                Thread.sleep(getSpinDelay());
            } catch (InterruptedException ignored) {}
        }
        return false;
    }

    protected abstract boolean doWork(boolean manually);

    protected long getSpinDelay() {
        return spinDelay;
    }

    protected long getLockTimeout() {
        return lockTimeout;
    }
}

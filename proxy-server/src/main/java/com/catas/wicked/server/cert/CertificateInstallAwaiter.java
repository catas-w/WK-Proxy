package com.catas.wicked.server.cert;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

final class CertificateInstallAwaiter {

    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);
    static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(1);

    private final Duration timeout;
    private final Duration pollInterval;
    private final LongSupplier nanoTime;
    private final Sleeper sleeper;

    CertificateInstallAwaiter() {
        this(DEFAULT_TIMEOUT, DEFAULT_POLL_INTERVAL, System::nanoTime,
                nanos -> TimeUnit.NANOSECONDS.sleep(nanos));
    }

    CertificateInstallAwaiter(Duration timeout, Duration pollInterval,
                              LongSupplier nanoTime, Sleeper sleeper) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        if (pollInterval == null || pollInterval.isNegative() || pollInterval.isZero()) {
            throw new IllegalArgumentException("pollInterval must be positive");
        }
        this.timeout = timeout;
        this.pollInterval = pollInterval;
        this.nanoTime = nanoTime;
        this.sleeper = sleeper;
    }

    boolean await(BooleanSupplier installed) throws InterruptedException {
        long startedAt = nanoTime.getAsLong();
        long timeoutNanos = timeout.toNanos();
        long pollNanos = pollInterval.toNanos();

        while (true) {
            if (installed.getAsBoolean()) {
                return true;
            }
            long elapsed = nanoTime.getAsLong() - startedAt;
            if (elapsed >= timeoutNanos) {
                return false;
            }
            sleeper.sleep(Math.min(pollNanos, timeoutNanos - elapsed));
        }
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long nanos) throws InterruptedException;
    }
}

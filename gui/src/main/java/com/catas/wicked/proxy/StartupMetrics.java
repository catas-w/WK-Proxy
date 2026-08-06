package com.catas.wicked.proxy;

import java.util.Locale;

final class StartupMetrics {

    static final String ENABLED_PROPERTY = "wkproxy.startup.metrics";
    static final String ENABLED_ENVIRONMENT_VARIABLE = "WK_PROXY_STARTUP_METRICS";

    private static final boolean ENABLED = Boolean.getBoolean(ENABLED_PROPERTY)
            || Boolean.parseBoolean(System.getenv(ENABLED_ENVIRONMENT_VARIABLE));

    private static volatile long mainEnteredNanos;
    private static volatile long applicationStartedNanos;
    private static volatile long fxmlLoadedNanos;
    private static volatile long stageShownNanos;

    private StartupMetrics() {
    }

    static void markMainEntered() {
        if (ENABLED) {
            mainEnteredNanos = System.nanoTime();
        }
    }

    static void markApplicationStarted() {
        if (ENABLED) {
            applicationStartedNanos = System.nanoTime();
        }
    }

    static void markFxmlLoaded() {
        if (ENABLED) {
            fxmlLoadedNanos = System.nanoTime();
        }
    }

    static void markStageShown() {
        if (ENABLED) {
            stageShownNanos = System.nanoTime();
        }
    }

    static String completeFirstPulse() {
        if (!ENABLED) {
            return null;
        }
        long firstPulseNanos = System.nanoTime();
        return format(new StartupTiming(
                elapsedMillis(mainEnteredNanos, applicationStartedNanos),
                elapsedMillis(applicationStartedNanos, fxmlLoadedNanos),
                elapsedMillis(fxmlLoadedNanos, stageShownNanos),
                elapsedMillis(stageShownNanos, firstPulseNanos),
                elapsedMillis(mainEnteredNanos, firstPulseNanos)));
    }

    static String format(StartupTiming timing) {
        return String.format(Locale.ROOT,
                "Startup timing: launcher/DI=%.1f ms, FXML/CSS=%.1f ms, stage=%.1f ms, first-pulse=%.1f ms, total-from-main=%.1f ms",
                timing.launcherAndDependencyInjectionMillis(),
                timing.fxmlAndCssMillis(),
                timing.stageMillis(),
                timing.firstPulseMillis(),
                timing.totalMillis());
    }

    private static double elapsedMillis(long startNanos, long endNanos) {
        if (startNanos <= 0 || endNanos < startNanos) {
            return Double.NaN;
        }
        return (endNanos - startNanos) / 1_000_000.0;
    }

    record StartupTiming(double launcherAndDependencyInjectionMillis,
                         double fxmlAndCssMillis,
                         double stageMillis,
                         double firstPulseMillis,
                         double totalMillis) {
    }
}

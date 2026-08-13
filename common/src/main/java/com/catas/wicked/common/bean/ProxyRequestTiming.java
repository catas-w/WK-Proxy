package com.catas.wicked.common.bean;

/**
 * Per-request timing state. Wall-clock values are retained for display while
 * elapsed durations are calculated with the monotonic clock.
 */
public final class ProxyRequestTiming {

    private final String requestId;

    private long requestStartTime;
    private long requestEndTime;
    private long responseStartTime;
    private long responseEndTime;

    private long requestStartNanos;
    private long requestEndNanos;
    private long responseStartNanos;
    private long responseEndNanos;

    private boolean requestStarted;
    private boolean requestEnded;
    private boolean responseStarted;
    private boolean responseEnded;

    public ProxyRequestTiming(String requestId) {
        this.requestId = requestId;
    }

    public synchronized void markRequestStart() {
        if (!requestStarted) {
            requestStartTime = System.currentTimeMillis();
            requestStartNanos = System.nanoTime();
            requestStarted = true;
        }
    }

    public synchronized void markRequestEnd() {
        markRequestStart();
        if (!requestEnded) {
            requestEndTime = Math.max(requestStartTime, System.currentTimeMillis());
            requestEndNanos = System.nanoTime();
            requestEnded = true;
        }
    }

    public synchronized void markResponseStart() {
        if (!responseStarted) {
            responseStartTime = Math.max(requestEndTime, System.currentTimeMillis());
            responseStartNanos = System.nanoTime();
            responseStarted = true;
        }
    }

    public synchronized void markResponseEnd() {
        markResponseStart();
        if (!responseEnded) {
            responseEndTime = Math.max(responseStartTime, System.currentTimeMillis());
            responseEndNanos = System.nanoTime();
            responseEnded = true;
        }
    }

    public String getRequestId() {
        return requestId;
    }

    public synchronized long getRequestStartTime() {
        return requestStartTime;
    }

    public synchronized long getRequestEndTime() {
        return requestEndTime;
    }

    public synchronized long getResponseStartTime() {
        return responseStartTime;
    }

    public synchronized long getResponseEndTime() {
        return responseEndTime;
    }

    public synchronized long getRequestDurationNanos() {
        return elapsed(requestStartNanos, requestEndNanos, requestStarted && requestEnded);
    }

    public synchronized long getWaitingDurationNanos() {
        return elapsed(requestEndNanos, responseStartNanos, requestEnded && responseStarted);
    }

    public synchronized long getResponseDurationNanos() {
        return elapsed(responseStartNanos, responseEndNanos, responseStarted && responseEnded);
    }

    private static long elapsed(long start, long end, boolean complete) {
        return complete && end >= start ? end - start : 0L;
    }
}

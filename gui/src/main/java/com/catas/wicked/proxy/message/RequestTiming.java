package com.catas.wicked.proxy.message;

import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.bean.message.ResponseMessage;

import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;

public final class RequestTiming {

    private static final double DEFAULT_FIRST_DIVIDER = 1.0 / 3.0;
    private static final double DEFAULT_SECOND_DIVIDER = 2.0 / 3.0;
    private static final double MIN_SEGMENT = 0.01;

    private final long requestStart;
    private final long requestEnd;
    private final long responseStart;
    private final long responseEnd;
    private final boolean requestValid;
    private final boolean waitingValid;
    private final boolean responseValid;
    private final long requestDurationNanos;
    private final long waitingDurationNanos;
    private final long responseDurationNanos;

    private RequestTiming(long requestStart, long requestEnd, long responseStart, long responseEnd,
                          long requestDurationNanos, long waitingDurationNanos,
                          long responseDurationNanos) {
        this.requestStart = requestStart;
        this.requestEnd = requestEnd;
        this.responseStart = responseStart;
        this.responseEnd = responseEnd;
        this.requestDurationNanos = requestDurationNanos;
        this.waitingDurationNanos = waitingDurationNanos;
        this.responseDurationNanos = responseDurationNanos;
        requestValid = requestDurationNanos > 0
                || (requestStart > 0 && requestEnd > 0 && requestStart <= requestEnd);
        waitingValid = waitingDurationNanos > 0
                || (requestValid && responseStart > 0 && requestEnd <= responseStart);
        responseValid = responseDurationNanos > 0
                || (waitingValid && responseEnd > 0 && responseStart <= responseEnd);
    }

    public static RequestTiming from(RequestMessage request) {
        if (request == null) {
            return new RequestTiming(0, 0, 0, 0, 0, 0, 0);
        }
        ResponseMessage response = request.getResponse();
        return new RequestTiming(
                request.getStartTime(),
                request.getEndTime(),
                response == null ? 0 : response.getStartTime(),
                response == null ? 0 : response.getEndTime(),
                request.getDurationNanos(),
                response == null ? 0 : response.getWaitingDurationNanos(),
                response == null ? 0 : response.getDurationNanos());
    }

    public OptionalLong requestDuration() {
        return durationMillis(requestDurationNanos, requestValid, requestEnd - requestStart);
    }

    public OptionalLong waitingDuration() {
        return durationMillis(waitingDurationNanos, waitingValid, responseStart - requestEnd);
    }

    public OptionalLong responseDuration() {
        return durationMillis(responseDurationNanos, responseValid, responseEnd - responseStart);
    }

    public OptionalLong totalDuration() {
        if (!responseValid) {
            return OptionalLong.empty();
        }
        if (hasMonotonicDurations()) {
            return OptionalLong.of(TimeUnit.NANOSECONDS.toMillis(
                    requestDurationNanos + waitingDurationNanos + responseDurationNanos));
        }
        return OptionalLong.of(responseEnd - requestStart);
    }

    public OptionalLong requestStart() {
        return requestStart > 0 ? OptionalLong.of(requestStart) : OptionalLong.empty();
    }

    public OptionalLong requestEnd() {
        return requestValid ? OptionalLong.of(requestEnd) : OptionalLong.empty();
    }

    public OptionalLong responseStart() {
        return waitingValid ? OptionalLong.of(responseStart) : OptionalLong.empty();
    }

    public OptionalLong responseEnd() {
        return responseValid ? OptionalLong.of(responseEnd) : OptionalLong.empty();
    }

    public double firstDivider() {
        OptionalLong total = totalDuration();
        if (total.isEmpty() || total.getAsLong() == 0) {
            return DEFAULT_FIRST_DIVIDER;
        }
        double position = (double) requestDuration().orElse(0) / total.getAsLong();
        return clamp(position, MIN_SEGMENT, 1.0 - (2 * MIN_SEGMENT));
    }

    public double secondDivider() {
        OptionalLong total = totalDuration();
        if (total.isEmpty() || total.getAsLong() == 0) {
            return DEFAULT_SECOND_DIVIDER;
        }
        double elapsed = requestDuration().orElse(0) + waitingDuration().orElse(0);
        return clamp(elapsed / total.getAsLong(), firstDivider() + MIN_SEGMENT, 1.0 - MIN_SEGMENT);
    }

    public static String formatDuration(OptionalLong duration) {
        return duration.isPresent() ? duration.getAsLong() + " ms" : "-";
    }

    public String formattedRequestDuration() {
        return formatPhase(requestDurationNanos, requestDuration());
    }

    public String formattedWaitingDuration() {
        return formatPhase(waitingDurationNanos, waitingDuration());
    }

    public String formattedResponseDuration() {
        return formatPhase(responseDurationNanos, responseDuration());
    }

    public String formattedTotalDuration() {
        if (hasMonotonicDurations()) {
            return formatNanos(requestDurationNanos + waitingDurationNanos + responseDurationNanos);
        }
        return formatDuration(totalDuration());
    }

    private boolean hasMonotonicDurations() {
        return requestDurationNanos > 0 && waitingDurationNanos > 0 && responseDurationNanos > 0;
    }

    private static OptionalLong durationMillis(long nanos, boolean valid, long fallbackMillis) {
        if (!valid) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(nanos > 0 ? TimeUnit.NANOSECONDS.toMillis(nanos) : fallbackMillis);
    }

    private static String formatPhase(long nanos, OptionalLong fallback) {
        return nanos > 0 ? formatNanos(nanos) : formatDuration(fallback);
    }

    private static String formatNanos(long nanos) {
        if (nanos > 0 && nanos < 1_000_000L) {
            return "<1 ms";
        }
        return TimeUnit.NANOSECONDS.toMillis(nanos) + " ms";
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}

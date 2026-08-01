package com.catas.wicked.proxy.message;

import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.bean.message.ResponseMessage;

import java.util.OptionalLong;

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

    private RequestTiming(long requestStart, long requestEnd, long responseStart, long responseEnd) {
        this.requestStart = requestStart;
        this.requestEnd = requestEnd;
        this.responseStart = responseStart;
        this.responseEnd = responseEnd;
        requestValid = requestStart > 0 && requestEnd > 0 && requestStart <= requestEnd;
        waitingValid = requestValid && responseStart > 0 && requestEnd <= responseStart;
        responseValid = waitingValid && responseEnd > 0 && responseStart <= responseEnd;
    }

    public static RequestTiming from(RequestMessage request) {
        if (request == null) {
            return new RequestTiming(0, 0, 0, 0);
        }
        ResponseMessage response = request.getResponse();
        return new RequestTiming(
                request.getStartTime(),
                request.getEndTime(),
                response == null ? 0 : response.getStartTime(),
                response == null ? 0 : response.getEndTime());
    }

    public OptionalLong requestDuration() {
        return requestValid ? OptionalLong.of(requestEnd - requestStart) : OptionalLong.empty();
    }

    public OptionalLong waitingDuration() {
        return waitingValid ? OptionalLong.of(responseStart - requestEnd) : OptionalLong.empty();
    }

    public OptionalLong responseDuration() {
        return responseValid ? OptionalLong.of(responseEnd - responseStart) : OptionalLong.empty();
    }

    public OptionalLong totalDuration() {
        return responseValid ? OptionalLong.of(responseEnd - requestStart) : OptionalLong.empty();
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

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}

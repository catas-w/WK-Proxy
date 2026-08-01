package com.catas.wicked.proxy.message;

import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.bean.message.ResponseMessage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RequestTimingTest {

    @Test
    public void calculatesCompleteOrderedRequest() {
        RequestTiming timing = timing(1_000, 1_100, 1_250, 1_500);

        assertEquals(100, timing.requestDuration().getAsLong());
        assertEquals(150, timing.waitingDuration().getAsLong());
        assertEquals(250, timing.responseDuration().getAsLong());
        assertEquals(500, timing.totalDuration().getAsLong());
        assertEquals(0.2, timing.firstDivider(), 0.0001);
        assertEquals(0.5, timing.secondDivider(), 0.0001);
    }

    @Test
    public void keepsCompletedRequestPhaseWhileResponseIsPending() {
        RequestTiming timing = timing(1_000, 1_100, 0, 0);

        assertEquals(100, timing.requestDuration().getAsLong());
        assertFalse(timing.waitingDuration().isPresent());
        assertFalse(timing.responseDuration().isPresent());
        assertFalse(timing.totalDuration().isPresent());
        assertEquals(1.0 / 3.0, timing.firstDivider(), 0.0001);
        assertEquals(2.0 / 3.0, timing.secondDivider(), 0.0001);
    }

    @Test
    public void rejectsEpochAndOutOfOrderTimestamps() {
        RequestTiming missingRequestEnd = timing(1_000, 0, 1_200, 1_300);
        RequestTiming responseBeforeRequest = timing(1_000, 1_200, 1_100, 1_300);

        assertFalse(missingRequestEnd.requestDuration().isPresent());
        assertFalse(missingRequestEnd.totalDuration().isPresent());
        assertFalse(responseBeforeRequest.waitingDuration().isPresent());
        assertFalse(responseBeforeRequest.responseDuration().isPresent());
        assertFalse(responseBeforeRequest.totalDuration().isPresent());
        assertFalse(responseBeforeRequest.requestEnd().isEmpty());
    }

    @Test
    public void zeroDurationUsesStableFiniteDividers() {
        RequestTiming timing = timing(1_000, 1_000, 1_000, 1_000);

        assertEquals(0, timing.totalDuration().getAsLong());
        assertEquals(1.0 / 3.0, timing.firstDivider(), 0.0001);
        assertEquals(2.0 / 3.0, timing.secondDivider(), 0.0001);
        assertTrue(Double.isFinite(timing.firstDivider()));
        assertTrue(timing.firstDivider() < timing.secondDivider());
    }

    @Test
    public void clampsTinySegmentsWithoutInvertingDividers() {
        RequestTiming timing = timing(1_000, 1_000, 1_000, 2_000);

        assertTrue(Double.isFinite(timing.firstDivider()));
        assertTrue(Double.isFinite(timing.secondDivider()));
        assertTrue(timing.firstDivider() > 0);
        assertTrue(timing.firstDivider() < timing.secondDivider());
        assertTrue(timing.secondDivider() < 1);
    }

    private static RequestTiming timing(long requestStart, long requestEnd,
                                        long responseStart, long responseEnd) {
        RequestMessage request = new RequestMessage();
        request.setStartTime(requestStart);
        request.setEndTime(requestEnd);
        ResponseMessage response = new ResponseMessage();
        response.setStartTime(responseStart);
        response.setEndTime(responseEnd);
        request.setResponse(response);
        return RequestTiming.from(request);
    }
}

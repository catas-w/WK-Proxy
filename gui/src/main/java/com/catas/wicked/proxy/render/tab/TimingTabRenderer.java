package com.catas.wicked.proxy.render.tab;

import com.catas.wicked.common.bean.message.RenderMessage;
import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.proxy.gui.componet.TimeSplitPane;
import com.catas.wicked.proxy.gui.controller.DetailTabController;
import com.catas.wicked.proxy.message.RequestTiming;
import com.catas.wicked.proxy.render.PreparedRender;
import com.catas.wicked.proxy.service.record.RequestRecordStore;
import com.catas.wicked.proxy.service.record.RequestRecordSnapshot;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class TimingTabRenderer extends AbstractTabRenderer {

    @Inject
    private DetailTabController detailTabController;

    @Inject
    private RequestRecordStore requestStore;

    @Override
    public PreparedRender prepare(RenderMessage renderMsg) {
        if (renderMsg.isPath()) {
            return PreparedRender.noop(renderMsg.getRequestId(), renderMsg.isEmpty());
        }
        String requestId = renderMsg.getRequestId();
        RequestRecordSnapshot snapshot = renderMsg.isEmpty() ? null : requestStore.snapshot(requestId);
        RequestMessage request = snapshot == null ? null : snapshot.message();
        RequestTiming timing = RequestTiming.from(request);

        return new PreparedRender(requestId, renderMsg.isEmpty(), () -> {
            boolean empty = renderMsg.isEmpty() || request == null;
            detailTabController.getTimingMsgLabel().setVisible(empty);
            if (empty) {
                setEmptyMsgLabel(detailTabController.getTimingMsgLabel());
                return;
            }

            double firstDivider = timing.firstDivider();
            double secondDivider = timing.secondDivider();
            List<TimeSplitPane> splitPanes = List.of(
                    detailTabController.getRequestTimeSplit(),
                    detailTabController.getWaitingTimeSplit(),
                    detailTabController.getResponseTimeSplit());
            splitPanes.forEach(splitPane ->
                    splitPane.setDividerPositions(firstDivider, secondDivider));

            detailTabController.getRequestDurationLabel().setText(
                    RequestTiming.formatDuration(timing.requestDuration()));
            detailTabController.getWaitingDurationLabel().setText(
                    RequestTiming.formatDuration(timing.waitingDuration()));
            detailTabController.getResponseDurationLabel().setText(
                    RequestTiming.formatDuration(timing.responseDuration()));
            detailTabController.getTotalDurationLabel().setText(
                    RequestTiming.formatDuration(timing.totalDuration()));

            detailTabController.getRequestTimeSplit().setSegmentVisible(timing.requestDuration().isPresent());
            detailTabController.getWaitingTimeSplit().setSegmentVisible(timing.waitingDuration().isPresent());
            detailTabController.getResponseTimeSplit().setSegmentVisible(timing.responseDuration().isPresent());
            detailTabController.getTotalTimeBar().setVisible(timing.totalDuration().isPresent());
        });
    }
}

package com.catas.wicked.proxy.render.tab;

import com.catas.wicked.common.bean.message.RenderMessage;
import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.proxy.gui.componet.TimeSplitPane;
import com.catas.wicked.proxy.gui.controller.DetailTabController;
import com.catas.wicked.proxy.message.RequestTiming;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.application.Platform;
import org.apache.commons.lang3.StringUtils;
import org.ehcache.Cache;

import java.util.List;

@Singleton
public class TimingTabRenderer extends AbstractTabRenderer {

    @Inject
    private DetailTabController detailTabController;

    @Inject
    private Cache<String, RequestMessage> requestCache;

    @Inject
    private ApplicationConfig appConfig;

    @Override
    public void render(RenderMessage renderMsg) {
        if (renderMsg.isPath()) {
            return;
        }
        String requestId = renderMsg.getRequestId();
        RequestMessage request = renderMsg.isEmpty() ? null : requestCache.get(requestId);
        RequestTiming timing = RequestTiming.from(request);

        Platform.runLater(() -> {
            String currentRequestId = appConfig.getObservableConfig().getCurrentRequestId();
            if ((renderMsg.isEmpty() && currentRequestId != null)
                    || (!renderMsg.isEmpty() && !StringUtils.equals(requestId, currentRequestId))) {
                return;
            }
            boolean empty = renderMsg.isEmpty() || request == null;
            detailTabController.getTimingMsgLabel().setVisible(empty);
            if (empty) {
                setEmptyMsgLabel(detailTabController.getTimingMsgLabel());
                return;
            }

            detailTabController.showRequestOnlyTabs();
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

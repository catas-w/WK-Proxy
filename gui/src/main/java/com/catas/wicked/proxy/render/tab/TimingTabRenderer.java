package com.catas.wicked.proxy.render.tab;

import com.catas.wicked.common.bean.message.RenderMessage;
import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.bean.message.ResponseMessage;
import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.proxy.gui.componet.TimeSplitPane;
import com.catas.wicked.proxy.gui.controller.DetailTabController;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
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
        // System.out.println("--render timing --");
        detailTabController.getTimingMsgLabel().setVisible(renderMsg.isEmpty());
        if (renderMsg.isEmpty()) {
            setEmptyMsgLabel(detailTabController.getTimingMsgLabel());
            return;
        }
        if (renderMsg.isPath()) {
            return;
        }
        detailTabController.showRequestOnlyTabs();
        RequestMessage request = requestCache.get(renderMsg.getRequestId());
        ResponseMessage response = request.getResponse();

        long requestTime = request.getEndTime() - request.getStartTime();
        long waitingTime = response == null ? 0 : response.getStartTime() - request.getEndTime();
        long respTime = response == null ? 0 : response.getEndTime() - response.getStartTime();
        long total = requestTime + waitingTime + respTime;
        double dividerPos1 = (double) requestTime / total;
        double dividerPos2 = (double) (requestTime + waitingTime) / total;

        GridPane timingPane = detailTabController.getTimingGridPane();
        List<TimeSplitPane> timeSplits = timingPane.getChildren()
                .stream()
                .filter(node -> node instanceof TimeSplitPane)
                .map(node -> (TimeSplitPane) node).toList();
        List<Label> timeLabels = timingPane.lookupAll(".duration-label")
                .stream()
                .map(node -> (Label) node).toList();

        timeSplits.forEach(splitPane -> splitPane.setDividerPositions(dividerPos1, dividerPos2));
        Platform.runLater(() -> {
            timeLabels.get(0).setText(requestTime + " ms");
            timeLabels.get(1).setText(waitingTime + " ms");
            timeLabels.get(2).setText(respTime + " ms");
            timeLabels.get(3).setText(total + " ms");
        });
    }
}

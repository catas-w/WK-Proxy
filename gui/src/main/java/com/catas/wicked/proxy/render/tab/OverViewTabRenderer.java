package com.catas.wicked.proxy.render.tab;

import com.catas.wicked.common.bean.PathOverviewInfo;
import com.catas.wicked.common.bean.RequestOverviewInfo;
import com.catas.wicked.common.bean.PairEntry;
import com.catas.wicked.common.bean.StatsData;
import com.catas.wicked.common.bean.message.RenderMessage;
import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.bean.message.ResponseMessage;
import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.util.WebUtils;
import com.catas.wicked.proxy.gui.controller.DetailTabController;
import com.catas.wicked.proxy.message.MessageService;
import io.netty.handler.codec.http.HttpMethod;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableView;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.ehcache.Cache;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

@Slf4j
@Singleton
public class OverViewTabRenderer extends AbstractTabRenderer {

    @Inject
    private DetailTabController detailTabController;

    @Inject
    private Cache<String, RequestMessage> requestCache;

    @Inject
    private ApplicationConfig appConfig;

    @Inject
    private RequestOverviewInfo requestOverviewInfo;

    @Inject
    private PathOverviewInfo pathOverviewInfo;

    @Setter
    private MessageService messageService;

    private TreeItem<PairEntry> requestRoot;
    private TreeItem<PairEntry> pathRoot;

    private final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    @Override
    public void render(RenderMessage renderMsg) {
        // System.out.println("-- render overview --");
        detailTabController.getOverViewMsgLabel().setVisible(renderMsg.isEmpty());
        if (renderMsg.isEmpty()) {
            return;
        }
        if (renderMsg.isPath()) {
            // display path info
            detailTabController.hideRequestOnlyTabs();
            displayPathOverview(renderMsg);
        } else {
            // display request info
            RequestMessage request = requestCache.get(renderMsg.getRequestId());
            displayOverView(request);
        }

    }

    private void displayPathOverview(RenderMessage renderMsg) {
        String path = renderMsg.getRequestId().substring(RenderMessage.PATH_MSG.length());

        if (pathRoot == null) {
            initPathRoot();
        }
        detailTabController.setOverviewTableRoot(pathRoot);

        String urlPath = "-";
        String host = "-";
        String port = "-";
        String protocol = "-";
        try {
            URL url = new URL(path);
            urlPath = url.getPath();
            host = url.getHost();
            port = String.valueOf(url.getPort() == -1 ? url.getDefaultPort(): url.getPort());
            protocol = url.getProtocol().toUpperCase(Locale.ROOT);
        } catch (MalformedURLException e) {
            log.error("Error in parsing overview path: ", e);
        }
        pathOverviewInfo.getHost().setVal(host);
        pathOverviewInfo.getPath().setVal(urlPath);
        pathOverviewInfo.getPort().setVal(port);
        pathOverviewInfo.getProtocol().setVal(protocol);

        StatsData statsData = messageService.pathStatistics(path);
        if (statsData == null) {
            log.error("OverviewTab statsData is null, {}", path);
            detailTabController.refreshOverviewTable();
            return;
        }
        Map<HttpMethod, Integer> countMap = statsData.getCountMap();
        pathOverviewInfo.getTotalCnt().setVal(String.valueOf(statsData.getCount()));
        pathOverviewInfo.getGetCnt().setVal(String.valueOf(countMap.getOrDefault(HttpMethod.GET, 0)));
        pathOverviewInfo.getPostCnt().setVal(String.valueOf(countMap.getOrDefault(HttpMethod.POST, 0)));

        // time
        pathOverviewInfo.getTimeCost().setVal(statsData.getTimeCost() == 0 ? "-": statsData.getTimeCost() + " ms");
        Date startTime = statsData.getStartTime();
        pathOverviewInfo.getStartTime().setVal(startTime != null && startTime.getTime() > 0 ? dateFormat.format(startTime): "-");
        Date endTime = statsData.getEndTime();
        pathOverviewInfo.getEndTime().setVal(endTime != null && endTime.getTime() > 0 ? dateFormat.format(endTime): "-");
        pathOverviewInfo.getAverageSpeed().setVal(statsData.getAverageSpeed() > 0 ? String.format("%.2f KB/s", statsData.getAverageSpeed()) : "-");

        // size
        pathOverviewInfo.getTotalSize().setVal(statsData.getTotalSize() > 0 ? WebUtils.getHSize(statsData.getTotalSize()) : "-");
        pathOverviewInfo.getRequestsSize().setVal(statsData.getRequestsSize() > 0 ? WebUtils.getHSize(statsData.getRequestsSize()) : "-");
        pathOverviewInfo.getResponsesSize().setVal(statsData.getResponsesSize() > 0 ? WebUtils.getHSize(statsData.getResponsesSize()) : "-");

        detailTabController.refreshOverviewTable();
    }

    public void displayOverView(RequestMessage request) {
        TreeTableView<PairEntry> overviewTable = detailTabController.getOverviewTable();
        // if (overviewTable.getColumns() == null || overviewTable.getColumns().isEmpty()) {
        if (requestRoot == null) {
            initRequestRoot();
        }
        detailTabController.setOverviewTableRoot(requestRoot);

        String protocol = request.getProtocol() == null ? "-" : request.getProtocol();
        String url = request.getRequestUrl();
        String method = request.getMethod();
        if (method.contains("UNK")) {
            method = "-";
        }
        ResponseMessage response = request.getResponse();
        String code = response == null ? "Pending" : response.getStatusStr() + " " + response.getReasonPhrase();

        // set status-column style
        if (response == null) {
            requestOverviewInfo.getStatus().setColumnStyleClass(PairEntry.ColumnStyle.PENDING.getStyleClass());
        } else {
            requestOverviewInfo.getStatus().setColumnStyleClass(PairEntry.ColumnStyle.OK.getStyleClass());
        }

        // basic
        requestOverviewInfo.getUrl().setVal(url);
        requestOverviewInfo.getMethod().setVal(method);
        requestOverviewInfo.getStatus().setVal(code);
        requestOverviewInfo.getProtocol().setVal(protocol);
        requestOverviewInfo.getRemoteHost().setVal(request.getRemoteHost());
        requestOverviewInfo.getRemotePort().setVal(String.valueOf(request.getRemotePort()));
        requestOverviewInfo.getClientHost().setVal(request.getLocalAddress());
        requestOverviewInfo.getClientPort().setVal(String.valueOf(request.getLocalPort()));

        // timing
        requestOverviewInfo.getTimeCost().setVal(response == null ? "-": response.getEndTime() - request.getStartTime() + " ms");
        requestOverviewInfo.getRequestTime().setVal(request.getStartTime() + "-" + request.getEndTime());
        requestOverviewInfo.getRequestStart().setVal(dateFormat.format(new Date(request.getStartTime())));
        requestOverviewInfo.getRequestEnd().setVal(dateFormat.format(new Date(request.getEndTime())));
        requestOverviewInfo.getRespTime().setVal(response == null ? "-": response.getStartTime() + " - " + response.getEndTime());
        requestOverviewInfo.getRespStart().setVal(response == null ? "-": dateFormat.format(new Date(response.getStartTime())));
        requestOverviewInfo.getRespEnd().setVal(response == null ? "-": dateFormat.format(new Date(response.getEndTime())));

        // size
        requestOverviewInfo.getRequestSize().setVal(WebUtils.getHSize(request.getSize()));
        requestOverviewInfo.getResponseSize().setVal(response == null ? "-": WebUtils.getHSize(response.getSize()));
        requestOverviewInfo.getAverageSpeed().setVal(getSpeed(request, response));

        detailTabController.refreshOverviewTable();
    }

    private String getSpeed(RequestMessage request, ResponseMessage response) {
        if (response == null || (request.getSize() == 0 && response.getSize() == 0)) {
            return "-";
        }
        long size = request.getSize() + response.getSize();
        int time = (int) (response.getEndTime() - request.getStartTime());
        return String.format("%.2f KB/s", (double) size / (double) time);
    }

    private String getContentStr(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        map.forEach((key, value) -> builder.append(key).append(": ").append(value).append("\n"));
        builder.deleteCharAt(builder.length() - 1);
        return builder.toString();
    }

    /**
     * initialize treeTableView
     */
    @SuppressWarnings("unchecked")
    public void initRequestRoot() {
        requestRoot = new TreeItem<>();
        TreeItem<PairEntry> reqNode = new TreeItem<>(new PairEntry("General", null));
        TreeItem<PairEntry> sizeNode = new TreeItem<>(new PairEntry("Size", null, "Maybe inaccurate"));
        TreeItem<PairEntry> timingNode = new TreeItem<>(new PairEntry("Timing", null, "Maybe inaccurate"));

        // basic info
        reqNode.getChildren().add(new TreeItem<>(requestOverviewInfo.getUrl()));
        reqNode.getChildren().add(new TreeItem<>(requestOverviewInfo.getMethod()));
        reqNode.getChildren().add(new TreeItem<>(requestOverviewInfo.getProtocol()));

        TreeItem<PairEntry> statusItem = new TreeItem<>(requestOverviewInfo.getStatus());
        reqNode.getChildren().add(statusItem);
        reqNode.getChildren().add(new TreeItem<>(requestOverviewInfo.getRemoteHost()));
        reqNode.getChildren().add(new TreeItem<>(requestOverviewInfo.getRemotePort()));
        reqNode.getChildren().add(new TreeItem<>(requestOverviewInfo.getClientHost()));
        reqNode.getChildren().add(new TreeItem<>(requestOverviewInfo.getClientPort()));

        // timing info
        timingNode.getChildren().add(new TreeItem<>(requestOverviewInfo.getTimeCost()));
        timingNode.getChildren().add(new TreeItem<>(requestOverviewInfo.getRequestTime()));
        timingNode.getChildren().add(new TreeItem<>(requestOverviewInfo.getRequestStart()));
        timingNode.getChildren().add(new TreeItem<>(requestOverviewInfo.getRequestEnd()));
        timingNode.getChildren().add(new TreeItem<>(requestOverviewInfo.getRespTime()));
        timingNode.getChildren().add(new TreeItem<>(requestOverviewInfo.getRespStart()));
        timingNode.getChildren().add(new TreeItem<>(requestOverviewInfo.getRespEnd()));

        // size info
        sizeNode.getChildren().add(new TreeItem<>(requestOverviewInfo.getRequestSize()));
        sizeNode.getChildren().add(new TreeItem<>(requestOverviewInfo.getResponseSize()));
        sizeNode.getChildren().add(new TreeItem<>(requestOverviewInfo.getAverageSpeed()));

        requestRoot.setExpanded(true);
        reqNode.setExpanded(true);
        sizeNode.setExpanded(true);
        timingNode.setExpanded(true);
        requestRoot.getChildren().addAll(reqNode, timingNode, sizeNode);
    }

    @SuppressWarnings("unchecked")
    public void initPathRoot() {
        pathRoot = new TreeItem<>();
        // general
        TreeItem<PairEntry> generalNode = new TreeItem<>(new PairEntry("General", null));
        generalNode.getChildren().add(new TreeItem<>(pathOverviewInfo.getHost()));
        generalNode.getChildren().add(new TreeItem<>(pathOverviewInfo.getPort()));
        generalNode.getChildren().add(new TreeItem<>(pathOverviewInfo.getPath()));
        generalNode.getChildren().add(new TreeItem<>(pathOverviewInfo.getProtocol()));
        generalNode.getChildren().add(new TreeItem<>(pathOverviewInfo.getTotalCnt()));
        generalNode.getChildren().add(new TreeItem<>(pathOverviewInfo.getGetCnt()));
        generalNode.getChildren().add(new TreeItem<>(pathOverviewInfo.getPostCnt()));

        // timing
        TreeItem<PairEntry> timingNode = new TreeItem<>(new PairEntry("Timing", null, "Maybe inaccurate"));
        timingNode.getChildren().add(new TreeItem<>(pathOverviewInfo.getTimeCost()));
        timingNode.getChildren().add(new TreeItem<>(pathOverviewInfo.getStartTime()));
        timingNode.getChildren().add(new TreeItem<>(pathOverviewInfo.getEndTime()));
        timingNode.getChildren().add(new TreeItem<>(pathOverviewInfo.getAverageSpeed()));

        // size
        TreeItem<PairEntry> sizeNode = new TreeItem<>(new PairEntry("Size", null, "Maybe inaccurate"));
        sizeNode.getChildren().add(new TreeItem<>(pathOverviewInfo.getTotalSize()));
        sizeNode.getChildren().add(new TreeItem<>(pathOverviewInfo.getRequestsSize()));
        sizeNode.getChildren().add(new TreeItem<>(pathOverviewInfo.getResponsesSize()));

        generalNode.setExpanded(true);
        sizeNode.setExpanded(true);
        timingNode.setExpanded(true);
        pathRoot.getChildren().addAll(generalNode, timingNode, sizeNode);
    }
}

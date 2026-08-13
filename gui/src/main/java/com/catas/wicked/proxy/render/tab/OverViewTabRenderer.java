package com.catas.wicked.proxy.render.tab;

import com.catas.wicked.common.bean.ApplicationGroupOverviewInfo;
import com.catas.wicked.common.bean.PathOverviewInfo;
import com.catas.wicked.common.bean.ProcessInfo;
import com.catas.wicked.common.bean.RequestCell;
import com.catas.wicked.common.bean.RequestOverviewInfo;
import com.catas.wicked.common.bean.PairEntry;
import com.catas.wicked.common.bean.StatsData;
import com.catas.wicked.common.bean.message.RenderMessage;
import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.bean.message.ResponseMessage;
import com.catas.wicked.common.provider.ResourceMessageProvider;
import com.catas.wicked.common.util.WebUtils;
import com.catas.wicked.proxy.gui.controller.DetailTabController;
import com.catas.wicked.proxy.message.MessageService;
import com.catas.wicked.proxy.message.ApplicationGroupOverview;
import com.catas.wicked.proxy.message.RequestTiming;
import com.catas.wicked.proxy.render.PreparedRender;
import com.catas.wicked.proxy.service.record.RequestRecordStore;
import com.catas.wicked.proxy.service.record.RequestRecordSnapshot;
import io.netty.handler.codec.http.HttpMethod;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.scene.control.TreeItem;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class OverViewTabRenderer extends AbstractTabRenderer {

    @Inject
    private DetailTabController detailTabController;

    @Inject
    private RequestRecordStore requestStore;

    @Inject
    private RequestOverviewInfo requestOverviewInfo;

    @Inject
    private PathOverviewInfo pathOverviewInfo;

    @Inject
    private ApplicationGroupOverviewInfo applicationGroupOverviewInfo;

    @Setter
    private MessageService messageService;

    @Inject
    private ResourceMessageProvider resourceMessageProvider;

    private TreeItem<PairEntry> requestRoot;
    private TreeItem<PairEntry> pathRoot;
    private TreeItem<PairEntry> applicationGroupRoot;
    private TreeItem<PairEntry> applicationHostRoot;

    private final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    @Override
    public PreparedRender prepare(RenderMessage renderMsg) {
        RequestRecordSnapshot snapshot = renderMsg.isEmpty() || renderMsg.isPath()
                || renderMsg.isApplicationGroup() ? null : requestStore.snapshot(renderMsg.getRequestId());
        RequestMessage request = snapshot == null ? null : snapshot.message();
        return new PreparedRender(renderMsg.getRequestId(), renderMsg.isEmpty(),
                () -> apply(renderMsg, request));
    }

    private void apply(RenderMessage renderMsg, RequestMessage request) {
        detailTabController.getOverViewMsgLabel().setVisible(renderMsg.isEmpty());
        if (renderMsg.isEmpty()) {
            return;
        }
        if (renderMsg.isApplicationGroup()) {
            displayApplicationGroupOverview(renderMsg);
        } else if (renderMsg.isPath()) {
            // display path info
            displayPathOverview(renderMsg);
        } else {
            // display request info
            if (request == null) {
                detailTabController.getOverViewMsgLabel().setVisible(true);
                return;
            }
            displayOverView(request);
        }
    }

    private void displayApplicationGroupOverview(RenderMessage renderMsg) {
        RequestCell.NodeType nodeType;
        String nodeKey;
        if (renderMsg.isApplicationHost()) {
            nodeType = RequestCell.NodeType.HOST;
            nodeKey = renderMsg.getRequestId().substring(RenderMessage.APPLICATION_HOST_MSG.length());
        } else {
            nodeType = RequestCell.NodeType.APPLICATION;
            nodeKey = renderMsg.getRequestId().substring(RenderMessage.APPLICATION_MSG.length());
        }

        ApplicationGroupOverview overview = messageService.applicationGroupOverview(nodeType, nodeKey);
        if (overview == null) {
            detailTabController.getOverViewMsgLabel().setVisible(true);
            return;
        }
        detailTabController.getOverViewMsgLabel().setVisible(false);
        boolean hostOverview = nodeType == RequestCell.NodeType.HOST;
        if (hostOverview && applicationHostRoot == null) {
            applicationHostRoot = initApplicationGroupRoot(true);
        } else if (!hostOverview && applicationGroupRoot == null) {
            applicationGroupRoot = initApplicationGroupRoot(false);
        }
        detailTabController.setOverviewTableRoot(hostOverview ? applicationHostRoot : applicationGroupRoot);

        setValue(applicationGroupOverviewInfo.getApplication(), overview.applicationName());
        setValue(applicationGroupOverviewInfo.getHost(), overview.host());
        setValue(applicationGroupOverviewInfo.getDomainCount(), String.valueOf(overview.domainCount()));
        setCollectionValue(applicationGroupOverviewInfo.getProtocols(), overview.protocols());
        setCollectionValue(applicationGroupOverviewInfo.getPorts(), overview.ports());
        setCollectionValue(applicationGroupOverviewInfo.getProcesses(), overview.processNames());
        setCollectionValue(applicationGroupOverviewInfo.getOwnerPids(), overview.ownerPids());
        setCollectionValue(applicationGroupOverviewInfo.getApplicationPids(), overview.applicationPids());
        setValue(applicationGroupOverviewInfo.getExecutable(), overview.executablePath());
        applicationGroupOverviewInfo.getExecutable().setTooltip(
                StringUtils.defaultIfBlank(overview.executablePath(), "-"));
        setCollectionValue(applicationGroupOverviewInfo.getLookupStatus(), overview.lookupStatuses());

        StatsData statistics = overview.statistics();
        Map<HttpMethod, Integer> countMap = statistics.getCountMap();
        int getCount = countMap.getOrDefault(HttpMethod.GET, 0);
        int postCount = countMap.getOrDefault(HttpMethod.POST, 0);
        applicationGroupOverviewInfo.getTotalCnt().setVal(String.valueOf(statistics.getCount()));
        applicationGroupOverviewInfo.getGetCnt().setVal(String.valueOf(getCount));
        applicationGroupOverviewInfo.getPostCnt().setVal(String.valueOf(postCount));
        applicationGroupOverviewInfo.getOtherCnt().setVal(
                String.valueOf(Math.max(0, statistics.getCount() - getCount - postCount)));

        applicationGroupOverviewInfo.getTimeCost().setVal(
                statistics.getTimeCost() > 0 ? statistics.getTimeCost() + " ms" : "-");
        applicationGroupOverviewInfo.getStartTime().setVal(formatDate(statistics.getStartTime()));
        applicationGroupOverviewInfo.getEndTime().setVal(formatDate(statistics.getEndTime()));
        applicationGroupOverviewInfo.getAverageSpeed().setVal(statistics.getAverageSpeed() > 0
                ? String.format("%.2f KB/s", statistics.getAverageSpeed()) : "-");
        applicationGroupOverviewInfo.getTotalSize().setVal(statistics.getTotalSize() > 0
                ? WebUtils.getHSize(statistics.getTotalSize()) : "-");
        applicationGroupOverviewInfo.getRequestsSize().setVal(statistics.getRequestsSize() > 0
                ? WebUtils.getHSize(statistics.getRequestsSize()) : "-");
        applicationGroupOverviewInfo.getResponsesSize().setVal(statistics.getResponsesSize() > 0
                ? WebUtils.getHSize(statistics.getResponsesSize()) : "-");
        detailTabController.refreshOverviewTable();
    }

    private String formatDate(Date value) {
        return value != null && value.getTime() > 0 ? dateFormat.format(value) : "-";
    }

    private void setValue(PairEntry entry, String value) {
        entry.setVal(StringUtils.defaultIfBlank(value, "-"));
    }

    private void setCollectionValue(PairEntry entry, Collection<?> values) {
        String fullValue = values == null || values.isEmpty() ? "-" : values.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(", "));
        entry.setVal(fullValue.length() > 80 ? fullValue.substring(0, 77) + "..." : fullValue);
        entry.setTooltip(fullValue);
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
            URL url = new URI(path).toURL();
            urlPath = url.getPath();
            host = url.getHost();
            port = String.valueOf(url.getPort() == -1 ? url.getDefaultPort(): url.getPort());
            protocol = url.getProtocol().toUpperCase(Locale.ROOT);
        } catch (MalformedURLException | URISyntaxException e) {
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
        if (requestRoot == null) {
            initRequestRoot();
        }
        detailTabController.setOverviewTableRoot(requestRoot);

        String protocol = request.getProtocol() == null ? "-" : request.getProtocol();
        String url = request.getRequestUrl();
        String method = StringUtils.defaultIfBlank(request.getMethod(), "-");
        if (method.contains("UNK")) {
            method = "-";
        }
        ResponseMessage response = request.getResponse();
        String code;

        // set status-column style
        if (response == null) {
            requestOverviewInfo.getStatus().setColumnStyle(PairEntry.ColumnStyle.PENDING);
            code = "Pending";
        } else if (response.getStatus() != null && response.getStatus() == -1) {
            requestOverviewInfo.getStatus().setColumnStyle(PairEntry.ColumnStyle.ERROR);
            code = StringUtils.defaultIfBlank(response.getReasonPhrase(), "-");
        } else {
            requestOverviewInfo.getStatus().setColumnStyle(PairEntry.ColumnStyle.OK);
            String status = StringUtils.defaultIfBlank(response.getStatusStr(), "-");
            String reason = StringUtils.defaultString(response.getReasonPhrase());
            code = (status + " " + reason).strip();
        }

        // System.out.println(request.getRemoteHost() + " === " + request.getRemoteAddress());
        // basic
        requestOverviewInfo.getUrl().setVal(url);
        requestOverviewInfo.getMethod().setVal(method);
        requestOverviewInfo.getStatus().setVal(code);
        requestOverviewInfo.getProtocol().setVal(protocol);
        requestOverviewInfo.getRemoteHost().setVal(request.getRemoteHost());
        requestOverviewInfo.getRemoteAddr().setVal(request.getRemoteAddress() == null ? "-": request.getRemoteAddress());
        requestOverviewInfo.getRemotePort().setVal(String.valueOf(request.getRemotePort()));
        requestOverviewInfo.getClientHost().setVal(request.getLocalAddress());
        requestOverviewInfo.getClientPort().setVal(String.valueOf(request.getLocalPort()));

        ProcessInfo processInfo = request.getProcessInfo();
        String applicationName = processInfo == null ? null : StringUtils.firstNonBlank(
                processInfo.getApplicationName(), processInfo.getOwnerProcessName());
        String processName = processInfo == null ? null : processInfo.getOwnerProcessName();
        long pid = processInfo == null ? 0 : processInfo.getOwnerPid() > 0
                ? processInfo.getOwnerPid() : processInfo.getApplicationPid();
        String executable = processInfo == null ? null : processInfo.getApplicationExecutablePath();
        if (StringUtils.isBlank(executable) && processInfo != null) {
            executable = processInfo.getOwnerExecutablePath();
        }
        requestOverviewInfo.getApplication().setVal(StringUtils.defaultIfBlank(applicationName, "-"));
        requestOverviewInfo.getProcess().setVal(StringUtils.defaultIfBlank(processName, "-"));
        requestOverviewInfo.getProcessPid().setVal(pid > 0 ? String.valueOf(pid) : "-");
        requestOverviewInfo.getExecutable().setVal(StringUtils.defaultIfBlank(executable, "-"));
        requestOverviewInfo.getExecutable().setTooltip(StringUtils.defaultIfBlank(executable, "-"));
        requestOverviewInfo.getProcessStatus().setVal(processInfo == null || processInfo.getLookupStatus() == null
                ? ProcessInfo.LookupStatus.UNKNOWN.name() : processInfo.getLookupStatus().name());

        RequestTiming timing = RequestTiming.from(request);
        requestOverviewInfo.getTimeCost().setVal(timing.formattedTotalDuration());
        requestOverviewInfo.getRequestTime().setVal(timing.formattedRequestDuration());
        requestOverviewInfo.getRequestStart().setVal(formatTimestamp(timing.requestStart()));
        requestOverviewInfo.getRequestEnd().setVal(formatTimestamp(timing.requestEnd()));
        requestOverviewInfo.getRespTime().setVal(timing.formattedResponseDuration());
        requestOverviewInfo.getRespStart().setVal(formatTimestamp(timing.responseStart()));
        requestOverviewInfo.getRespEnd().setVal(formatTimestamp(timing.responseEnd()));

        // size
        requestOverviewInfo.getRequestSize().setVal(WebUtils.getHSize(request.getSize()));
        requestOverviewInfo.getResponseSize().setVal(response == null ? "-": WebUtils.getHSize(response.getSize()));
        requestOverviewInfo.getAverageSpeed().setVal(getSpeed(request, response, timing));

        detailTabController.refreshOverviewTable();
    }

    private String formatTimestamp(OptionalLong timestamp) {
        return timestamp.isPresent() ? dateFormat.format(new Date(timestamp.getAsLong())) : "-";
    }

    private String getSpeed(RequestMessage request, ResponseMessage response, RequestTiming timing) {
        OptionalLong totalDuration = timing.totalDuration();
        if (response == null || totalDuration.isEmpty() || totalDuration.getAsLong() <= 0
                || (request.getSize() == 0 && response.getSize() == 0)) {
            return "-";
        }
        long size = request.getSize() + response.getSize();
        return String.format("%.2f KB/s", (double) size / totalDuration.getAsLong());
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
        String estimatedMsg = resourceMessageProvider.getMessage("estimate.tooltip");
        TreeItem<PairEntry> reqNode = new TreeItem<>(new PairEntry("General", null));
        TreeItem<PairEntry> sourceNode = new TreeItem<>(new PairEntry(
                resourceMessageProvider.getMessage("source-section.label"), null));
        TreeItem<PairEntry> sizeNode = new TreeItem<>(new PairEntry("Size", null, estimatedMsg));
        TreeItem<PairEntry> timingNode = new TreeItem<>(new PairEntry("Timing", null, estimatedMsg));

        // basic info
        reqNode.getChildren().add(new TreeItem<>(requestOverviewInfo.getUrl()));
        reqNode.getChildren().add(new TreeItem<>(requestOverviewInfo.getMethod()));
        reqNode.getChildren().add(new TreeItem<>(requestOverviewInfo.getProtocol()));

        TreeItem<PairEntry> statusItem = new TreeItem<>(requestOverviewInfo.getStatus());
        reqNode.getChildren().add(statusItem);
        reqNode.getChildren().add(new TreeItem<>(requestOverviewInfo.getRemoteHost()));
        reqNode.getChildren().add(new TreeItem<>(requestOverviewInfo.getRemoteAddr()));
        reqNode.getChildren().add(new TreeItem<>(requestOverviewInfo.getRemotePort()));
        reqNode.getChildren().add(new TreeItem<>(requestOverviewInfo.getClientHost()));
        reqNode.getChildren().add(new TreeItem<>(requestOverviewInfo.getClientPort()));

        sourceNode.getChildren().add(new TreeItem<>(requestOverviewInfo.getApplication()));
        sourceNode.getChildren().add(new TreeItem<>(requestOverviewInfo.getProcess()));
        sourceNode.getChildren().add(new TreeItem<>(requestOverviewInfo.getProcessPid()));
        sourceNode.getChildren().add(new TreeItem<>(requestOverviewInfo.getExecutable()));
        sourceNode.getChildren().add(new TreeItem<>(requestOverviewInfo.getProcessStatus()));

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
        sourceNode.setExpanded(true);
        requestRoot.getChildren().addAll(reqNode, sourceNode, timingNode, sizeNode);
    }

    @SuppressWarnings("unchecked")
    public void initPathRoot() {
        pathRoot = new TreeItem<>();
        String estimatedMsg = resourceMessageProvider.getMessage("estimate.tooltip");
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
        TreeItem<PairEntry> timingNode = new TreeItem<>(new PairEntry("Timing", null, estimatedMsg));
        timingNode.getChildren().add(new TreeItem<>(pathOverviewInfo.getTimeCost()));
        timingNode.getChildren().add(new TreeItem<>(pathOverviewInfo.getStartTime()));
        timingNode.getChildren().add(new TreeItem<>(pathOverviewInfo.getEndTime()));
        timingNode.getChildren().add(new TreeItem<>(pathOverviewInfo.getAverageSpeed()));

        // size
        TreeItem<PairEntry> sizeNode = new TreeItem<>(new PairEntry("Size", null, estimatedMsg));
        sizeNode.getChildren().add(new TreeItem<>(pathOverviewInfo.getTotalSize()));
        sizeNode.getChildren().add(new TreeItem<>(pathOverviewInfo.getRequestsSize()));
        sizeNode.getChildren().add(new TreeItem<>(pathOverviewInfo.getResponsesSize()));

        generalNode.setExpanded(true);
        sizeNode.setExpanded(true);
        timingNode.setExpanded(true);
        pathRoot.getChildren().addAll(generalNode, timingNode, sizeNode);
    }

    @SuppressWarnings("unchecked")
    private TreeItem<PairEntry> initApplicationGroupRoot(boolean hostOverview) {
        TreeItem<PairEntry> root = new TreeItem<>();
        String estimatedMsg = resourceMessageProvider.getMessage("estimate.tooltip");
        TreeItem<PairEntry> generalNode = new TreeItem<>(new PairEntry("General", null));
        if (hostOverview) {
            generalNode.getChildren().addAll(
                    new TreeItem<>(applicationGroupOverviewInfo.getHost()),
                    new TreeItem<>(applicationGroupOverviewInfo.getApplication()),
                    new TreeItem<>(applicationGroupOverviewInfo.getProtocols()),
                    new TreeItem<>(applicationGroupOverviewInfo.getPorts()));
        } else {
            generalNode.getChildren().addAll(
                    new TreeItem<>(applicationGroupOverviewInfo.getApplication()),
                    new TreeItem<>(applicationGroupOverviewInfo.getApplicationPids()),
                    new TreeItem<>(applicationGroupOverviewInfo.getProcesses()),
                    new TreeItem<>(applicationGroupOverviewInfo.getOwnerPids()),
                    new TreeItem<>(applicationGroupOverviewInfo.getExecutable()),
                    new TreeItem<>(applicationGroupOverviewInfo.getLookupStatus()),
                    new TreeItem<>(applicationGroupOverviewInfo.getDomainCount()));
        }
        generalNode.getChildren().addAll(
                new TreeItem<>(applicationGroupOverviewInfo.getTotalCnt()),
                new TreeItem<>(applicationGroupOverviewInfo.getGetCnt()),
                new TreeItem<>(applicationGroupOverviewInfo.getPostCnt()),
                new TreeItem<>(applicationGroupOverviewInfo.getOtherCnt()));

        TreeItem<PairEntry> timingNode = new TreeItem<>(new PairEntry("Timing", null, estimatedMsg));
        timingNode.getChildren().addAll(
                new TreeItem<>(applicationGroupOverviewInfo.getTimeCost()),
                new TreeItem<>(applicationGroupOverviewInfo.getStartTime()),
                new TreeItem<>(applicationGroupOverviewInfo.getEndTime()),
                new TreeItem<>(applicationGroupOverviewInfo.getAverageSpeed()));

        TreeItem<PairEntry> sizeNode = new TreeItem<>(new PairEntry("Size", null, estimatedMsg));
        sizeNode.getChildren().addAll(
                new TreeItem<>(applicationGroupOverviewInfo.getTotalSize()),
                new TreeItem<>(applicationGroupOverviewInfo.getRequestsSize()),
                new TreeItem<>(applicationGroupOverviewInfo.getResponsesSize()));

        generalNode.setExpanded(true);
        timingNode.setExpanded(true);
        sizeNode.setExpanded(true);
        root.getChildren().addAll(generalNode, timingNode, sizeNode);
        return root;
    }
}

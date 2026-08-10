package com.catas.wicked.proxy.message;

import com.catas.wicked.common.bean.RequestCell;
import com.catas.wicked.common.bean.StatsData;
import com.catas.wicked.common.bean.TimeStatsData;
import com.catas.wicked.common.bean.message.BaseMessage;
import com.catas.wicked.common.bean.message.DeleteMessage;
import com.catas.wicked.common.bean.message.Message;
import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.bean.message.RenderMessage;
import com.catas.wicked.common.bean.message.ResponseMessage;
import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.pipeline.MessageQueue;
import com.catas.wicked.common.pipeline.Topic;
import com.catas.wicked.proxy.gui.componet.FilterableTreeItem;
import com.catas.wicked.proxy.gui.controller.ButtonBarController;
import com.catas.wicked.proxy.gui.controller.RequestViewController;
import com.catas.wicked.proxy.render.tab.OverViewTabRenderer;
import com.catas.wicked.proxy.service.RequestViewService;
import com.catas.wicked.proxy.service.record.RequestRecordStore;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.ObservableList;
import javafx.scene.control.ListView;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Slf4j
@Singleton
public class MessageService {

    public enum SelectionSource {
        TREE_VIEW,
        LIST_VIEW,
        APPLICATION_VIEW
    }

    @Inject
    private ApplicationConfig appConfig;

    @Inject
    private RequestViewService requestViewService;

    @Inject
    private MessageQueue messageQueue;

    @Inject
    private RequestRecordStore requestStore;

    @Inject
    private RequestViewController requestViewController;

    @Inject
    private ButtonBarController buttonBarController;

    @Inject
    private OverViewTabRenderer overViewTabRenderer;

    private MessageTree messageTree;
    private ApplicationMessageTree applicationMessageTree;
    private final RequestUpdateBuffer requestUpdateBuffer = new RequestUpdateBuffer();
    private final ResponseUpdateBuffer responseUpdateBuffer = new ResponseUpdateBuffer();
    private final AtomicBoolean requestViewActivated = new AtomicBoolean();
    private final UiMutationScheduler uiMutationScheduler = new UiMutationScheduler(Platform::runLater);
    private long lastQueueWarningNanos;

    @Getter
    private final SimpleIntegerProperty requestCntProperty = new SimpleIntegerProperty(0);

    @PostConstruct
    public void init() {
        // avoid circular dependency
        requestViewController.setMessageService(this);
        buttonBarController.setMessageService(this);
        overViewTabRenderer.setMessageService(this);
    }

    public void onRequestViewReady() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::onRequestViewReady);
            return;
        }
        if (!requestViewController.isRequestViewReady()
                || !requestViewActivated.compareAndSet(false, true)) {
            return;
        }
        try {
            resetMessageTree();
            // MessageQueue retains startup messages until these consumers are registered.
            messageQueue.subscribe(Topic.RECORD, this::processMsg);
            messageQueue.subscribe(Topic.UPDATE_MSG, this::processUpdate);
            log.debug("Request view is ready; message consumers activated");
        } catch (RuntimeException | Error exception) {
            requestViewActivated.set(false);
            log.error("Unable to activate request view message consumers", exception);
            throw exception;
        }
    }

    private void resetMessageTree() {
        Consumer<Runnable> uiScheduler = uiMutationScheduler.nextSession();
        messageTree = new MessageTree(
                requestViewController.getTreeRoot(), requestViewController.getReqSourceList(), uiScheduler);
        applicationMessageTree = new ApplicationMessageTree(requestViewController, uiScheduler);
        requestCntProperty.set(0);
    }

    private void refreshCntProperty() {
        requestCntProperty.set(requestCountState(
                messageTree.getCount(), !messageTree.isEmpty(), applicationMessageTree.hasGroups()));
    }

    static int requestCountState(int requestCount, boolean hasUrlStructure, boolean hasApplicationGroups) {
        if (requestCount > 0) {
            return requestCount;
        }
        return hasUrlStructure || hasApplicationGroups ? 0 : -1;
    }

    public StatsData pathStatistics(String fullPath) {
        if (StringUtils.isBlank(fullPath)) {
            return null;
        }
        StatsData statsData = new StatsData();

        TreeNode pathNode = messageTree.findNodeByPath(fullPath, null);
        if (pathNode == null) {
            log.warn("pathNode is null: {}", fullPath);
            return statsData;
        }

        List<TreeNode> leafNodeList = new ArrayList<>();
        messageTree.travel(pathNode, leafNodeList::add);

        // count
        statsData.setCount(leafNodeList.size());
        statsData.setCountMap(new HashMap<>());
        for (TreeNode node : leafNodeList) {
            if (node.getMethod() != null) {
                Integer cnt = statsData.getCountMap().computeIfAbsent(node.getMethod(), httpMethod -> 0);
                statsData.getCountMap().put(node.getMethod(), cnt + 1);
            }

            // time
            TimeStatsData requestTimeStats = node.getReqTimeStats();
            TimeStatsData respTimeStats = node.getRespTimeStats();
            statsData.addTimeCost(respTimeStats.getEndTime() - requestTimeStats.getStartTime());
            if (statsData.getStartTime() == null || statsData.getStartTime().getTime() > requestTimeStats.getStartTime()) {
                statsData.setStartTime(new Date(requestTimeStats.getStartTime()));
            }
            if (statsData.getEndTime() == null || statsData.getEndTime().getTime() < respTimeStats.getEndTime()) {
                statsData.setEndTime(new Date(requestTimeStats.getEndTime()));
            }

            // size
            statsData.addTotalSize(requestTimeStats.getSize());
            statsData.addTotalSize(respTimeStats.getSize());
            statsData.addRequestsSize(requestTimeStats.getSize());
            statsData.addResponsesSize(respTimeStats.getSize());
        }
        if (statsData.getTotalSize() > 0 && statsData.getTimeCost() > 0) {
            statsData.setAverageSpeed((double) statsData.getTotalSize() / statsData.getTimeCost());
        }

        return statsData;
    }

    /**
     * set selectionMode in treeView/listView
     * @param requestId requestId
     * @param source selection source
     */
    public void selectRequestItem(String requestId, SelectionSource source) {
        // currentRequestId is the canonical selection. Hidden views are restored
        // lazily when the user switches views, avoiding O(n) tree work per click.
    }

    public void restoreSelection(SelectionSource target) {
        String requestId = appConfig.getObservableConfig().getCurrentRequestId();
        if (requestId == null || RenderMessage.isOverviewOnly(requestId)) {
            return;
        }
        TreeNode treeNode = messageTree.requestNode(requestId);
        if (treeNode == null) {
            return;
        }
        switch (target) {
            case TREE_VIEW -> selectAttachedTreeItem(
                    requestViewController.getReqTreeView(), treeNode.getTreeItem());
            case APPLICATION_VIEW -> applicationMessageTree.select(requestId);
            case LIST_VIEW -> requestViewController.getReqListView()
                    .getSelectionModel().select(treeNode.getListItem());
        }
    }

    private static void selectAttachedTreeItem(TreeView<RequestCell> treeView, TreeItem<RequestCell> item) {
        if (treeView == null || item == null) {
            return;
        }
        TreeItem<RequestCell> root = item;
        while (root.getParent() != null) {
            root = root.getParent();
        }
        if (root != treeView.getRoot()) {
            return;
        }
        TreeItem<RequestCell> parent = item.getParent();
        while (parent != null) {
            parent.setExpanded(true);
            parent = parent.getParent();
        }
        treeView.getSelectionModel().select(item);
    }

    public ApplicationGroupOverview applicationGroupOverview(RequestCell.NodeType nodeType, String nodeKey) {
        return applicationMessageTree.overview(nodeType, nodeKey);
    }

    public void deleteApplicationItem(RequestCell requestCell) {
        if (requestCell == null) {
            return;
        }
        messageQueue.pushMsg(Topic.RECORD,
                new ApplicationDeleteMessage(requestCell.getNodeType(), requestCell.getNodeKey()));
    }

    /**
     * update info for existed requestMsg/responseMsg
     * @param msg updateMsg
     */
    private void processUpdate(Message msg) {
        if (msg instanceof RequestMessage updateMsg) {
            RequestMessage requestMessage;
            synchronized (requestUpdateBuffer) {
                requestMessage = requestStore.getMetadata(updateMsg.getRequestId());
                if (requestMessage == null) {
                    requestUpdateBuffer.defer(updateMsg);
                    return;
                }
                RequestUpdateBuffer.apply(requestMessage, updateMsg);
                requestStore.put(requestMessage);
            }
            boolean selected = StringUtils.equals(
                    appConfig.getObservableConfig().getCurrentRequestId(), requestMessage.getRequestId());
            applicationMessageTree.update(requestMessage, selected);
            messageTree.updateTransferStatus(requestMessage);
            updateTimeStats(requestMessage, requestMessage);
            requestViewService.refreshCurrentRequest(requestMessage.getRequestId());
            requestViewService.refreshCurrentApplicationGroup();
        } else if (msg instanceof ResponseMessage updateMsg) {
            RequestMessage requestMessage;
            synchronized (responseUpdateBuffer) {
                requestMessage = requestStore.getMetadata(updateMsg.getRequestId());
                if (requestMessage != null && requestMessage.getResponse() != null) {
                    ResponseUpdateBuffer.apply(requestMessage.getResponse(), updateMsg);
                    requestStore.put(requestMessage);
                } else {
                    responseUpdateBuffer.defer(updateMsg);
                    return;
                }
            }
            updateResponseStats(requestMessage);
        } else {
            log.warn("Unrecognized requestMsg");
        }
    }

    /**
     * record request and response msg
     * @param msg requestMessage/responseMessage
     */
    private void processMsg(Message msg) {
        if (msg instanceof RequestMessage requestMessage) {
            switch (requestMessage.getType()) {
                case REQUEST -> {
                    synchronized (requestUpdateBuffer) {
                        RequestUpdateBuffer.apply(
                                requestMessage, requestUpdateBuffer.drain(requestMessage.getRequestId()));
                        messageTree.add(requestMessage);
                        applicationMessageTree.add(requestMessage);
                        requestStore.put(requestMessage);
                    }
                    refreshCntProperty();
                    logPerformanceSnapshot();
                    requestViewService.refreshCurrentApplicationGroup();
                }
                // TODO: deprecated
                case REQUEST_CONTENT -> {
                    // 添加请求体
                    RequestMessage contentMsg = (RequestMessage) msg;
                    RequestMessage data = requestStore.getMetadata(contentMsg.getRequestId());
                    if (data != null) {
                        data.setBody(contentMsg.getBody());
                        requestStore.put(data);
                    }
                }
            }
        }

        if (msg instanceof ResponseMessage responseMessage) {
            switch (responseMessage.getType()) {
                case RESPONSE -> {
                    RequestMessage data;
                    synchronized (responseUpdateBuffer) {
                        data = requestStore.getMetadata(responseMessage.getRequestId());
                        if (data != null) {
                            data.setResponse(responseMessage);
                            ResponseMessage pendingUpdate =
                                    responseUpdateBuffer.drain(responseMessage.getRequestId());
                            ResponseUpdateBuffer.apply(responseMessage, pendingUpdate);
                            requestStore.put(data);
                        }
                    }
                    if (data != null) {
                        updateResponseStats(data);
                    }
                }
                // Deprecated
                case RESPONSE_CONTENT -> {
                    // 添加响应体
                    // TODO 分开resp
                    ResponseMessage respMessage = (ResponseMessage) msg;
                    RequestMessage data = requestStore.getMetadata(respMessage.getRequestId());
                    if (data != null && data.getResponse() != null) {
                        data.getResponse().setContent(respMessage.getContent());
                        requestStore.put(data);
                    }
                }
            }
        }

        if (msg instanceof DeleteMessage deleteMessage) {
            if (deleteMessage.isCleanLeaves()) {
                cleanLeaves();
            } else if (deleteMessage.isRemoveAll()){
                removeAll();
            } else {
                deleteRequest(deleteMessage);
            }
            refreshCntProperty();
        }
        if (msg instanceof ApplicationDeleteMessage deleteMessage) {
            Set<String> requestIds = applicationMessageTree.detach(
                    deleteMessage.getNodeType(), deleteMessage.getNodeKey());
            deleteDetachedApplicationRequests(requestIds);
            requestViewService.updateRequestTab(null);
            refreshCntProperty();
        }
    }

    private void logPerformanceSnapshot() {
        int count = messageTree == null ? 0 : messageTree.getCount();
        if (count == 0 || count % 1000 != 0) {
            return;
        }
        RequestRecordStore.StoreStats stats = requestStore.stats();
        int recordQueue = messageQueue.getSize(Topic.RECORD);
        int updateQueue = messageQueue.getSize(Topic.UPDATE_MSG);
        int fxMutations = uiMutationScheduler.pendingActions();
        log.debug("Request view stats: requests={}, payload={} MB/{}, evicted={}, recordQueue={}, "
                        + "updateQueue={}, fxMutations={}",
                stats.requestCount(), stats.retainedPayloadBytes() / (1024 * 1024),
                stats.payloadBudgetBytes() / (1024 * 1024), stats.evictedPayloadCount(),
                recordQueue, updateQueue, fxMutations);
        if (Math.max(Math.max(recordQueue, updateQueue), fxMutations) >= 5_000) {
            long now = System.nanoTime();
            if (now - lastQueueWarningNanos >= 30_000_000_000L) {
                lastQueueWarningNanos = now;
                log.warn("Request view queue high water: record={}, update={}, fxMutations={}",
                        recordQueue, updateQueue, fxMutations);
            }
        }
    }

    /**
     * delete request from gui and cache
     * @param deleteMessage deleteMessage
     */
    private void deleteRequest(DeleteMessage deleteMessage) {
        RequestCell requestCell = deleteMessage.getRequestCell();
        if (requestCell == null || StringUtils.isBlank(requestCell.getFullPath())) {
            return;
        }

        // TODO update requestDetailView
        // find node to delete
        String requestId = requestCell.isLeaf() ? requestCell.getRequestId() : null;
        TreeNode nodeToDelete = messageTree.findNodeByPath(requestCell.getFullPath(), requestId);
        if (nodeToDelete == null) {
            return;
        }
        log.info("Node to delete: {}", nodeToDelete.getFullPath());

        TreeItem<RequestCell> treeItemToDelete = nodeToDelete.getTreeItem();
        Platform.runLater(() -> {
            requestViewController.clearUrlTreeSelectionBeforeRemoving(treeItemToDelete);
            if (treeItemToDelete.getParent() instanceof FilterableTreeItem<?> parent) {
                ((FilterableTreeItem<RequestCell>) parent).getInternalChildren().remove(treeItemToDelete);
            }
        });

        Set<String> requestIdList = new HashSet<>();
        List<RequestCell> listItemList = new ArrayList<>();
        messageTree.travel(nodeToDelete, treeNode -> {
            requestIdList.add(treeNode.getRequestId());
            listItemList.add(treeNode.getListItem());
            if (StringUtils.equals(appConfig.getObservableConfig().getCurrentRequestId(), treeNode.getRequestId())) {
                // System.out.println("***** remove reqId: " + treeNode.getRequestId());
                requestViewService.updateRequestTab(null);
            }
        });
        // set currentRequestId to null if current path is deleted
        if (!requestCell.isLeaf()) {
            requestViewService.updateRequestTab(null);
        }

        ObservableList<RequestCell> reqSourceList = requestViewController.getReqSourceList();
        Platform.runLater(() -> reqSourceList.removeAll(listItemList));
        messageTree.delete(nodeToDelete);
        messageTree.subtractCnt(requestIdList.size());
        applicationMessageTree.remove(requestIdList, requestCell.isLeaf());
        requestUpdateBuffer.removeAll(requestIdList);
        responseUpdateBuffer.removeAll(requestIdList);

        requestStore.removeAll(requestIdList);
    }

    private void deleteDetachedApplicationRequests(Set<String> requestIds) {
        if (requestIds == null || requestIds.isEmpty()) {
            return;
        }
        List<RequestCell> listItems = new ArrayList<>();
        int removed = 0;
        Platform.runLater(requestViewController::clearSelectionsBeforeTreeMutation);
        for (String requestId : requestIds) {
            RequestMessage request = requestStore.getMetadata(requestId);
            if (request == null) {
                continue;
            }
            TreeNode node = messageTree.findNodeByPath(request.getRequestUrl(), requestId);
            if (node == null) {
                continue;
            }
            listItems.add(node.getListItem());
            TreeItem<RequestCell> item = node.getTreeItem();
            Platform.runLater(() -> {
                if (item.getParent() instanceof FilterableTreeItem<?> parent) {
                    ((FilterableTreeItem<RequestCell>) parent).getInternalChildren().remove(item);
                }
            });
            messageTree.delete(node);
            removed++;
        }
        messageTree.subtractCnt(removed);
        requestUpdateBuffer.removeAll(requestIds);
        responseUpdateBuffer.removeAll(requestIds);
        Platform.runLater(() -> requestViewController.getReqSourceList().removeAll(listItems));
        requestViewService.updateRequestTab(null);
        requestStore.removeAll(requestIds);
    }

    /**
     * delete all leaf-nodes
     */
    private void cleanLeaves() {
        Set<String> requestIdList = new HashSet<>();
        List<TreeNode> treeNodeList = new ArrayList<>();
        Platform.runLater(requestViewController::clearSelectionsBeforeTreeMutation);

        // delete leafNodes in treeView
        messageTree.travelRoot(treeNode -> {
            requestIdList.add(treeNode.getRequestId());
            // delete current leaf-node
            FilterableTreeItem<RequestCell> nodeParent =
                    (FilterableTreeItem<RequestCell>) treeNode.getParent().getTreeItem();
            treeNodeList.add(treeNode);
            Platform.runLater(() -> {
                nodeParent.getInternalChildren().remove(treeNode.getTreeItem());
            });
        });
        treeNodeList.forEach(messageTree::delete);
        messageTree.resetCnt();
        applicationMessageTree.cleanLeaves();
        requestUpdateBuffer.removeAll(requestIdList);
        responseUpdateBuffer.removeAll(requestIdList);
        requestViewService.updateRequestTab(null);

        // delete all items in listView
        ObservableList<RequestCell> reqSourceList = requestViewController.getReqSourceList();
        Platform.runLater(() -> reqSourceList.remove(0, reqSourceList.size()));

        requestStore.removeAll(requestIdList);
    }

    /**
     * remove all request data
     */
    private void removeAll() {
        Platform.runLater(() -> {
            requestViewController.clearSelectionsBeforeTreeMutation();
            requestViewController.getTreeRoot().getInternalChildren().clear();
            requestViewController.getApplicationTreeRoot().getInternalChildren().clear();
            requestViewController.getReqSourceList().clear();
        });
        resetMessageTree();
        messageTree.resetCnt();
        requestUpdateBuffer.clear();
        responseUpdateBuffer.clear();
        requestViewService.updateRequestTab(null);

        requestStore.clear();
    }

    /**
     * update timeStatsData
     */
    private void updateTimeStats(RequestMessage requestMessage, BaseMessage msg) {
        if (requestMessage == null || msg == null) {
            log.error("Update timeStatsData with null args: {}",
                    Optional.ofNullable(requestMessage).map(RequestMessage::getRequestUrl).orElse(null));
            return;
        }
        TreeNode treeNode = messageTree.findNodeByPath(requestMessage.getRequestUrl(), requestMessage.getRequestId());
        if (treeNode == null) {
            return;
        }

        // log.info("update timeStat: {}-{}, size: {}, request: {}", msg.getStartTime(), msg.getEndTime(), msg.getSize(), msg instanceof RequestMessage);
        if (msg instanceof RequestMessage) {
            messageTree.updateTimeStats(treeNode.getReqTimeStats(), msg);
        } else if (msg instanceof ResponseMessage){
            messageTree.updateTimeStats(treeNode.getRespTimeStats(), msg);
        } else {
            log.warn("Unexpected msg type");
        }
    }

    private void updateResponseStats(RequestMessage requestMessage) {
        updateTimeStats(requestMessage, requestMessage.getResponse());
        messageTree.updateTransferStatus(requestMessage);
        applicationMessageTree.updateStatus(requestMessage);
        requestViewService.refreshCurrentRequest(requestMessage.getRequestId());
        requestViewService.refreshCurrentApplicationGroup();
    }
}

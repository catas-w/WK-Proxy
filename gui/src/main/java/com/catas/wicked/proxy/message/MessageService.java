package com.catas.wicked.proxy.message;

import com.catas.wicked.common.bean.RequestCell;
import com.catas.wicked.common.bean.StatsData;
import com.catas.wicked.common.bean.TimeStatsData;
import com.catas.wicked.common.bean.message.BaseMessage;
import com.catas.wicked.common.bean.message.DeleteMessage;
import com.catas.wicked.common.bean.message.Message;
import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.bean.message.ResponseMessage;
import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.pipeline.MessageQueue;
import com.catas.wicked.common.pipeline.Topic;
import com.catas.wicked.proxy.gui.componet.FilterableTreeItem;
import com.catas.wicked.proxy.gui.controller.ButtonBarController;
import com.catas.wicked.proxy.gui.controller.RequestViewController;
import com.catas.wicked.proxy.render.tab.OverViewTabRenderer;
import com.catas.wicked.proxy.service.RequestViewService;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.ObservableList;
import javafx.scene.control.ListView;
import javafx.scene.control.TreeItem;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.ehcache.Cache;
import org.ehcache.spi.loaderwriter.BulkCacheWritingException;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
    private Cache<String, RequestMessage> requestCache;

    @Inject
    private RequestViewController requestViewController;

    @Inject
    private ButtonBarController buttonBarController;

    @Inject
    private OverViewTabRenderer overViewTabRenderer;

    private MessageTree messageTree;
    private ApplicationMessageTree applicationMessageTree;
    private final ResponseUpdateBuffer responseUpdateBuffer = new ResponseUpdateBuffer();
    private boolean synchronizingSelection;

    @Getter
    private final SimpleIntegerProperty requestCntProperty = new SimpleIntegerProperty(0);

    @PostConstruct
    public void init() {
        // TODO: use one thread-pool consumer
        messageQueue.subscribe(Topic.RECORD, this::processMsg);
        messageQueue.subscribe(Topic.UPDATE_MSG, this::processUpdate);

        // avoid circular dependency
        requestViewController.setMessageService(this);
        buttonBarController.setMessageService(this);
        overViewTabRenderer.setMessageService(this);
        resetMessageTree();
    }

    private void resetMessageTree() {
        messageTree = new MessageTree();
        messageTree.setRequestViewController(requestViewController);
        applicationMessageTree = new ApplicationMessageTree(requestViewController);
        requestCntProperty.set(0);
    }

    private void refreshCntProperty() {
        if (messageTree.isEmpty()) {
            requestCntProperty.set(-1);
        } else {
            requestCntProperty.set(messageTree.getCount());
        }
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
     * @param fromTreeView source
     */
    public void selectRequestItem(String requestId, SelectionSource source) {
        if (requestId == null || synchronizingSelection) {
            return;
        }

        synchronizingSelection = true;
        try {
            RequestMessage requestMessage = requestCache.get(requestId);
            if (requestMessage == null) {
                return;
            }
            TreeNode treeNode = messageTree.findNodeByPath(requestMessage.getRequestUrl(), requestId);
            if (treeNode == null) {
                log.error("treeNode to select is null: {}", requestId);
                return;
            }
            if (source != SelectionSource.LIST_VIEW) {
                requestViewController.getReqListView().getSelectionModel().select(treeNode.getListItem());
            }
            if (source != SelectionSource.TREE_VIEW) {
                requestViewController.getReqTreeView().getSelectionModel().select(treeNode.getTreeItem());
            }
            if (source != SelectionSource.APPLICATION_VIEW) {
                applicationMessageTree.select(requestId);
            }
        } finally {
            synchronizingSelection = false;
        }
    }

    public Set<String> getApplicationRequestIds(RequestCell requestCell) {
        return applicationMessageTree.requestIds(requestCell);
    }

    public ApplicationGroupOverview applicationGroupOverview(RequestCell.NodeType nodeType, String nodeKey) {
        ApplicationGroupSnapshot snapshot = applicationMessageTree.snapshot(nodeType, nodeKey);
        return ApplicationGroupStatistics.aggregate(snapshot, requestCache::get);
    }

    public void deleteApplicationRequests(Set<String> requestIds) {
        messageQueue.pushMsg(Topic.RECORD, new ApplicationDeleteMessage(requestIds));
    }

    /**
     * update info for existed requestMsg/responseMsg
     * @param msg updateMsg
     */
    private void processUpdate(Message msg) {
        // TODO 更新 current request
        if (msg instanceof RequestMessage updateMsg) {
            RequestMessage requestMessage = requestCache.get(updateMsg.getRequestId());
            if (requestMessage == null) {
                // TODO: avoid
                System.out.println("requestMessage is null");
                return;
            }
            requestMessage.setOversize(updateMsg.isOversize());
            requestMessage.setSize(updateMsg.getSize());
            requestMessage.setEndTime(updateMsg.getEndTime());
            if (updateMsg.getClientStatus() != null) {
                requestMessage.setClientStatus(updateMsg.getClientStatus());
            }
            if (updateMsg.getBody() != null) {
                requestMessage.setBody(updateMsg.getBody());
            }
            if (updateMsg.getHeaders() != null) {
                requestMessage.getHeaders().putAll(updateMsg.getHeaders());
            }
            if (StringUtils.isNoneEmpty(updateMsg.getRemoteAddress())) {
                requestMessage.setRemoteAddress(updateMsg.getRemoteAddress());
            }
            if (updateMsg.getProcessInfo() != null) {
                requestMessage.setProcessInfo(updateMsg.getProcessInfo());
            }
            requestCache.put(requestMessage.getRequestId(), requestMessage);
            boolean selected = StringUtils.equals(
                    appConfig.getObservableConfig().getCurrentRequestId(), requestMessage.getRequestId());
            applicationMessageTree.update(requestMessage, selected);

            if (selected) {
                Platform.runLater(() -> overViewTabRenderer.displayOverView(requestMessage));
            }

            // update time in treeNode
            updateTimeStats(requestMessage, updateMsg);
            requestViewService.refreshCurrentApplicationGroup();
        } else if (msg instanceof ResponseMessage updateMsg) {
            RequestMessage requestMessage;
            synchronized (responseUpdateBuffer) {
                requestMessage = requestCache.get(updateMsg.getRequestId());
                if (requestMessage != null && requestMessage.getResponse() != null) {
                    ResponseUpdateBuffer.apply(requestMessage.getResponse(), updateMsg);
                    requestCache.put(requestMessage.getRequestId(), requestMessage);
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
                    // put to cache
                    requestCache.put(requestMessage.getRequestId(), requestMessage);
                    messageTree.add(requestMessage);
                    applicationMessageTree.add(requestMessage);
                    refreshCntProperty();
                    requestViewService.refreshCurrentApplicationGroup();
                }
                // TODO: deprecated
                case REQUEST_CONTENT -> {
                    // 添加请求体
                    RequestMessage contentMsg = (RequestMessage) msg;
                    RequestMessage data = requestCache.get(contentMsg.getRequestId());
                    if (data != null) {
                        data.setBody(contentMsg.getBody());
                        requestCache.put(data.getRequestId(), data);
                    }
                }
            }
        }

        if (msg instanceof ResponseMessage responseMessage) {
            switch (responseMessage.getType()) {
                case RESPONSE -> {
                    RequestMessage data;
                    synchronized (responseUpdateBuffer) {
                        data = requestCache.get(responseMessage.getRequestId());
                        if (data != null) {
                            data.setResponse(responseMessage);
                            ResponseMessage pendingUpdate =
                                    responseUpdateBuffer.drain(responseMessage.getRequestId());
                            ResponseUpdateBuffer.apply(responseMessage, pendingUpdate);
                            requestCache.put(data.getRequestId(), data);
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
                    RequestMessage data = requestCache.get(respMessage.getRequestId());
                    if (data != null && data.getResponse() != null) {
                        data.getResponse().setContent(respMessage.getContent());
                        requestCache.put(data.getRequestId(), data);
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
            deleteRequestsById(deleteMessage.getRequestIds());
            refreshCntProperty();
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
        applicationMessageTree.remove(requestIdList);
        responseUpdateBuffer.removeAll(requestIdList);

        // remove requestId from ehcache
        try {
            requestCache.removeAll(requestIdList);
        } catch (BulkCacheWritingException e) {
            log.error("Error in deleting in cache.", e);
        }
    }

    private void deleteRequestsById(Set<String> requestIds) {
        if (requestIds == null || requestIds.isEmpty()) {
            return;
        }
        List<RequestCell> listItems = new ArrayList<>();
        int removed = 0;
        for (String requestId : requestIds) {
            RequestMessage request = requestCache.get(requestId);
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
        applicationMessageTree.remove(requestIds);
        responseUpdateBuffer.removeAll(requestIds);
        Platform.runLater(() -> requestViewController.getReqSourceList().removeAll(listItems));
        requestViewService.updateRequestTab(null);
        try {
            requestCache.removeAll(requestIds);
        } catch (BulkCacheWritingException e) {
            log.error("Error in deleting in cache.", e);
        }
    }

    /**
     * delete all leaf-nodes
     */
    private void cleanLeaves() {
        Set<String> requestIdList = new HashSet<>();
        List<TreeNode> treeNodeList = new ArrayList<>();

        // delete leafNodes in treeView
        messageTree.travelRoot(treeNode -> {
            requestIdList.add(treeNode.getRequestId());
            // delete current leaf-node
            FilterableTreeItem<RequestCell> nodeParent = treeNode.getParent().getTreeItem();
            treeNodeList.add(treeNode);
            Platform.runLater(() -> {
                nodeParent.getInternalChildren().remove(treeNode.getTreeItem());
            });
        });
        treeNodeList.forEach(messageTree::delete);
        messageTree.resetCnt();
        applicationMessageTree.cleanLeaves();
        responseUpdateBuffer.removeAll(requestIdList);
        requestViewService.updateRequestTab(null);

        // delete all items in listView
        ObservableList<RequestCell> reqSourceList = requestViewController.getReqSourceList();
        Platform.runLater(() -> reqSourceList.remove(0, reqSourceList.size()));

        // delete in cache
        try {
            requestCache.removeAll(requestIdList);
        } catch (BulkCacheWritingException e) {
            log.error("Error in deleting in cache.", e);
        }
    }

    /**
     * remove all request data
     */
    private void removeAll() {
        Platform.runLater(() -> {
            requestViewController.getTreeRoot().getInternalChildren().clear();
            requestViewController.getApplicationTreeRoot().getInternalChildren().clear();
            requestViewController.getReqSourceList().clear();
        });
        resetMessageTree();
        messageTree.resetCnt();
        responseUpdateBuffer.clear();
        requestViewService.updateRequestTab(null);

        try {
            requestCache.clear();
        } catch (Exception e) {
            log.error("Error in deleting in cache.", e);
        }
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
        requestViewService.refreshCurrentApplicationGroup();
    }
}

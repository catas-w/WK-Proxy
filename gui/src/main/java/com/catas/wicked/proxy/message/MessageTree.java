package com.catas.wicked.proxy.message;

import com.catas.wicked.common.bean.TimeStatsData;
import com.catas.wicked.common.bean.message.BaseMessage;
import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.bean.RequestCell;
import com.catas.wicked.proxy.gui.componet.FilterableTreeItem;
import com.catas.wicked.proxy.gui.controller.RequestViewController;
import com.catas.wicked.common.util.WebUtils;
import io.micronaut.core.util.CollectionUtils;
import io.netty.handler.codec.http.HttpMethod;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
public class MessageTree {

    private final TreeNode root = new TreeNode();

    private int count;

    private TreeNode latestNode;

    private RequestViewController requestViewController;

    public void setRequestViewController(RequestViewController requestViewController) {
        this.requestViewController = requestViewController;
    }

    /**
     * 根据 path 添加节点到树中
     * @param msg request/response entity
     */
    public void add(RequestMessage msg) {
        // create leaf node
        TreeNode node = new TreeNode();
        node.setRequestId(msg.getRequestId());
        node.setMethod(new HttpMethod(msg.getMethod()));
        node.setUrl(msg.getRequestUrl());
        node.setFullPath(msg.getRequestUrl());
        node.setLeaf(true);
        updateTimeStats(node.getReqTimeStats(), msg);

        // add node to its position
        List<String> pathSplits = WebUtils.getPathSplits(msg.getRequestUrl());
        node.setPath(pathSplits.get(pathSplits.size() - 1));
        pathSplits.remove(pathSplits.size() - 1);

        TreeNode parent = findAndCreatParentNode(root, pathSplits, 0);
        parent.getLeafChildren().add(node);
        node.setParent(parent);

        // 创建 UI
        createTreeItemUI(parent, node);
        createListItemUI(node);

        addCnt();
    }

    /**
     * 创建 tree item
     * @param parent parent node
     * @param node tree node
     */
    private void createTreeItemUI(TreeNode parent, TreeNode node) {
        if (!node.isLeaf() && node.getTreeItem() != null) {
            return;
        }
        if (parent == root && parent.getTreeItem() == null) {
            parent.setTreeItem(requestViewController.getTreeRoot());
        }
        FilterableTreeItem<RequestCell> parentTreeItem = parent.getTreeItem();
        // TreeItem<RequestCell> treeItem = new TreeItem<>();

        RequestCell requestCell = new RequestCell(node.getPath(),
                node.getMethod() == null ? "": node.getMethod().name());
        requestCell.setFullPath(node.getFullPath());
        requestCell.setLeaf(node.isLeaf());
        requestCell.setRequestId(node.getRequestId());

        // treeItem.setValue(requestCell);
        FilterableTreeItem<RequestCell> treeItem = new FilterableTreeItem<>(requestCell);
        // expand child if children size = 1
        treeItem.expandedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue && treeItem.getChildren().size() == 1) {
                for (TreeItem<RequestCell> child : treeItem.getChildren()) {
                    child.expandedProperty().set(true);
                }
            }
        });
        node.setTreeItem(treeItem);

        // define tree item order
        int index;
        if (node.isLeaf()) {
            index = parent.getPathChildren().size() + parent.getLeafChildren().size() - 1;
        } else {
            index = parent.getPathChildren().size() - 1;
        }
        Platform.runLater(() -> {
           parentTreeItem.getInternalChildren().add(index, treeItem);
        });
    }

    /**
     * 根据 path 查找并创建需要添加的父节点
     * @param parent parent node
     * @param index index
     */
    private TreeNode findAndCreatParentNode(TreeNode parent, List<String> path, int index) {
        if (index >= path.size()) {
            return parent;
        }
        String curPath = path.get(index);
        TreeNode node = parent.getPathChildren().get(curPath);
        if (node == null) {
            node = new TreeNode();
            node.setParent(parent);
            node.setPath(curPath);
            node.setFullPath(parent == root ? curPath : parent.getFullPath() + '/' + curPath);
            parent.getPathChildren().put(curPath, node);
        }
        createTreeItemUI(parent, node);
        RequestCell cell = node.getTreeItem().getValue();
        cell.setCreatedTime(System.currentTimeMillis());
        return findAndCreatParentNode(node, path, ++index);
    }

    /**
     * 创建 list-item ui
     * @param node
     */
    private void createListItemUI(TreeNode node) {
        if (node == null || !node.isLeaf()) {
            return;
        }
        RequestCell requestCell = new RequestCell(node.getUrl(),
                node.getMethod() == null ? "" : node.getMethod().name());
        requestCell.setRequestId(node.getRequestId());
        requestCell.setFullPath(node.getFullPath());
        requestCell.setLeaf(node.isLeaf());
        node.setListItem(requestCell);

        // use filterableList
        // ListView<RequestCell> reqListView = requestViewController.getReqListView();
        ObservableList<RequestCell> reqSourceList = requestViewController.getReqSourceList();
        Platform.runLater(() -> {
            // reqListView.getItems().add(requestCell);
            reqSourceList.add(requestCell);
        });
    }

    /**
     * delete request by path and id
     */
    public void delete(TreeNode node) {
        if (node == null) {
            return;
        }
        // memory leak
        if (node.isLeaf()) {
            node.getParent().getLeafChildren().remove(node);
            // subtractCnt(1);
        } else {
            node.getParent().getPathChildren().remove(node.getPath());
        }
    }

    /**
     * travel tree from specified treeNode
     * @param node start point
     * @param action function to perform for each leaf-node
     */
    public void travel(TreeNode node, Consumer<? super TreeNode> action) {
        if (node == null) {
            return;
        }

        if (node.isLeaf()) {
            action.accept(node);
            return;
        }
        // perform action for each leaf-child node
        if (node.getLeafChildren() != null && !node.getLeafChildren().isEmpty()) {
            for (TreeNode leafChild : node.getLeafChildren()) {
                action.accept(leafChild);
            }
        }

        if (node.getPathChildren() != null && !node.getPathChildren().isEmpty()) {
            for (Map.Entry<String, TreeNode> entry : node.getPathChildren().entrySet()) {
                travel(entry.getValue(), action);
            }
        }
    }

    /**
     * travel tree from rootNode
     * @param action function to perform for each leaf-node
     */
    public void travelRoot(Consumer<? super TreeNode> action) {
        travel(root, action);
    }

    /**
     * find TreeNode by full path and request id
     * @param fullPath full path
     * @param requestId requestId
     */
    public TreeNode findNodeByPath(String fullPath, String requestId) {
        List<String> pathSplits = WebUtils.getPathSplits(fullPath, false);
        return findNodeByPath(root, requestId, pathSplits, 0);
    }

    private TreeNode findNodeByPath(TreeNode parent, String requestId, List<String> pathSplits, int index) {
        if (index >= pathSplits.size() || parent == null) {
            return parent;
        }
        if (requestId != null && index == pathSplits.size() - 1) {
            // last path, find in leavesChildren
            for (TreeNode leafChild : parent.getLeafChildren()) {
                if (StringUtils.equals(requestId, leafChild.getRequestId())) {
                    return leafChild;
                }
            }
            // not find
            return null;
        }

        // find in pathChildren
        String curPath = pathSplits.get(index);
        TreeNode node = parent.getPathChildren().getOrDefault(curPath, null);
        return findNodeByPath(node, requestId, pathSplits, ++index);
    }

    public boolean isEmpty() {
        return CollectionUtils.isEmpty(root.getPathChildren()) && CollectionUtils.isEmpty(root.getLeafChildren());
    }

    void addCnt() {
        this.count += 1;
    }

    void subtractCnt(int num) {
        if (this.count > 0) {
            this.count -= num;
        }
    }

    void resetCnt() {
        this.count = 0;
    }

    int getCount() {
        return this.count;
    }

    void updateTimeStats(TimeStatsData timeStatsData, BaseMessage msg) {
        if (timeStatsData == null || msg == null) {
            return;
        }
        if (msg.getStartTime() > 0) {
            timeStatsData.setStartTime(msg.getStartTime());
        }
        if (msg.getEndTime() > timeStatsData.getEndTime()) {
            timeStatsData.setEndTime(msg.getEndTime());
        }
        if (msg.getSize() > timeStatsData.getSize()) {
            timeStatsData.setSize(msg.getSize());
        }
    }
}

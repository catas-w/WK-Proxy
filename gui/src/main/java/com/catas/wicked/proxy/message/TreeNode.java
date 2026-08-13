package com.catas.wicked.proxy.message;

import com.catas.wicked.common.bean.RequestCell;
import com.catas.wicked.common.bean.TimeStatsData;
import io.netty.handler.codec.http.HttpMethod;
import javafx.scene.control.TreeItem;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
class TreeNode {

    /**
     * request id
     */
    private String requestId;

    /**
     * request method
     */
    private HttpMethod method;

    /**
     * full url
     */
    private String url;

    /**
     * separate path
     * eg. http://google.com, host, page, 1
     */
    private String path;

    /**
     * full path
     * eg. http://google.com, http://google.com/host/page
     */
    private String fullPath;

    private TimeStatsData reqTimeStats;

    private TimeStatsData respTimeStats;

    private boolean isLeaf;

    /**
     * related tree item
     */
    private TreeItem<RequestCell> treeItem;

    /**
     * related list item
     */
    private RequestCell listItem;

    /**
     * children: path-nodes
     */
    private Map<String, TreeNode> pathChildren;

    /**
     * children: leaf-nodes
     */
    private List<TreeNode> leafChildren;

    private TreeNode parent;

    TreeNode() {
        this.pathChildren = new ConcurrentHashMap<>();
        this.leafChildren = Collections.synchronizedList(new ArrayList<>());
        this.url = "";
        this.path = "";
        this.reqTimeStats = new TimeStatsData();
        this.respTimeStats = new TimeStatsData();
    }
}

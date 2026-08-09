package com.catas.wicked.proxy.message;

import com.catas.wicked.common.bean.RequestCell;
import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.proxy.gui.componet.FilterableTreeItem;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MessageTreeLifecycleTest {

    @Test
    public void delayedUiMutationsKeepPathNodesBeforeRequestLeaves() {
        FilterableTreeItem<RequestCell> root = new FilterableTreeItem<>();
        ObservableList<RequestCell> list = FXCollections.observableArrayList();
        Deque<Runnable> pendingUiMutations = new ArrayDeque<>();
        MessageTree tree = new MessageTree(root, list, pendingUiMutations::addLast);

        tree.add(request("one", "https://example.com/first"));
        tree.add(request("two", "https://example.com/group/second"));
        tree.add(request("three", "https://second.example.com/third"));

        while (!pendingUiMutations.isEmpty()) {
            pendingUiMutations.removeFirst().run();
        }

        assertEquals(3, list.size());
        assertEquals(3, countLeaves(root));
        assertPathNodesPrecedeLeaves(root);
    }

    @Test
    public void delayedLeafMutationIsIgnoredAfterTheModelNodeWasDeleted() {
        FilterableTreeItem<RequestCell> root = new FilterableTreeItem<>();
        ObservableList<RequestCell> list = FXCollections.observableArrayList();
        Deque<Runnable> pendingUiMutations = new ArrayDeque<>();
        MessageTree tree = new MessageTree(root, list, pendingUiMutations::addLast);
        RequestMessage request = request("deleted", "https://example.com/deleted");

        tree.add(request);
        TreeNode leaf = tree.findNodeByPath(request.getRequestUrl(), request.getRequestId());
        tree.delete(leaf);
        while (!pendingUiMutations.isEmpty()) {
            pendingUiMutations.removeFirst().run();
        }

        assertTrue(list.isEmpty());
        assertEquals(0, countLeaves(root));
    }

    private static RequestMessage request(String requestId, String url) {
        RequestMessage request = new RequestMessage(url);
        request.setRequestId(requestId);
        request.setMethod("GET");
        return request;
    }

    private static int countLeaves(TreeItem<RequestCell> parent) {
        int count = 0;
        for (TreeItem<RequestCell> child : parent.getChildren()) {
            if (child.getValue() != null && child.getValue().isLeaf()) {
                count++;
            } else {
                count += countLeaves(child);
            }
        }
        return count;
    }

    private static void assertPathNodesPrecedeLeaves(TreeItem<RequestCell> parent) {
        boolean leafSeen = false;
        for (TreeItem<RequestCell> child : parent.getChildren()) {
            boolean leaf = child.getValue() != null && child.getValue().isLeaf();
            if (leaf) {
                leafSeen = true;
            } else {
                assertFalse("Path node was inserted after a request leaf", leafSeen);
                assertPathNodesPrecedeLeaves(child);
            }
        }
    }
}

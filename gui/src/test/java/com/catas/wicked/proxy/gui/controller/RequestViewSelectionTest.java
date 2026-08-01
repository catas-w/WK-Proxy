package com.catas.wicked.proxy.gui.controller;

import javafx.scene.control.TreeItem;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RequestViewSelectionTest {

    @Test
    public void detectsSelectionInsideRemovedSubtree() {
        TreeItem<String> root = new TreeItem<>("root");
        TreeItem<String> application = new TreeItem<>("application");
        TreeItem<String> host = new TreeItem<>("host");
        TreeItem<String> request = new TreeItem<>("request");
        TreeItem<String> sibling = new TreeItem<>("sibling");
        root.getChildren().addAll(application, sibling);
        application.getChildren().add(host);
        host.getChildren().add(request);

        assertTrue(RequestViewController.isDescendantOrSelf(application, request));
        assertTrue(RequestViewController.isDescendantOrSelf(host, host));
        assertFalse(RequestViewController.isDescendantOrSelf(host, sibling));
        assertFalse(RequestViewController.isDescendantOrSelf(host, null));
    }
}

package com.catas.wicked.proxy.gui.componet;

import com.catas.wicked.common.bean.RequestCell;
import com.catas.wicked.proxy.service.RequestViewService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.scene.control.ListView;
import javafx.scene.control.TreeView;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class ViewCellFactory {

    @Inject
    private RequestViewService requestViewService;

    public RequestViewTreeCell<RequestCell> createTreeCell(TreeView<RequestCell> treeView) {
        return new RequestViewTreeCell<>(treeView);
    }

    public RequestViewListCell<RequestCell> createListCell(ListView<RequestCell> listView) {
        return new RequestViewListCell<>(listView);
    }
}

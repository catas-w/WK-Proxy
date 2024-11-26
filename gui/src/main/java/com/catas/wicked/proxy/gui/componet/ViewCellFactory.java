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
        RequestViewTreeCell<RequestCell> treeCell = new RequestViewTreeCell<>(treeView);
        treeCell.setRequestViewService(requestViewService);
        // treeCell.setOnMouseClicked(event -> {
        //     if (event.getClickCount() > 1) {
        //         return;
        //     }
        //     TreeItem<RequestCell> treeItem = treeCell.getTreeItem();
        //     RequestCell requestCell = treeItem.getValue();
        //     if (requestCell != null) {
        //         if (requestCell.isLeaf()) {
        //             requestViewService.updateRequestTab(requestCell.getRequestId());
        //         } else {
        //             requestViewService.updateRequestTab(RenderMessage.PATH_MSG + requestCell.getFullPath());
        //         }
        //         event.consume();
        //     }
        // });
        return treeCell;
    }

    public RequestViewListCell<RequestCell> createListCell(ListView<RequestCell> listView) {
        RequestViewListCell<RequestCell> listCell = new RequestViewListCell<>(listView);
        listCell.setRequestViewService(requestViewService);
        // listCell.setOnMouseClicked(event -> {
        //     if (event.getClickCount() > 1) {
        //         return;
        //     }
        //     RequestCell requestCell = listCell.getItem();
        //     if (requestCell != null) {
        //         requestViewService.updateRequestTab(requestCell.getRequestId());
        //     }
        // });
        return listCell;
    }
}

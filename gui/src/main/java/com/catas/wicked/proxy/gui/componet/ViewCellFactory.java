package com.catas.wicked.proxy.gui.componet;

import com.catas.wicked.common.bean.RequestCell;
import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.proxy.service.RequestViewService;
import com.catas.wicked.proxy.service.icon.ApplicationIconService;
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

    @Inject
    private ApplicationIconService applicationIconService;

    @Inject
    private ApplicationConfig applicationConfig;

    public RequestViewTreeCell<RequestCell> createTreeCell(TreeView<RequestCell> treeView) {
        return new RequestViewTreeCell<>(treeView, applicationIconService,
                applicationConfig.getObservableConfig().showApplicationRequestCountProperty());
    }

    public RequestViewListCell<RequestCell> createListCell(ListView<RequestCell> listView) {
        return new RequestViewListCell<>(listView);
    }
}

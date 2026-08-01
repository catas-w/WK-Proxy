package com.catas.wicked.proxy.gui.componet;

import com.catas.wicked.common.bean.RequestCell;
import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.proxy.service.RequestViewService;
import com.catas.wicked.proxy.service.LocalizationService;
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

    @Inject
    private LocalizationService localization;

    public RequestViewTreeCell<RequestCell> createTreeCell(
            TreeView<RequestCell> treeView, boolean showRequestStatusIcon,
            boolean showGroupFailureCount) {
        return new RequestViewTreeCell<>(treeView, applicationIconService,
                applicationConfig.getObservableConfig().showApplicationRequestCountProperty(), localization,
                showRequestStatusIcon, showGroupFailureCount);
    }

    public RequestViewListCell<RequestCell> createListCell(
            ListView<RequestCell> listView, boolean showRequestStatusIcon) {
        return new RequestViewListCell<>(listView, localization, showRequestStatusIcon);
    }
}

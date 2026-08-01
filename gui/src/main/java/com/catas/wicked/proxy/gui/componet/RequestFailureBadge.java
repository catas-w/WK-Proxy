package com.catas.wicked.proxy.gui.componet;

import com.catas.wicked.common.bean.RequestCell;
import com.catas.wicked.proxy.service.LocalizationService;
import javafx.beans.InvalidationListener;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;

final class RequestFailureBadge extends Label {

    private final LocalizationService localization;
    private final Tooltip tooltip = new Tooltip();
    private final InvalidationListener listener = observable -> refresh();
    private RequestCell requestCell;

    RequestFailureBadge(LocalizationService localization) {
        this.localization = localization;
        getStyleClass().add("request-failure-count");
        setMinWidth(USE_PREF_SIZE);
        Tooltip.install(this, tooltip);
        localization.languageProperty().addListener(listener);
        refresh();
    }

    void bind(RequestCell cell) {
        if (requestCell == cell) {
            refresh();
            return;
        }
        unbind();
        requestCell = cell;
        if (cell != null) {
            cell.failedCountProperty().addListener(listener);
        }
        refresh();
    }

    void unbind() {
        if (requestCell != null) {
            requestCell.failedCountProperty().removeListener(listener);
            requestCell = null;
        }
        refresh();
    }

    private void refresh() {
        int count = requestCell == null ? 0 : requestCell.getFailedCount();
        boolean displayed = count > 0;
        setVisible(displayed);
        setManaged(displayed);
        setText(displayed ? "! " + count : "");
        tooltip.setText(displayed
                ? localization.getMessage("request-status.failed-count", count) : "");
        setAccessibleText(tooltip.getText());
    }
}

package com.catas.wicked.proxy.gui.componet;

import com.catas.wicked.common.bean.RequestCell;
import com.catas.wicked.proxy.service.LocalizationService;
import javafx.beans.InvalidationListener;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import org.apache.commons.lang3.StringUtils;
import org.kordamp.ikonli.javafx.FontIcon;

final class RequestStatusIndicator extends StackPane {

    private final LocalizationService localization;
    private final FontIcon icon = new FontIcon();
    private final Tooltip tooltip = new Tooltip();
    private final InvalidationListener statusListener = observable -> refresh();
    private RequestCell requestCell;

    RequestStatusIndicator(LocalizationService localization) {
        this.localization = localization;
        getStyleClass().add("request-status-indicator");
        setMinSize(16, 16);
        setPrefSize(16, 16);
        setMaxSize(16, 16);
        setMouseTransparent(false);
        icon.setIconSize(11);
        getChildren().add(icon);
        Tooltip.install(this, tooltip);
        localization.languageProperty().addListener(statusListener);
    }

    void bind(RequestCell cell) {
        if (requestCell == cell) {
            refresh();
            return;
        }
        unbind();
        requestCell = cell;
        if (cell != null) {
            cell.transferStateProperty().addListener(statusListener);
            cell.transferStatusKeyProperty().addListener(statusListener);
            cell.transferStatusDetailProperty().addListener(statusListener);
        }
        refresh();
    }

    void unbind() {
        if (requestCell != null) {
            requestCell.transferStateProperty().removeListener(statusListener);
            requestCell.transferStatusKeyProperty().removeListener(statusListener);
            requestCell.transferStatusDetailProperty().removeListener(statusListener);
            requestCell = null;
        }
    }

    private void refresh() {
        getStyleClass().removeAll("pending", "success", "failed");
        if (requestCell == null) {
            setVisible(false);
            return;
        }
        setVisible(true);
        RequestCell.TransferState state = requestCell.getTransferState();
        switch (state) {
            case SUCCESS -> {
                getStyleClass().add("success");
                icon.setIconLiteral("fas-check-circle");
            }
            case FAILED -> {
                getStyleClass().add("failed");
                icon.setIconLiteral("fas-exclamation-circle");
            }
            default -> {
                getStyleClass().add("pending");
                icon.setIconLiteral("far-circle");
            }
        }
        String message = localization.getMessage(requestCell.getTransferStatusKey());
        if (StringUtils.isNotBlank(requestCell.getTransferStatusDetail())) {
            message += "\n" + requestCell.getTransferStatusDetail();
        }
        tooltip.setText(message);
        setAccessibleText(localization.getMessage("request-status.accessible",
                localization.getMessage("request-status." + state.name().toLowerCase()), message));
    }
}

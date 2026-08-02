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
    private final InvalidationListener stateListener = observable -> {
        refreshVisualState();
        refreshDescription();
    };
    private final InvalidationListener descriptionListener = observable -> refreshDescription();
    private RequestCell requestCell;
    private RequestCell.TransferState renderedState;

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
        localization.languageProperty().addListener(descriptionListener);
    }

    void bind(RequestCell cell) {
        if (requestCell == cell) {
            return;
        }
        unbind();
        requestCell = cell;
        if (cell != null) {
            cell.transferStateProperty().addListener(stateListener);
            cell.transferStatusKeyProperty().addListener(descriptionListener);
            cell.transferStatusDetailProperty().addListener(descriptionListener);
        }
        refreshVisualState();
        refreshDescription();
    }

    void unbind() {
        if (requestCell != null) {
            requestCell.transferStateProperty().removeListener(stateListener);
            requestCell.transferStatusKeyProperty().removeListener(descriptionListener);
            requestCell.transferStatusDetailProperty().removeListener(descriptionListener);
            requestCell = null;
        }
        setVisible(false);
    }

    private void refreshVisualState() {
        if (requestCell == null) {
            setVisible(false);
            return;
        }
        setVisible(true);
        RequestCell.TransferState state = requestCell.getTransferState();
        if (state == renderedState) {
            return;
        }

        getStyleClass().removeAll("pending", "success", "failed");
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
        renderedState = state;
    }

    private void refreshDescription() {
        if (requestCell == null) {
            tooltip.setText("");
            setAccessibleText("");
            return;
        }
        RequestCell.TransferState state = requestCell.getTransferState();
        String message = localization.getMessage(requestCell.getTransferStatusKey());
        if (StringUtils.isNotBlank(requestCell.getTransferStatusDetail())) {
            message += "\n" + requestCell.getTransferStatusDetail();
        }
        tooltip.setText(message);
        setAccessibleText(localization.getMessage("request-status.accessible",
                localization.getMessage("request-status." + state.name().toLowerCase()), message));
    }
}

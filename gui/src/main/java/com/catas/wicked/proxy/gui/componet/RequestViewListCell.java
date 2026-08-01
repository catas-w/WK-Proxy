package com.catas.wicked.proxy.gui.componet;

import com.catas.wicked.common.bean.RequestCell;
import com.catas.wicked.proxy.service.LocalizationService;
import javafx.animation.FadeTransition;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.apache.commons.lang3.StringUtils;

public class RequestViewListCell<T> extends ListCell<T> {

    private HBox hbox;
    private StackPane selectedPane = new StackPane();
    private Label methodLabel;
    private Label pathLabel;
    private RequestStatusIndicator statusIndicator;
    private FadeTransition fadeTransition;
    private RequestCell requestCell;
    private final boolean showRequestStatusIcon;
    private final static String DEFAULT_STYLE_CLASS = "req-list-cell";

    public RequestViewListCell(ListView<RequestCell> listView, LocalizationService localization,
                               boolean showRequestStatusIcon) {
        this.showRequestStatusIcon = showRequestStatusIcon;
        this.getStyleClass().add(DEFAULT_STYLE_CLASS);
        selectedPane.getStyleClass().add("req-cell-bar");
        selectedPane.setMouseTransparent(true);
        if (showRequestStatusIcon) {
            statusIndicator = new RequestStatusIndicator(localization);
        }
    }

    /**
     * play animation
     */
    private void triggerFade() {
        if (fadeTransition == null) {
            fadeTransition = new FadeTransition();
            fadeTransition.setNode(selectedPane);
            fadeTransition.setDuration(Duration.millis(750));
            fadeTransition.setCycleCount(1);
            fadeTransition.setAutoReverse(true);
            fadeTransition.setFromValue(1.0);
            fadeTransition.setToValue(0.0);
        }
        fadeTransition.play();
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        if (!getChildren().contains(selectedPane)) {
            getChildren().add(0, selectedPane);
        }
        selectedPane.resizeRelocate(0, 0, getWidth(), getHeight());
        selectedPane.setVisible(true);
        selectedPane.setOpacity(0);
    }

    @Override
    protected void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);
        updateDisplay(item, empty);
        setMouseTransparent(item == null || empty);
    }

    private void createHBox(RequestCell cell) {
        hbox = new HBox(3);
        hbox.getStyleClass().add("req-graphic-box");
        hbox.prefWidthProperty().bind(widthProperty().subtract(12));
        hbox.setMinWidth(0);
        pathLabel = new Label();
        pathLabel.getStyleClass().add("req-path-label");
        pathLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        pathLabel.setMinWidth(0);
        pathLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(pathLabel, Priority.ALWAYS);
        if (cell.isOnCreated()) {
            triggerFade();
        }
        this.requestCell = cell;
    }

    private void updateDisplay(T item, boolean empty) {
        if (item == null || empty) {
            if (statusIndicator != null) {
                statusIndicator.unbind();
            }
            requestCell = null;
            setText(null);
            setGraphic(null);
        } else {
            if (item instanceof RequestCell requestCell) {
                if (methodLabel == null) {
                    methodLabel = new Label(requestCell.getMethod());
                    methodLabel.getStyleClass().add("req-method-label");
                    methodLabel.getStyleClass().add(requestCell.getStyleClass());
                    methodLabel.setMinWidth(Region.USE_PREF_SIZE);
                } else {
                    if (!StringUtils.equals(requestCell.getMethod(), methodLabel.getText())) {
                        methodLabel.setText(requestCell.getMethod());
                        methodLabel.getStyleClass().removeIf(styleClass -> styleClass.startsWith("method-label"));
                        if (!methodLabel.getStyleClass().contains(requestCell.getStyleClass())) {
                            methodLabel.getStyleClass().add(requestCell.getStyleClass());
                        }
                    }
                }

                if (hbox == null) {
                    createHBox(requestCell);
                    hbox.getChildren().setAll(RequestLeafLayout.elements(
                            showRequestStatusIcon, statusIndicator, methodLabel, pathLabel));
                } else if (this.requestCell != requestCell && requestCell.isOnCreated()) {
                    triggerFade();
                }
                this.requestCell = requestCell;
                pathLabel.setText(requestCell.getPath());
                if (statusIndicator != null) {
                    statusIndicator.bind(requestCell);
                }
                setText(null);
                setGraphic(hbox);
            }
        }
    }
}

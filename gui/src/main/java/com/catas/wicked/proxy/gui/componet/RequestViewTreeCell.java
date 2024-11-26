package com.catas.wicked.proxy.gui.componet;

import com.catas.wicked.common.bean.RequestCell;
import com.catas.wicked.proxy.service.RequestViewService;
import javafx.animation.FadeTransition;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.apache.commons.lang3.StringUtils;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.javafx.Icon;

import java.lang.ref.WeakReference;

public class RequestViewTreeCell<T> extends TreeCell<T> {

    private HBox hbox;
    private StackPane pathStackPane = new StackPane();
    /**
     * display animation
     */
    private StackPane selectedPane = new StackPane();
    /**
     * display on method
     */
    private Label methodLabel;
    /**
     * displayPath
     */
    private Label pathLabel;
    /**
     * display on pathIcon
     */
    private FontIcon pathIcon;
    private FadeTransition fadeTransition;
    private FadeTransition showTransition;
    private RequestViewService requestViewService;

    private InvalidationListener treeItemGraphicInvalidationListener = observable -> updateDisplay(getItem(),
            isEmpty());
    private WeakInvalidationListener weakTreeItemGraphicListener = new WeakInvalidationListener(
            treeItemGraphicInvalidationListener);

    private WeakReference<TreeItem<T>> treeItemRef;

    public RequestViewTreeCell(TreeView<RequestCell> treeView) {
        // selectedPane.getStyleClass().add("req-cell-bar");
        // selectedPane.setMouseTransparent(true);

        final InvalidationListener treeItemInvalidationListener = observable -> {
            TreeItem<T> oldTreeItem = treeItemRef == null ? null : treeItemRef.get();
            if (oldTreeItem != null) {
                oldTreeItem.graphicProperty().removeListener(weakTreeItemGraphicListener);
            }

            TreeItem<T> newTreeItem = getTreeItem();
            if (newTreeItem != null) {
                newTreeItem.graphicProperty().addListener(weakTreeItemGraphicListener);
                treeItemRef = new WeakReference<>(newTreeItem);
            }
        };
        final WeakInvalidationListener weakTreeItemListener = new WeakInvalidationListener(treeItemInvalidationListener);
        treeItemProperty().addListener(weakTreeItemListener);
        if (getTreeItem() != null) {
            getTreeItem().graphicProperty().addListener(weakTreeItemGraphicListener);
        }
    }

    public void setRequestViewService(RequestViewService requestViewService) {
        this.requestViewService = requestViewService;
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
    }

    /**
     * play animation
     */
    private void triggerFade() {
        if (this.fadeTransition == null) {
            this.fadeTransition = new FadeTransition();
            this.fadeTransition.setNode(selectedPane);
            this.fadeTransition.setDuration(Duration.millis(750));
            this.fadeTransition.setCycleCount(1);
            this.fadeTransition.setAutoReverse(true);
            this.fadeTransition.setFromValue(1.0);
            this.fadeTransition.setToValue(0.0);
        }
        // showTransition.play();
        this.fadeTransition.play();
    }

    private void createOrUpdateHBox(RequestCell requestCell) {
        if (hbox == null) {
            hbox = new HBox(3);
            hbox.getStyleClass().add("req-graphic-box");

            pathLabel = new Label();
            pathLabel.getStyleClass().add("req-path-label");

            selectedPane.getStyleClass().add("req-cell-bar");
            pathStackPane.getChildren().add(selectedPane);
            pathStackPane.getChildren().add(pathLabel);

            hbox.getChildren().add(0, pathStackPane);
        }

        if (requestCell.getPath() != null && !StringUtils.equals(requestCell.getPath(), pathLabel.getText())) {
            pathLabel.setText(requestCell.getPath());
        }
        if (requestCell.isLeaf()) {
            hbox.getStyleClass().add("req-leaf");
            if (methodLabel == null) {
                methodLabel = new Label(requestCell.getMethod());
                methodLabel.getStyleClass().add("req-method-label");
                methodLabel.getStyleClass().add(requestCell.getStyleClass());
            } else {
                if (!StringUtils.equals(requestCell.getMethod(), methodLabel.getText())) {
                    methodLabel.setText(requestCell.getMethod());
                    methodLabel.getStyleClass().removeIf(styleClass -> styleClass.startsWith("method-label"));
                    if (!methodLabel.getStyleClass().contains(requestCell.getStyleClass())) {
                        methodLabel.getStyleClass().add(requestCell.getStyleClass());
                    }
                }
            }
            hbox.getChildren().removeIf(node -> node instanceof Icon);
            if (!hbox.getChildren().contains(methodLabel)) {
                hbox.getChildren().add(0, methodLabel);
            }
        } else {
            if (pathIcon == null) {
                pathIcon = new FontIcon();
                pathIcon.getStyleClass().add("req-icon");
                // pathIcon.setIconColor(Color.valueOf("#8C9C9E"));
                pathIcon.setIconSize(14);
            }
            if (requestCell.getPath() != null && requestCell.getPath().startsWith("http")) {
                pathIcon.setIconLiteral("fas-globe-africa");
            } else {
                pathIcon.setIconLiteral("fas-folder-minus");
            }
            // icon.getStyleClass().add("request-path-icon");
            hbox.getChildren().removeIf(node -> node == methodLabel);
            if (!hbox.getChildren().contains(pathIcon)) {
                hbox.getChildren().add(0, pathIcon);
            }
        }
        if (requestCell.isOnCreated()) {
            // TODO efficiency
            triggerFade();
        }
    }

    private void updateDisplay(T item, boolean empty) {
        if (item == null || empty) {
            // hbox = null;
            setText(null);
            setGraphic(null);
            if (this.showTransition != null) {
                this.showTransition.stop();
            }
            if (this.fadeTransition != null) {
                this.fadeTransition.stop();
            }
            // selectedPane.setVisible(false);
        } else {
            if (item instanceof RequestCell requestCell) {
                setText(requestCell.getPath());

                createOrUpdateHBox(requestCell);
                setGraphic(hbox);
            }
        }
    }

    @Override
    protected void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);
        updateDisplay(item, empty);
        setMouseTransparent(item == null || empty);
    }
}

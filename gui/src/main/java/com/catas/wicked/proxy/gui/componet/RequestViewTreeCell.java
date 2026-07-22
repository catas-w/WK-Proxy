package com.catas.wicked.proxy.gui.componet;

import com.catas.wicked.common.bean.RequestCell;
import com.catas.wicked.proxy.service.icon.ApplicationIconService;
import javafx.application.Platform;
import javafx.animation.FadeTransition;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.image.ImageView;
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
    private final ApplicationIconService applicationIconService;
    private String pendingApplicationIconKey;

    private InvalidationListener treeItemGraphicInvalidationListener = observable -> updateDisplay(getItem(),
            isEmpty());
    private WeakInvalidationListener weakTreeItemGraphicListener = new WeakInvalidationListener(
            treeItemGraphicInvalidationListener);

    private WeakReference<TreeItem<T>> treeItemRef;

    public RequestViewTreeCell(TreeView<RequestCell> treeView, ApplicationIconService applicationIconService) {
        this.applicationIconService = applicationIconService;

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
        this.fadeTransition.play();
    }

    private void createOrUpdateHBox(RequestCell requestCell) {
        if (hbox == null) {
            hbox = new HBox(3);
            hbox.getStyleClass().add("req-graphic-box");
            hbox.prefWidthProperty().bind(widthProperty().subtract(35));
            hbox.setMinWidth(0);

            pathLabel = new Label();
            pathLabel.getStyleClass().add("req-path-label");

            selectedPane.getStyleClass().add("req-cell-bar");
            pathStackPane.getChildren().add(selectedPane);
            pathStackPane.getChildren().add(pathLabel);
        }

        hbox.getChildren().clear();
        hbox.getStyleClass().remove("req-application-row");
        setTooltip(null);

        if (requestCell.getPath() != null && !StringUtils.equals(requestCell.getPath(), pathLabel.getText())) {
            pathLabel.setText(requestCell.getPath());
        }
        pathLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        pathLabel.setMaxWidth(Double.MAX_VALUE);

        if (requestCell.getNodeType() == RequestCell.NodeType.APPLICATION) {
            String iconLiteral = switch (requestCell.getNodeKey()) {
                case "__identifying__" -> "fas-search";
                case "__unknown__" -> "fas-question-circle";
                default -> "fas-window-maximize";
            };
            FontIcon applicationIcon = new FontIcon(iconLiteral);
            applicationIcon.getStyleClass().add("application-icon");
            Label primary = new Label(requestCell.getPath());
            primary.getStyleClass().add("application-name");
            primary.setTextOverrun(OverrunStyle.ELLIPSIS);
            Label secondary = new Label(requestCell.getSecondaryText());
            secondary.getStyleClass().add("application-secondary");
            secondary.setTextOverrun(OverrunStyle.ELLIPSIS);
            VBox labels = new VBox(0, primary, secondary);
            labels.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(labels, Priority.ALWAYS);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Label count = countLabel(requestCell);
            hbox.getStyleClass().add("req-application-row");
            hbox.getChildren().setAll(applicationIcon, labels, spacer, count);
            loadApplicationIcon(requestCell, applicationIcon);
            if (StringUtils.isNotBlank(requestCell.getStatusText())) {
                setTooltip(new Tooltip(requestCell.getStatusText()));
            }
            return;
        }

        if (requestCell.getNodeType() == RequestCell.NodeType.HOST) {
            FontIcon hostIcon = new FontIcon("fas-globe-africa");
            hostIcon.getStyleClass().add("req-icon");
            Region spacer = new Region();
            HBox.setHgrow(pathStackPane, Priority.ALWAYS);
            HBox.setHgrow(spacer, Priority.ALWAYS);
            hbox.getChildren().setAll(hostIcon, pathStackPane, spacer, countLabel(requestCell));
            return;
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
            HBox.setHgrow(pathStackPane, Priority.ALWAYS);
            hbox.getChildren().setAll(methodLabel, pathStackPane);
        } else {
            if (pathIcon == null) {
                pathIcon = new FontIcon();
                pathIcon.getStyleClass().add("req-icon");
                pathIcon.setIconSize(14);
            }
            if (requestCell.getPath() != null && requestCell.getPath().startsWith("http")) {
                pathIcon.setIconLiteral("fas-globe-africa");
            } else {
                pathIcon.setIconLiteral("fas-folder-minus");
            }
            // icon.getStyleClass().add("request-path-icon");
            HBox.setHgrow(pathStackPane, Priority.ALWAYS);
            hbox.getChildren().setAll(pathIcon, pathStackPane);
        }
        if (requestCell.isOnCreated()) {
            // TODO efficiency
            triggerFade();
        }
    }

    private void loadApplicationIcon(RequestCell requestCell, FontIcon fallbackIcon) {
        if (requestCell.getProcessInfo() == null || "__identifying__".equals(requestCell.getNodeKey())
                || "__unknown__".equals(requestCell.getNodeKey())) {
            pendingApplicationIconKey = null;
            return;
        }
        String requestKey = requestCell.getNodeKey();
        pendingApplicationIconKey = requestKey;
        applicationIconService.load(requestCell.getProcessInfo()).thenAccept(icon -> icon.ifPresent(image ->
                Platform.runLater(() -> {
                    RequestCell current = getItem() instanceof RequestCell cell ? cell : null;
                    if (current == requestCell && requestKey.equals(pendingApplicationIconKey)
                            && requestKey.equals(current.getNodeKey()) && hbox != null
                            && hbox.getChildren().contains(fallbackIcon)) {
                        ImageView imageView = new ImageView(image);
                        imageView.setFitWidth(20);
                        imageView.setFitHeight(20);
                        imageView.setPreserveRatio(true);
                        imageView.setSmooth(true);
                        int index = hbox.getChildren().indexOf(fallbackIcon);
                        hbox.getChildren().set(index, imageView);
                    }
                })));
    }

    private Label countLabel(RequestCell requestCell) {
        Label count = new Label(String.valueOf(requestCell.getCount()));
        count.getStyleClass().add("request-count");
        return count;
    }

    private void updateDisplay(T item, boolean empty) {
        if (item == null || empty) {
            pendingApplicationIconKey = null;
            setText(null);
            setGraphic(null);
            if (this.fadeTransition != null) {
                this.fadeTransition.stop();
            }
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

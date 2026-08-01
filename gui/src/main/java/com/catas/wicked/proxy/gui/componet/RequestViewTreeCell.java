package com.catas.wicked.proxy.gui.componet;

import com.catas.wicked.common.bean.RequestCell;
import com.catas.wicked.proxy.service.icon.ApplicationIconService;
import com.catas.wicked.proxy.service.LocalizationService;
import javafx.application.Platform;
import javafx.animation.FadeTransition;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.value.ObservableBooleanValue;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.apache.commons.lang3.StringUtils;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.javafx.Icon;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Optional;

public class RequestViewTreeCell<T> extends TreeCell<T> {

    private static final String DEFAULT_APPLICATION_ICON = "fas-window-maximize";
    private static final int APPLICATION_FALLBACK_ICON_SIZE = 22;
    private static final int DEFAULT_APPLICATION_ICON_SIZE = 22;

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
    private FontIcon hostIcon;
    private FadeTransition fadeTransition;
    private final ApplicationIconService applicationIconService;
    private final ObservableBooleanValue showApplicationRequestCount;
    private String pendingApplicationIconKey;
    private String displayedApplicationIconKey;
    private StackPane applicationIconContainer;
    private FontIcon applicationFallbackIcon;
    private boolean applicationRow;
    private Label applicationNameLabel;
    private Label applicationSecondaryLabel;
    private Label applicationCountLabel;
    private HBox applicationTitle;
    private VBox applicationLabels;
    private RequestCell boundApplicationCell;
    private Label groupCountLabel;
    private RequestCell boundGroupCountCell;
    private RequestStatusIndicator requestStatusIndicator;
    private RequestFailureBadge requestFailureBadge;
    private final LocalizationService localization;
    private final boolean showRequestStatusIcon;
    private final boolean showGroupFailureCount;

    private InvalidationListener treeItemGraphicInvalidationListener = observable -> updateDisplay(getItem(),
            isEmpty());
    private WeakInvalidationListener weakTreeItemGraphicListener = new WeakInvalidationListener(
            treeItemGraphicInvalidationListener);
    private final InvalidationListener requestCountVisibilityListener =
            observable -> updateDisplay(getItem(), isEmpty());
    private final WeakInvalidationListener weakRequestCountVisibilityListener =
            new WeakInvalidationListener(requestCountVisibilityListener);

    private WeakReference<TreeItem<T>> treeItemRef;

    public RequestViewTreeCell(TreeView<RequestCell> treeView, ApplicationIconService applicationIconService,
                               ObservableBooleanValue showApplicationRequestCount,
                               LocalizationService localization, boolean showRequestStatusIcon,
                               boolean showGroupFailureCount) {
        this.applicationIconService = applicationIconService;
        this.showApplicationRequestCount = showApplicationRequestCount;
        this.localization = localization;
        this.showRequestStatusIcon = showRequestStatusIcon;
        this.showGroupFailureCount = showGroupFailureCount;
        this.showApplicationRequestCount.addListener(weakRequestCountVisibilityListener);

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
        Node disclosureNode = lookup(".tree-disclosure-node");
        if (disclosureNode == null) {
            return;
        }

        // TreeCellSkin can reposition a reused disclosure node. Always calculate
        // an absolute offset from its un-translated position to the application logo.
        disclosureNode.setTranslateY(0);
        if (applicationRow && applicationIconContainer != null
                && applicationIconContainer.getParent() != null) {
            Bounds disclosureBounds = disclosureNode.localToScene(disclosureNode.getBoundsInLocal());
            Bounds iconBounds = applicationIconContainer.localToScene(applicationIconContainer.getBoundsInLocal());
            if (disclosureBounds != null && iconBounds != null) {
                double iconCenter = iconBounds.getMinY() + iconBounds.getHeight() / 2;
                double disclosureCenter = disclosureBounds.getMinY() + disclosureBounds.getHeight() / 2;
                double translateY = Math.rint(iconCenter - disclosureCenter);
                if (Double.isFinite(translateY)) {
                    disclosureNode.setTranslateY(translateY);
                }
            }
        }
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
            pathStackPane.setMinWidth(0);
        }

        hbox.getStyleClass().remove("req-application-row");
        applicationRow = requestCell.getNodeType() == RequestCell.NodeType.APPLICATION;
        setTooltip(null);

        if (requestCell.getPath() != null && !StringUtils.equals(requestCell.getPath(), pathLabel.getText())) {
            pathLabel.setText(requestCell.getPath());
        }
        pathLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        pathLabel.setMaxWidth(Double.MAX_VALUE);
        pathLabel.setGraphic(null);

        if (requestCell.getNodeType() == RequestCell.NodeType.APPLICATION) {
            unbindGroupCount();
            unbindRequestStatus();
            String iconLiteral = switch (requestCell.getNodeKey()) {
                case "__identifying__" -> "fas-search";
                case "__unknown__" -> "fas-question-circle";
                default -> DEFAULT_APPLICATION_ICON;
            };
            StackPane iconContainer = applicationIcon(requestCell, iconLiteral);
            createApplicationLabels();
            bindApplicationLabels(requestCell);
            if (showApplicationRequestCount.get()) {
                setChildrenIfChanged(applicationTitle, applicationNameLabel, applicationCountLabel);
            } else {
                setChildrenIfChanged(applicationTitle, applicationNameLabel);
            }
            hbox.getStyleClass().add("req-application-row");
            if (showGroupFailureCount) {
                setChildrenIfChanged(hbox, iconContainer, applicationLabels, failureBadge(requestCell));
            } else {
                unbindFailureBadge();
                setChildrenIfChanged(hbox, iconContainer, applicationLabels);
            }
            if (StringUtils.isNotBlank(requestCell.getStatusText())) {
                setTooltip(new Tooltip(requestCell.getStatusText()));
            }
            return;
        }

        unbindApplicationLabels();
        if (requestCell.getNodeType() == RequestCell.NodeType.HOST) {
            unbindRequestStatus();
            if (hostIcon == null) {
                hostIcon = new FontIcon("fas-globe-africa");
                hostIcon.getStyleClass().add("req-icon");
                hostIcon.setIconSize(14);
            }
            HBox.setHgrow(pathStackPane, Priority.ALWAYS);
            if (showApplicationRequestCount.get()) {
                pathLabel.setGraphic(countLabel(requestCell));
                pathLabel.setContentDisplay(ContentDisplay.RIGHT);
                pathLabel.setGraphicTextGap(4);
            } else {
                unbindGroupCount();
            }
            if (showGroupFailureCount) {
                setChildrenIfChanged(hbox, hostIcon, pathStackPane, failureBadge(requestCell));
            } else {
                unbindFailureBadge();
                setChildrenIfChanged(hbox, hostIcon, pathStackPane);
            }
            return;
        }

        unbindGroupCount();
        unbindFailureBadge();
        if (requestCell.isLeaf()) {
            hbox.getStyleClass().add("req-leaf");
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
            HBox.setHgrow(pathStackPane, Priority.ALWAYS);
            if (showRequestStatusIcon) {
                RequestStatusIndicator statusIndicator = statusIndicator(requestCell);
                setChildrenIfChanged(hbox, RequestLeafLayout
                        .elements(true, statusIndicator, methodLabel, pathStackPane)
                        .toArray(Node[]::new));
            } else {
                unbindRequestStatus();
                setChildrenIfChanged(hbox, RequestLeafLayout
                        .elements(false, null, methodLabel, pathStackPane)
                        .toArray(Node[]::new));
            }
        } else {
            unbindRequestStatus();
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
            setChildrenIfChanged(hbox, pathIcon, pathStackPane);
        }
        if (requestCell.isOnCreated()) {
            // TODO efficiency
            triggerFade();
        }
    }

    private StackPane applicationIcon(RequestCell requestCell, String fallbackIconLiteral) {
        if (applicationIconContainer == null) {
            applicationFallbackIcon = new FontIcon();
            applicationFallbackIcon.getStyleClass().add("application-icon");
            applicationIconContainer = new StackPane();
            applicationIconContainer.setMinSize(30, 30);
            applicationIconContainer.setPrefSize(30, 30);
            applicationIconContainer.setMaxSize(30, 30);
        }

        applicationFallbackIcon.setIconSize(DEFAULT_APPLICATION_ICON.equals(fallbackIconLiteral)
                ? DEFAULT_APPLICATION_ICON_SIZE : APPLICATION_FALLBACK_ICON_SIZE);
        String applicationKey = requestCell.getNodeKey();
        boolean imageVisible = applicationIconContainer.getChildren().stream()
                .anyMatch(ImageView.class::isInstance);
        if (ApplicationIconRenderState.shouldLoad(applicationKey, displayedApplicationIconKey, imageVisible)) {
            boolean applicationChanged = !StringUtils.equals(applicationKey, displayedApplicationIconKey);
            displayedApplicationIconKey = applicationKey;
            if (applicationChanged || applicationIconContainer.getChildren().isEmpty()) {
                pendingApplicationIconKey = null;
                applicationFallbackIcon.setIconLiteral(fallbackIconLiteral);
                applicationIconContainer.getChildren().setAll(applicationFallbackIcon);
            }
            loadApplicationIcon(requestCell, applicationIconContainer, applicationFallbackIcon);
        }
        return applicationIconContainer;
    }

    private void loadApplicationIcon(RequestCell requestCell, StackPane iconContainer, FontIcon fallbackIcon) {
        if (requestCell.getProcessInfo() == null || "__identifying__".equals(requestCell.getNodeKey())
                || "__unknown__".equals(requestCell.getNodeKey())) {
            pendingApplicationIconKey = null;
            return;
        }
        String requestKey = requestCell.getNodeKey();
        pendingApplicationIconKey = requestKey;
        var iconFuture = applicationIconService.load(requestCell.getProcessInfo());
        if (iconFuture.isDone() && !iconFuture.isCompletedExceptionally()) {
            Optional<Image> cachedIcon = iconFuture.getNow(Optional.empty());
            cachedIcon.ifPresent(image -> setApplicationIcon(iconContainer, image));
            return;
        }
        iconFuture.thenAccept(icon -> icon.ifPresent(image ->
                Platform.runLater(() -> {
                    RequestCell current = getItem() instanceof RequestCell cell ? cell : null;
                    if (current == requestCell && requestKey.equals(pendingApplicationIconKey)
                            && requestKey.equals(current.getNodeKey()) && hbox != null
                            && hbox.getChildren().contains(iconContainer)
                            && iconContainer.getChildren().contains(fallbackIcon)) {
                        setApplicationIcon(iconContainer, image);
                    }
                })));
    }

    private void setApplicationIcon(StackPane iconContainer, Image image) {
        ImageView imageView = new ImageView(image);
        imageView.setFitWidth(30);
        imageView.setFitHeight(30);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        iconContainer.getChildren().setAll(imageView);
        pendingApplicationIconKey = null;
    }

    private Label countLabel(RequestCell requestCell) {
        if (groupCountLabel == null) {
            groupCountLabel = new Label();
            groupCountLabel.getStyleClass().add("application-request-count");
            groupCountLabel.setMinWidth(Region.USE_PREF_SIZE);
        }
        if (boundGroupCountCell != requestCell) {
            unbindGroupCount();
            groupCountLabel.textProperty().bind(requestCell.countProperty().asString("(%d)"));
            boundGroupCountCell = requestCell;
        }
        return groupCountLabel;
    }

    private void createApplicationLabels() {
        if (applicationLabels != null) {
            return;
        }
        applicationNameLabel = new Label();
        applicationNameLabel.getStyleClass().add("application-name");
        applicationNameLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        applicationNameLabel.setMinWidth(0);
        applicationNameLabel.setMaxWidth(Double.MAX_VALUE);

        applicationCountLabel = new Label();
        applicationCountLabel.getStyleClass().add("application-request-count");
        applicationCountLabel.setMinWidth(Region.USE_PREF_SIZE);

        applicationTitle = new HBox(4, applicationNameLabel);
        applicationTitle.setMinWidth(0);
        applicationTitle.setMaxWidth(Double.MAX_VALUE);

        applicationSecondaryLabel = new Label();
        applicationSecondaryLabel.getStyleClass().add("application-secondary");
        applicationSecondaryLabel.setTextOverrun(OverrunStyle.ELLIPSIS);

        applicationLabels = new VBox(0, applicationTitle, applicationSecondaryLabel);
        applicationLabels.setMinWidth(0);
        applicationLabels.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(applicationLabels, Priority.ALWAYS);
    }

    private void bindApplicationLabels(RequestCell requestCell) {
        if (boundApplicationCell == requestCell) {
            return;
        }
        unbindApplicationLabels();
        applicationNameLabel.textProperty().bind(requestCell.pathProperty());
        applicationSecondaryLabel.textProperty().bind(requestCell.secondaryTextProperty());
        applicationCountLabel.textProperty().bind(requestCell.countProperty().asString("(%d)"));
        boundApplicationCell = requestCell;
    }

    private void unbindApplicationLabels() {
        if (boundApplicationCell == null) {
            return;
        }
        applicationNameLabel.textProperty().unbind();
        applicationSecondaryLabel.textProperty().unbind();
        applicationCountLabel.textProperty().unbind();
        boundApplicationCell = null;
    }

    private void unbindGroupCount() {
        if (boundGroupCountCell != null) {
            groupCountLabel.textProperty().unbind();
            boundGroupCountCell = null;
        }
    }

    private RequestStatusIndicator statusIndicator(RequestCell requestCell) {
        if (requestStatusIndicator == null) {
            requestStatusIndicator = new RequestStatusIndicator(localization);
        }
        requestStatusIndicator.bind(requestCell);
        return requestStatusIndicator;
    }

    private RequestFailureBadge failureBadge(RequestCell requestCell) {
        if (requestFailureBadge == null) {
            requestFailureBadge = new RequestFailureBadge(localization);
        }
        requestFailureBadge.bind(requestCell);
        return requestFailureBadge;
    }

    private void unbindRequestStatus() {
        if (requestStatusIndicator != null) {
            requestStatusIndicator.unbind();
        }
    }

    private void unbindFailureBadge() {
        if (requestFailureBadge != null) {
            requestFailureBadge.unbind();
        }
    }

    private static void setChildrenIfChanged(HBox box, Node... nodes) {
        if (box.getChildren().size() == nodes.length) {
            boolean unchanged = true;
            for (int i = 0; i < nodes.length; i++) {
                if (box.getChildren().get(i) != nodes[i]) {
                    unchanged = false;
                    break;
                }
            }
            if (unchanged) {
                return;
            }
        }
        box.getChildren().setAll(Arrays.asList(nodes));
    }

    private void updateDisplay(T item, boolean empty) {
        if (item == null || empty) {
            pendingApplicationIconKey = null;
            applicationRow = false;
            unbindApplicationLabels();
            unbindGroupCount();
            unbindRequestStatus();
            unbindFailureBadge();
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

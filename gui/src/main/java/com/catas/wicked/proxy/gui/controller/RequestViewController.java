package com.catas.wicked.proxy.gui.controller;

import com.catas.wicked.common.bean.RequestCell;
import com.catas.wicked.common.bean.message.DeleteMessage;
import com.catas.wicked.common.bean.message.RenderMessage;
import com.catas.wicked.common.bean.message.RequestMessage;
import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.config.ExternalProxyConfig;
import com.catas.wicked.common.constant.ProxyProtocol;
import com.catas.wicked.common.executor.ThreadPoolService;
import com.catas.wicked.common.pipeline.MessageQueue;
import com.catas.wicked.common.pipeline.Topic;
import com.catas.wicked.common.provider.ResourceMessageProvider;
import com.catas.wicked.common.util.AlertUtils;
import com.catas.wicked.proxy.gui.componet.FilterableTreeItem;
import com.catas.wicked.proxy.gui.componet.TreeItemPredicate;
import com.catas.wicked.proxy.gui.componet.ViewCellFactory;
import com.catas.wicked.proxy.message.MessageService;
import com.catas.wicked.proxy.service.RequestViewService;
import com.catas.wicked.proxy.service.record.RequestRecordSnapshot;
import com.catas.wicked.proxy.service.record.RequestRecordStore;
import com.catas.wicked.proxy.service.LocalizationService;
import com.catas.wicked.server.client.MinimalHttpClient;
import com.catas.wicked.common.constant.InternalRequestOrigin;
import com.jfoenix.controls.JFXToggleNode;
import io.micronaut.context.condition.OperatingSystem;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.ReferenceCountUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.beans.binding.Bindings;
import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.net.URL;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.Predicate;

@Slf4j
@Singleton
public class RequestViewController implements Initializable {

    private static final boolean SHOW_REQUEST_STATUS_ICON = true;
    private static final boolean SHOW_GROUP_FAILURE_COUNT = false;
    private static final PseudoClass WINDOWS = PseudoClass.getPseudoClass("windows");

    @FXML
    private HBox requestViewSwitcher;
    @FXML
    public JFXToggleNode applicationViewToggleNode;
    @FXML
    public JFXToggleNode treeViewToggleNode;
    @FXML
    public JFXToggleNode listViewToggleNode;
    @FXML
    private TextField filterInput;
    @FXML
    private Button filterCancelBtn;
    @Getter
    @FXML
    private TreeView<RequestCell> reqTreeView;
    @Getter
    @FXML
    private TreeView<RequestCell> reqApplicationTreeView;
    @Getter
    @FXML
    private ListView<RequestCell> reqListView;
    @FXML
    private ContextMenu contextMenu;
    @FXML
    private MenuItem removeItem;
    @FXML
    private MenuItem resendItem;
    @FXML private Tooltip applicationViewTooltip;
    @FXML private Tooltip treeViewTooltip;
    @FXML private Tooltip listViewTooltip;
    @Inject
    private ViewCellFactory cellFactory;
    @Inject
    private MessageQueue messageQueue;
    @Inject
    private RequestViewService requestViewService;
    @Inject
    private ApplicationConfig appConfig;
    @Inject
    private RequestRecordStore requestStore;
    @Inject
    private LocalizationService localization;

    private ResourceMessageProvider resourceMessageProvider;

    private ToggleGroup toggleGroup;

    /**
     * To avoid circular dependency
     * postConstruct() executed earlier than initialize()
     */
    private MessageService messageService;

    private boolean fxmlInitialized;

    /**
     * save requestList in filteredList
     */
    @Getter
    private ObservableList<RequestCell> reqSourceList;

    private FilteredList<RequestCell> filteredList;
    private PauseTransition filterDebounce;

    private final PseudoClass FocusPseudoClass = PseudoClass.getPseudoClass("custom-focused");

    public FilterableTreeItem<RequestCell> getTreeRoot() {
        return (FilterableTreeItem<RequestCell>) reqTreeView.getRoot();
    }

    public FilterableTreeItem<RequestCell> getApplicationTreeRoot() {
        return (FilterableTreeItem<RequestCell>) reqApplicationTreeView.getRoot();
    }

    public synchronized void setMessageService(MessageService messageService) {
        this.messageService = messageService;
        notifyMessageServiceIfReady();
    }

    public synchronized boolean isRequestViewReady() {
        return fxmlInitialized
                && reqTreeView != null && reqTreeView.getRoot() != null
                && reqApplicationTreeView != null && reqApplicationTreeView.getRoot() != null
                && reqListView != null && reqSourceList != null;
    }

    private synchronized void notifyMessageServiceIfReady() {
        if (messageService != null && isRequestViewReady()) {
            messageService.onRequestViewReady();
        }
    }

    @Inject
    public void setResourceMessageProvider(ResourceMessageProvider resourceMessageProvider) {
        this.resourceMessageProvider = resourceMessageProvider;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        filterDebounce = new PauseTransition(Duration.millis(150));
        requestViewSwitcher.pseudoClassStateChanged(WINDOWS, OperatingSystem.getCurrent().isWindows());
        localization.bind(filterInput.promptTextProperty(), "filter.prompt");
        localization.bind(applicationViewToggleNode.textProperty(), "application-view.label");
        localization.bind(treeViewToggleNode.textProperty(), "tree-view.label");
        localization.bind(listViewToggleNode.textProperty(), "list-view.label");
        localization.bind(applicationViewTooltip.textProperty(), "application-view.tooltip");
        localization.bind(treeViewTooltip.textProperty(), "tree-view.tooltip");
        localization.bind(listViewTooltip.textProperty(), "list-view.tooltip");
        localization.bind(removeItem.textProperty(), "delete.label");
        localization.bind(resendItem.textProperty(), "resend.label");
        reqTreeView.setRoot(new FilterableTreeItem<>());
        reqApplicationTreeView.setRoot(new FilterableTreeItem<>());

        // make reqListView filterable
        reqSourceList = FXCollections.observableArrayList();
        filteredList = new FilteredList<>(reqSourceList);
        reqListView.setItems(filteredList);

        // init filterTextField
        filterInputEventBind();

        reqTreeView.setCellFactory(
                treeView -> cellFactory.createTreeCell(
                        treeView, SHOW_REQUEST_STATUS_ICON, SHOW_GROUP_FAILURE_COUNT));
        reqApplicationTreeView.setCellFactory(
                treeView -> cellFactory.createTreeCell(
                        treeView, SHOW_REQUEST_STATUS_ICON, SHOW_GROUP_FAILURE_COUNT));
        reqListView.setCellFactory(
                listView -> cellFactory.createListCell(listView, SHOW_REQUEST_STATUS_ICON));

        // context menu
        reqTreeView.setContextMenu(contextMenu);
        reqApplicationTreeView.setContextMenu(contextMenu);
        reqListView.setContextMenu(contextMenu);

        // update detail tab
        reqTreeView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                // System.out.println("select null");
                contextMenu.getItems().forEach(menuItem -> menuItem.setDisable(true));
                return;
            }

            RequestCell requestCell = newValue.getValue();
            if (requestCell != null) {
                if (requestCell.isLeaf()) {
                    contextMenu.getItems().forEach(menuItem -> menuItem.setDisable(false));

                    requestViewService.updateRequestTab(requestCell.getRequestId());
                    messageService.selectRequestItem(requestCell.getRequestId(), MessageService.SelectionSource.TREE_VIEW);
                } else {
                    removeItem.setDisable(false);
                    resendItem.setDisable(true);

                    requestViewService.updateRequestTab(RenderMessage.PATH_MSG + requestCell.getFullPath());
                    reqListView.getSelectionModel().clearSelection();
                    reqApplicationTreeView.getSelectionModel().clearSelection();
                }
            }
        });

        reqListView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                contextMenu.getItems().forEach(menuItem -> menuItem.setDisable(false));
                requestViewService.updateRequestTab(newValue.getRequestId());
                messageService.selectRequestItem(newValue.getRequestId(), MessageService.SelectionSource.LIST_VIEW);
            } else {
                // contextMenu.getItems().forEach(menuItem -> menuItem.setDisable(true));
            }
        });

        reqApplicationTreeView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null || newValue.getValue() == null) {
                contextMenu.getItems().forEach(menuItem -> menuItem.setDisable(true));
                return;
            }
            RequestCell requestCell = newValue.getValue();
            if (requestCell.isLeaf()) {
                removeItem.setDisable(false);
                resendItem.setDisable(false);
                requestViewService.updateRequestTab(requestCell.getRequestId());
                messageService.selectRequestItem(requestCell.getRequestId(), MessageService.SelectionSource.APPLICATION_VIEW);
            } else {
                reqTreeView.getSelectionModel().clearSelection();
                reqListView.getSelectionModel().clearSelection();
                removeItem.setDisable(false);
                resendItem.setDisable(true);
                requestViewService.updateApplicationGroupTab(requestCell);
            }
        });

        toggleRequestView();
        bindKeyboardDeleteEvent();
        synchronized (this) {
            fxmlInitialized = true;
        }
        notifyMessageServiceIfReady();
    }

    /**
     * int toggle request view event
     */
    public void toggleRequestView() {
        toggleGroup = new ToggleGroup();
        applicationViewToggleNode.setToggleGroup(toggleGroup);
        treeViewToggleNode.setToggleGroup(toggleGroup);
        listViewToggleNode.setToggleGroup(toggleGroup);
        applicationViewToggleNode.setSelected(true);

        // make at least & only one being selected
        toggleGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                oldValue.setSelected(true);
            }
            if (newValue instanceof JFXToggleNode toggleNode) {
                if (oldValue == newValue) {
                    return;
                }
                // System.out.println("selected " + toggleNode);
                reqTreeView.setVisible(toggleNode == treeViewToggleNode);
                reqApplicationTreeView.setVisible(toggleNode == applicationViewToggleNode);
                reqListView.setVisible(toggleNode == listViewToggleNode);
                if (messageService != null) {
                    MessageService.SelectionSource target = toggleNode == treeViewToggleNode
                            ? MessageService.SelectionSource.TREE_VIEW
                            : toggleNode == listViewToggleNode
                            ? MessageService.SelectionSource.LIST_VIEW
                            : MessageService.SelectionSource.APPLICATION_VIEW;
                    Platform.runLater(() -> messageService.restoreSelection(target));
                }
            }
        });
    }

    /**
     * filter requests
     */
    private void filterInputEventBind() {
        filterCancelBtn.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> !filterInput.getText().isEmpty(), filterInput.textProperty()));

        filterCancelBtn.setOnAction(e -> {
            filterInput.clear();
        });

        filterDebounce.setOnFinished(event -> applyRequestFilter(filterInput.getText()));
        filterInput.textProperty().addListener((observable, oldValue, newValue) -> {
            filterDebounce.stop();
            filterDebounce.playFromStart();
        });
        applyRequestFilter(filterInput.getText());
    }

    private void applyRequestFilter(String filterText) {
        // Filtering changes the TreeView's visible row model. Drop stale selections
        // before the FilteredList notifies JavaFX's selection model.
        clearRequestSelection();
        if (StringUtils.isBlank(filterText)) {
            getTreeRoot().setPredicate(null);
            getApplicationTreeRoot().setPredicate(null);
            filteredList.setPredicate(null);
            return;
        }
        TreeItemPredicate<RequestCell> treePredicate =
                TreeItemPredicate.create(cell -> cell.matchesFilter(filterText));
        Predicate<RequestCell> listPredicate = cell -> cell.matchesFilter(filterText);
        getTreeRoot().setPredicate(treePredicate);
        getApplicationTreeRoot().setPredicate(treePredicate);
        filteredList.setPredicate(listPredicate);
    }

    /**
     * delete treeView/listView item by keyPressed
     */
    private void bindKeyboardDeleteEvent() {
        reqTreeView.setOnKeyPressed(e -> {
            TreeItem<RequestCell> selectedItem = reqTreeView.getSelectionModel().getSelectedItem();
            if (selectedItem != null && (e.getCode() == KeyCode.BACK_SPACE || e.getCode() == KeyCode.DELETE)) {
                removeItem();
            }
        });

        reqApplicationTreeView.setOnKeyPressed(e -> {
            TreeItem<RequestCell> selectedItem = reqApplicationTreeView.getSelectionModel().getSelectedItem();
            if (selectedItem != null && (e.getCode() == KeyCode.BACK_SPACE || e.getCode() == KeyCode.DELETE)) {
                removeItem();
            }
        });

        reqListView.setOnKeyPressed(e -> {
            RequestCell selectedItem = reqListView.getSelectionModel().getSelectedItem();
            if (selectedItem != null && (e.getCode() == KeyCode.BACK_SPACE || e.getCode() == KeyCode.DELETE)) {
                removeItem();
            }
        });
    }

    /**
     * remove item from listView or treeView
     */
    public void removeItem() {
        RequestCell requestCell = null;
        DeleteMessage deleteMessage = new DeleteMessage();

        if (applicationViewToggleNode.isSelected()) {
            TreeItem<RequestCell> selectedItem = reqApplicationTreeView.getSelectionModel().getSelectedItem();
            requestCell = selectedItem == null ? null : selectedItem.getValue();
            if (requestCell == null) {
                log.error("Unable to delete request, request cell is null.");
                return;
            }
            messageService.deleteApplicationItem(requestCell);
            clearRequestSelection();
            requestViewService.updateRequestTab(null);
            return;
        } else if (treeViewToggleNode.isSelected()) {
            TreeItem<RequestCell> selectedItem = reqTreeView.getSelectionModel().getSelectedItem();
            requestCell = selectedItem == null ? null : selectedItem.getValue();
            deleteMessage.setSource(DeleteMessage.Source.TREE_VIEW);
        } else {
            requestCell = reqListView.getSelectionModel().getSelectedItem();
            deleteMessage.setSource(DeleteMessage.Source.LIST_VIEW);
        }

        if (requestCell == null) {
            log.error("Unable to delete request, request cell is null.");
            return;
        }
        // clear selection
        clearRequestSelection();

        // send msg
        deleteMessage.setRequestCell(requestCell);
        messageQueue.pushMsg(Topic.RECORD, deleteMessage);
        requestViewService.updateRequestTab(null);
    }

    private void clearRequestSelection() {
        reqListView.getSelectionModel().clearSelection();
        reqTreeView.getSelectionModel().clearSelection();
        reqApplicationTreeView.getSelectionModel().clearSelection();
    }

    public void clearSelectionsBeforeTreeMutation() {
        if (reqListView != null) {
            reqListView.getSelectionModel().clearSelection();
        }
        if (reqTreeView != null) {
            reqTreeView.getSelectionModel().clearSelection();
        }
        if (reqApplicationTreeView != null) {
            reqApplicationTreeView.getSelectionModel().clearSelection();
        }
    }

    public void clearUrlTreeSelectionBeforeRemoving(TreeItem<RequestCell> item) {
        clearTreeSelectionBeforeRemoving(reqTreeView, item);
    }

    public void clearApplicationTreeSelectionBeforeRemoving(TreeItem<RequestCell> item) {
        clearTreeSelectionBeforeRemoving(reqApplicationTreeView, item);
    }

    private static void clearTreeSelectionBeforeRemoving(
            TreeView<RequestCell> treeView, TreeItem<RequestCell> item) {
        if (treeView == null || item == null) {
            return;
        }
        TreeItem<RequestCell> selected = treeView.getSelectionModel().getSelectedItem();
        if (isDescendantOrSelf(item, selected)) {
            treeView.getSelectionModel().clearSelection();
        }
    }

    static boolean isDescendantOrSelf(TreeItem<?> ancestor, TreeItem<?> item) {
        TreeItem<?> current = item;
        while (current != null) {
            if (current == ancestor) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    /**
     * clear all leaf-nodes of treeView
     */
    public void clearLeafNode() {
        DeleteMessage deleteMessage = new DeleteMessage();
        deleteMessage.setCleanLeaves(true);
        messageQueue.pushMsg(Topic.RECORD, deleteMessage);
    }

    /**
     * focus on selected item
     */
    public void focus() {
        // scroll treeView
        int selectedTreeItem = reqTreeView.getSelectionModel().getSelectedIndex();
        if (selectedTreeItem >= 0 && selectedTreeItem < reqTreeView.getExpandedItemCount()) {
            reqTreeView.scrollTo(selectedTreeItem);
        }

        int selectedApplicationItem = reqApplicationTreeView.getSelectionModel().getSelectedIndex();
        if (selectedApplicationItem >= 0 && selectedApplicationItem < reqApplicationTreeView.getExpandedItemCount()) {
            reqApplicationTreeView.scrollTo(selectedApplicationItem);
        }

        // scroll listView
        int selectedListItem = reqListView.getSelectionModel().getSelectedIndex();
        if (selectedListItem >= 0 && selectedListItem < reqListView.getItems().size()) {
            reqListView.scrollTo(selectedListItem);
        }
    }

    public void updateFocusPseudoClass(Boolean state) {
        reqTreeView.pseudoClassStateChanged(FocusPseudoClass, state);
        reqApplicationTreeView.pseudoClassStateChanged(FocusPseudoClass, state);
        reqListView.pseudoClassStateChanged(FocusPseudoClass, state);
    }

    public void resendRequest() {
        String requestId = appConfig.getObservableConfig().getCurrentRequestId();
        if (StringUtils.isBlank(requestId)) {
            return;
        }
        RequestRecordSnapshot snapshot = requestStore.snapshot(requestId);
        RequestMessage requestMessage = snapshot == null ? null : snapshot.message();
        if (requestMessage == null || requestMessage.isEncrypted() || requestMessage.isOversize()) {
            log.warn("Not integrated http request, unable to resend");
            String msg;
            if (requestMessage == null || requestMessage.isOversize()) {
                msg = resourceMessageProvider.getMessage("resend.incomplete.label");
            } else {
                msg = resourceMessageProvider.getMessage("resend.encrypted.label");
            }
            AlertUtils.alertWarning(resourceMessageProvider.getMessage("alert.type.warning"), msg);
            return;
        }
        if (requiresBody(requestMessage.getMethod()) && snapshot.requestPayloadEvicted()) {
            AlertUtils.alertWarning(resourceMessageProvider.getMessage("alert.type.warning"),
                    resourceMessageProvider.getMessage("payload-released.label"));
            return;
        }

        log.info("Resending request: {}, method: {}", requestMessage.getRequestUrl(), requestMessage.getMethod());
        ThreadPoolService.getInstance().run(() -> {
            String url = requestMessage.getRequestUrl();
            String method = requestMessage.getMethod();
            String protocol = requestMessage.getProtocol();
            Map<String, String> headers = requestMessage.getHeaders();
            byte[] content = requestMessage.getBody();

            ExternalProxyConfig proxyConfig = new ExternalProxyConfig();
            proxyConfig.setProtocol(ProxyProtocol.HTTP);
            proxyConfig.setProxyAddress(appConfig.getHost(), appConfig.getSettings().getPort());

            try (MinimalHttpClient client = MinimalHttpClient.builder()
                    .uri(url)
                    .method(HttpMethod.valueOf(method))
                    .httpVersion(protocol)
                    .headers(headers)
                    .content(content)
                    .proxyConfig(proxyConfig)
                    .internalRequest(InternalRequestOrigin.RESEND, appConfig.getInternalRequestToken())
                    .build()) {
                client.execute();
                HttpResponse response = client.response();
                try {
                    log.info("Get response in resending: {}", response);
                } finally {
                    ReferenceCountUtil.release(response);
                }
            } catch (Exception e) {
                log.error("Error in resending request: {}", requestMessage.getRequestUrl());
            }
        });
    }

    private static boolean requiresBody(String method) {
        return "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method);
    }
}

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
import com.catas.wicked.proxy.gui.componet.FilterableTreeItem;
import com.catas.wicked.proxy.gui.componet.TreeItemPredicate;
import com.catas.wicked.proxy.gui.componet.ViewCellFactory;
import com.catas.wicked.proxy.message.MessageService;
import com.catas.wicked.proxy.service.RequestViewService;
import com.catas.wicked.server.client.MinimalHttpClient;
import com.jfoenix.controls.JFXToggleNode;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.beans.binding.Bindings;
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
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.ehcache.Cache;

import java.net.URL;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;
import java.util.function.Predicate;

@Slf4j
@Singleton
public class RequestViewController implements Initializable {

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
    private ListView<RequestCell> reqListView;
    @FXML
    private ContextMenu contextMenu;
    @FXML
    private MenuItem removeItem;
    @FXML
    private MenuItem resendItem;
    @Inject
    private ViewCellFactory cellFactory;
    @Inject
    private MessageQueue messageQueue;
    @Inject
    private RequestViewService requestViewService;
    @Inject
    private ApplicationConfig appConfig;
    @Inject
    private Cache<String, RequestMessage> requestCache;

    private ToggleGroup toggleGroup;

    /**
     * To avoid circular dependency
     * postConstruct() executed earlier than initialize()
     */
    @Setter
    private MessageService messageService;

    /**
     * save requestList in filteredList
     */
    @Getter
    private ObservableList<RequestCell> reqSourceList;

    private FilteredList<RequestCell> filteredList;

    private final PseudoClass FocusPseudoClass = PseudoClass.getPseudoClass("custom-focused");

    public FilterableTreeItem getTreeRoot() {
        return (FilterableTreeItem) reqTreeView.getRoot();
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        reqTreeView.setRoot(new FilterableTreeItem<>());

        // make reqListView filterable
        reqSourceList = FXCollections.observableArrayList();
        filteredList = new FilteredList<>(reqSourceList);
        reqListView.setItems(filteredList);

        // init filterTextField
        filterInputEventBind();

        reqTreeView.setCellFactory(treeView -> cellFactory.createTreeCell(treeView));
        reqListView.setCellFactory(listView -> cellFactory.createListCell(listView));

        // context menu
        reqTreeView.setContextMenu(contextMenu);
        reqListView.setContextMenu(contextMenu);

        // update detail tab
        reqTreeView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                System.out.println("selected null1");
                contextMenu.getItems().forEach(menuItem -> menuItem.setDisable(true));
                return;
            }

            RequestCell requestCell = newValue.getValue();
            if (requestCell != null) {
                if (requestCell.isLeaf()) {
                    contextMenu.getItems().forEach(menuItem -> menuItem.setDisable(false));

                    requestViewService.updateRequestTab(requestCell.getRequestId());
                    messageService.selectRequestItem(requestCell.getRequestId(), true);
                } else {
                    removeItem.setDisable(false);
                    resendItem.setDisable(true);

                    requestViewService.updateRequestTab(RenderMessage.PATH_MSG + requestCell.getFullPath());
                    reqListView.getSelectionModel().clearSelection();
                }
            }
        });

        reqListView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                contextMenu.getItems().forEach(menuItem -> menuItem.setDisable(false));
                requestViewService.updateRequestTab(newValue.getRequestId());
                messageService.selectRequestItem(newValue.getRequestId(), false);
            } else {
                contextMenu.getItems().forEach(menuItem -> menuItem.setDisable(true));
            }
        });

        toggleRequestView();
        bindKeyboardDeleteEvent();
    }

    /**
     * int toggle request view event
     */
    public void toggleRequestView() {
        toggleGroup = new ToggleGroup();
        treeViewToggleNode.setToggleGroup(toggleGroup);
        listViewToggleNode.setToggleGroup(toggleGroup);
        treeViewToggleNode.setSelected(true);

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
                reqListView.setVisible(toggleNode == listViewToggleNode);
            }
        });
    }

    /**
     * filter requests
     */
    @SuppressWarnings("unchecked")
    private void filterInputEventBind() {
        filterCancelBtn.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> !filterInput.getText().isEmpty(), filterInput.textProperty()));

        filterCancelBtn.setOnAction(e -> {
            filterInput.clear();
        });

        // bind filter treeView from: JFX
        getTreeRoot().predicateProperty().bind(Bindings.createObjectBinding(() -> {
            // System.out.println(filterInput.getText());
            if (filterInput.getText() == null || filterInput.getText().isEmpty())
                return null;
            return TreeItemPredicate.create(actor -> actor.toString().contains(filterInput.getText()));
        }, filterInput.textProperty()));

        // bind filter listView
        filteredList.predicateProperty().bind(Bindings.createObjectBinding(new Callable<Predicate<? super RequestCell>>() {
            @Override
            public Predicate<? super RequestCell> call() throws Exception {
                if (filterInput.getText() == null || filterInput.getText().isEmpty())
                    return null;
                return new Predicate<RequestCell>() {
                    @Override
                    public boolean test(RequestCell requestCell) {
                        // System.out.println("filter: " + filterInput.getText());
                        return requestCell.getFullPath().contains(filterInput.getText());
                    }
                };
            }
        }, filterInput.textProperty()));
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
        TreeItem<RequestCell> selectedItem = null;
        RequestCell requestCell = null;
        DeleteMessage deleteMessage = new DeleteMessage();

        if (treeViewToggleNode.selectedProperty().get()) {
            // from tree view
            selectedItem = reqTreeView.getSelectionModel().getSelectedItem();
            FilterableTreeItem<RequestCell> parent = (FilterableTreeItem<RequestCell>) selectedItem.getParent();
            parent.getInternalChildren().remove(selectedItem);
            requestCell = selectedItem.getValue();
            deleteMessage.setSource(DeleteMessage.Source.TREE_VIEW);
        } else {
            // from list view
            requestCell = reqListView.getSelectionModel().getSelectedItem();
            // reqListView.getItems().remove(requestCell);
            reqSourceList.remove(requestCell);
            deleteMessage.setSource(DeleteMessage.Source.LIST_VIEW);
        }

        if (requestCell == null) {
            log.error("Unable to delete request, request cell is null.");
        }
        // clear selection
        reqListView.getSelectionModel().clearSelection();
        reqTreeView.getSelectionModel().clearSelection();

        // send msg
        deleteMessage.setRequestCell(requestCell);
        messageQueue.pushMsg(Topic.RECORD, deleteMessage);
        requestViewService.updateRequestTab(null);
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
        reqTreeView.scrollTo(selectedTreeItem);

        // scroll listView
        int selectedListItem = reqListView.getSelectionModel().getSelectedIndex();
        reqListView.scrollTo(selectedListItem);

        // focus style
        // reqTreeView.pseudoClassStateChanged(FocusPseudoClass, true);
        // reqListView.pseudoClassStateChanged(FocusPseudoClass, true);

    }

    public void updateFocusPseudoClass(Boolean state) {
        reqTreeView.pseudoClassStateChanged(FocusPseudoClass, state);
        reqListView.pseudoClassStateChanged(FocusPseudoClass, state);
    }

    public void resendRequest() {
        String requestId = appConfig.getObservableConfig().getCurrentRequestId();
        if (StringUtils.isBlank(requestId)) {
            return;
        }
        RequestMessage requestMessage = requestCache.get(requestId);
        if (requestMessage == null || requestMessage.isEncrypted() || requestMessage.isOversize()) {
            log.warn("Not integrated http request, unable to resend");
            return;
        }

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
                    .build()) {
                client.execute();
                HttpResponse response = client.response();
                log.info("Get response in resending: {}", response);
            } catch (Exception e) {
                log.error("Error in resending request: {}", requestMessage.getRequestUrl());
            }
        });
    }
}

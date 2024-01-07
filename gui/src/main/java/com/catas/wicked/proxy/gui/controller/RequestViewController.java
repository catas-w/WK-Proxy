package com.catas.wicked.proxy.gui.controller;

import com.catas.wicked.common.bean.RequestCell;
import com.catas.wicked.common.bean.message.DeleteMessage;
import com.catas.wicked.common.pipeline.MessageQueue;
import com.catas.wicked.common.pipeline.Topic;
import com.catas.wicked.proxy.gui.componet.FilterableTreeItem;
import com.catas.wicked.proxy.gui.componet.TreeItemPredicate;
import com.catas.wicked.proxy.gui.componet.ViewCellFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.paint.Color;
import lombok.extern.slf4j.Slf4j;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;
import java.util.function.Predicate;

@Slf4j
@Singleton
public class RequestViewController implements Initializable {

    @FXML
    private MenuButton listViewMenuBtn;
    @FXML
    private MenuItem treeViewMenuItem;
    @FXML
    private MenuItem listViewMenuItem;
    @FXML
    private TextField filterInput;
    @FXML
    private Button filterCancelBtn;
    @FXML
    private TreeView<RequestCell> reqTreeView;
    @FXML
    private ListView<RequestCell> reqListView;
    @FXML
    private TreeItem root;
    @FXML
    private ContextMenu contextMenu;
    @Inject
    private ViewCellFactory cellFactory;
    @Inject
    private MessageQueue messageQueue;

    /**
     * save requestList in filteredList
     */
    private ObservableList<RequestCell> reqSourceList;
    private FilteredList<RequestCell> filteredList;
    /**
     * current request-view type, 0=tree 1=list
     */
    private int curViewType = 0;

    public FilterableTreeItem getTreeRoot() {
        return (FilterableTreeItem) reqTreeView.getRoot();
    }

    public ListView<RequestCell> getReqListView() {
        return reqListView;
    }

    public ObservableList<RequestCell> getReqSourceList() {
        return reqSourceList;
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

        reqTreeView.setContextMenu(contextMenu);
        reqListView.setContextMenu(contextMenu);
    }

    public void bindViewChange(ActionEvent event) {
        MenuItem source = (MenuItem) event.getSource();
        FontIcon icon = (FontIcon) source.getGraphic();
        FontIcon fontIcon = new FontIcon(icon.getIconCode());
        fontIcon.setIconSize(18);
        fontIcon.setIconColor(Color.web("#616161"));
        listViewMenuBtn.setGraphic(fontIcon);

        if (source.getId().contains("list")) {
            reqTreeView.setVisible(false);
            reqListView.setVisible(true);
            curViewType = 1;
        } else {
            reqTreeView.setVisible(true);
            reqListView.setVisible(false);
            curViewType = 0;
        }
    }

    private void filterInputEventBind() {
        filterInput.setOnKeyTyped(e -> {
            CharSequence characters = filterInput.getText();
            filterCancelBtn.setVisible(characters.length() > 0);
        });

        filterCancelBtn.setOnMouseClicked(e -> {
            filterInput.clear();
            filterCancelBtn.setVisible(false);
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
                        return requestCell.getFullPath().contains(filterInput.getText());
                    }
                };
            }
        }, filterInput.textProperty()));
    }

    /**
     * remove item from listView or treeView
     */
    public void removeItem(ActionEvent event) {
        TreeItem<RequestCell> selectedItem = null;
        RequestCell requestCell = null;
        DeleteMessage deleteMessage = new DeleteMessage();

        if (curViewType == 0) {
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
        deleteMessage.setRequestCell(requestCell);
        messageQueue.pushMsg(Topic.RECORD, deleteMessage);
    }
}

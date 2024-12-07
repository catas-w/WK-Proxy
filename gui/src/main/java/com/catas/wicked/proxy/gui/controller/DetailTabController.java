package com.catas.wicked.proxy.gui.controller;

import com.catas.wicked.common.bean.HeaderEntry;
import com.catas.wicked.common.bean.RequestOverviewInfo;
import com.catas.wicked.common.bean.PairEntry;
import com.catas.wicked.common.constant.CodeStyle;
import com.catas.wicked.common.util.TableUtils;
import com.catas.wicked.proxy.gui.componet.OverviewTreeTableCell;
import com.catas.wicked.proxy.gui.componet.SelectableTableCell;
import com.catas.wicked.proxy.gui.componet.MessageLabel;
import com.catas.wicked.proxy.gui.componet.SelectableTreeTableCell;
import com.catas.wicked.proxy.gui.componet.SideBar;
import com.catas.wicked.proxy.gui.componet.ZoomImageView;
import com.catas.wicked.proxy.gui.componet.builder.TextAreaEditorNodeBuilder;
import com.catas.wicked.proxy.gui.componet.highlight.CodeStyleLabel;
import com.catas.wicked.proxy.gui.componet.richtext.DisplayCodeArea;
import com.catas.wicked.proxy.render.ContextMenuFactory;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXTabPane;
import com.jfoenix.controls.JFXTreeTableView;
import io.micronaut.core.util.CollectionUtils;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Labeled;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TitledPane;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.skin.TableHeaderRow;
import javafx.scene.layout.GridPane;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

@Slf4j
@Getter
@Singleton
public class DetailTabController implements Initializable {

    @FXML
    public JFXTabPane mainTabPane;
    @FXML
    public DisplayCodeArea testCodeArea;
    @FXML
    public TableView<HeaderEntry> reqContentTable;
    @FXML
    public ZoomImageView reqImageView;
    @FXML
    public MessageLabel respHeaderMsgLabel;
    @FXML
    public MessageLabel reqHeaderMsgLabel;
    @FXML
    public MessageLabel reqContentMsgLabel;
    @FXML
    public MessageLabel respContentMsgLabel;
    @FXML
    public MessageLabel timingMsgLabel;
    @FXML
    public JFXComboBox<Labeled> reqComboBox;
    @FXML
    public SideBar respSideBar;
    @FXML
    public SideBar reqContentSideBar;
    @FXML
    public SideBar reqQuerySideBar;
    @FXML
    public GridPane timingGridPane;
    @FXML
    public Tab overviewTab;
    @FXML
    public Tab requestTab;
    @FXML
    public Tab respTab;
    @FXML
    public Tab timingTab;
    @FXML
    private JFXComboBox<Labeled> respComboBox;
    @FXML
    private ZoomImageView respImageView;
    @FXML
    private MessageLabel overViewMsgLabel;
    @FXML
    private SplitPane respSplitPane;
    @FXML
    private SplitPane reqSplitPane;
    @FXML
    private DisplayCodeArea overviewArea;
    @FXML
    private TreeTableView<PairEntry> overviewTable;
    @FXML
    private JFXTreeTableView<PairEntry> testTable;
    @FXML
    private TitledPane reqPayloadTitlePane;
    @FXML
    private JFXTabPane reqPayloadTabPane;
    @FXML
    private TitledPane respHeaderPane;
    @FXML
    private TitledPane reqParamPane;
    @FXML
    private TitledPane respDataPane;
    @FXML
    private TitledPane reqHeaderPane;
    @FXML
    private TableView<HeaderEntry> reqHeaderTable;
    @FXML
    private DisplayCodeArea reqHeaderArea;
    @FXML
    private DisplayCodeArea reqParamArea;
    @FXML
    private DisplayCodeArea reqPayloadCodeArea;
    @FXML
    private DisplayCodeArea respHeaderArea;
    @FXML
    private DisplayCodeArea respContentArea;
    @FXML
    private TableView<HeaderEntry> respHeaderTable;

    @Inject
    private RequestOverviewInfo requestOverviewInfo;

    private final Map<SplitPane, double[]> dividerPositionMap = new HashMap<>();

    private final List<Tab> requestOnlyTabs = new ArrayList<>();

    private boolean dividerUpdating;

    private boolean midTitleCollapse;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        dividerPositionMap.put(reqSplitPane, reqSplitPane.getDividerPositions().clone());
        dividerPositionMap.put(respSplitPane, respSplitPane.getDividerPositions().clone());

        requestOnlyTabs.addAll(List.of(requestTab, respTab, timingTab));

        // init titlePane collapse
        addTitleListener(reqHeaderPane, reqSplitPane);
        addTitleListener(reqPayloadTitlePane, reqSplitPane);
        addTitleListener(respHeaderPane, respSplitPane);
        addTitleListener(respDataPane, respSplitPane);

        // init sideBar
        reqQuerySideBar.setStrategy(SideBar.Strategy.URLENCODED_FORM_DATA);
        reqQuerySideBar.setTargetCodeArea(reqParamArea);
        reqContentSideBar.setTargetCodeArea(reqPayloadCodeArea);
        respSideBar.setTargetCodeArea(respContentArea);
        initComboBox(respComboBox, respSideBar);
        initComboBox(reqComboBox, reqContentSideBar);

        // init tableView
        initTableView(reqHeaderTable);
        initTableView(respHeaderTable);
        initOverviewTable(overviewTable);
    }

    public void setOverviewTableRoot(TreeItem<PairEntry> root) {
        if (overviewTable.getRoot() == root) {
            return;
        }
        Platform.runLater(() -> {
            overviewTable.setRoot(root);
        });
    }

    public void refreshOverviewTable() {
        overviewTable.refresh();
    }

    @SuppressWarnings("unchecked")
    private void initOverviewTable(TreeTableView<PairEntry> tableView) {
        TreeTableColumn<PairEntry, String> nameColumn = new TreeTableColumn<>("Name");
        nameColumn.setPrefWidth(130);
        nameColumn.setMaxWidth(200);
        nameColumn.setMinWidth(100);
        nameColumn.setSortable(false);
        final String titleStyle = "tree-table-key";
        nameColumn.getStyleClass().add(titleStyle);
        nameColumn.getStyleClass().add("headers-key");
        nameColumn.setCellValueFactory((TreeTableColumn.CellDataFeatures<PairEntry, String> param) ->
                new ReadOnlyStringWrapper(param.getValue().getValue().getKey()));
        nameColumn.setCellFactory((TreeTableColumn<PairEntry, String> param) ->
                new OverviewTreeTableCell());

        TreeTableColumn<PairEntry, String> valueColumn = new TreeTableColumn<>("Value");
        valueColumn.setSortable(false );
        nameColumn.getStyleClass().add("headers-value");
        valueColumn.setCellValueFactory((TreeTableColumn.CellDataFeatures<PairEntry, String> param) ->
                new ReadOnlyStringWrapper(param.getValue().getValue().getVal()));
        valueColumn.setCellFactory((TreeTableColumn<PairEntry, String> param) ->
                new SelectableTreeTableCell(new TextAreaEditorNodeBuilder(valueColumn), valueColumn));

        Platform.runLater(() -> {
            tableView.getColumns().addAll(nameColumn, valueColumn);
            tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            tableView.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
            tableView.setShowRoot(false);
            tableView.setEditable(true);
        });

        tableView.widthProperty().addListener((source, oldWidth, newWidth) -> {
            TableHeaderRow header = (TableHeaderRow) tableView.lookup("TableHeaderRow");
            header.reorderingProperty().addListener((observable, oldValue, newValue) -> header.setReordering(false));
        });
        tableView.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) {
                tableView.getSelectionModel().clearSelection();
            }
        });
    }

    /**
     * initialize tableView for headers
     */
    @SuppressWarnings("unchecked")
    private void initTableView(TableView<HeaderEntry> tableView) {
        tableView.setEditable(true);

        // set key column
        TableColumn<HeaderEntry, String> keyColumn = new TableColumn<>();
        keyColumn.setText("Name");
        keyColumn.getStyleClass().add("table-key");
        keyColumn.setSortable(false);
        keyColumn.setCellValueFactory(new PropertyValueFactory<>("key"));
        keyColumn.setPrefWidth(120);
        keyColumn.setMaxWidth(200);
        // TableUtils.setTableCellFactory(keyColumn, true);
        keyColumn.setCellFactory((TableColumn<HeaderEntry, String> param) -> {
            SelectableTableCell<HeaderEntry> cell =
                    new SelectableTableCell<>(new TextAreaEditorNodeBuilder(keyColumn), keyColumn);
            cell.addTextStyle("headers-key");
            return cell;
        });

        // set value column
        TableColumn<HeaderEntry, String> valColumn = new TableColumn<>();
        valColumn.setText("Value");
        valColumn.getStyleClass().add("table-value");
        valColumn.setSortable(false);
        valColumn.setEditable(true);
        valColumn.setCellValueFactory(new PropertyValueFactory<>("val"));
        valColumn.setCellFactory((TableColumn<HeaderEntry, String> param) ->
                new SelectableTableCell<>(new TextAreaEditorNodeBuilder(valColumn), valColumn));

        tableView.getColumns().setAll(keyColumn, valColumn);

        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        // tableView.setFixedCellSize(20);
        tableView.prefHeightProperty()
                .bind(Bindings.size(tableView.getItems()).multiply(tableView.getFixedCellSize()));

        // selection
        // tableView.getSelectionModel().setCellSelectionEnabled(true);
        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // tableView.setContextMenu(ContextMenuFactory.getTableViewContextMenu(tableView));

        // clearSelection when lose focus
        tableView.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) {
                tableView.getSelectionModel().clearSelection();
            }
        });
        tableView.widthProperty().addListener((source, oldWidth, newWidth) -> {
            TableHeaderRow header = (TableHeaderRow) tableView.lookup("TableHeaderRow");
            header.reorderingProperty().addListener((observable, oldValue, newValue) -> header.setReordering(false));
        });

        TableUtils.installCopyPasteHandler(tableView);
    }

    private void initComboBox(ComboBox<Labeled> comboBox, SideBar sideBar) {
        if (comboBox.getItems().isEmpty()) {
            // comboBox.setButtonCell(new Gra);
            comboBox.getItems().add(new CodeStyleLabel("Plain", CodeStyle.PLAIN));
            comboBox.getItems().add(new CodeStyleLabel("Json", CodeStyle.JSON));
            comboBox.getItems().add(new CodeStyleLabel("Html", CodeStyle.HTML));
            comboBox.getItems().add(new CodeStyleLabel("Xml", CodeStyle.XML));
            // comboBox.getItems().add(new CodeStyleLabel("Javascript", CodeStyle.JAVASCRIPT));

            comboBox.valueProperty().addListener(new ChangeListener<Labeled>() {
                @Override
                public void changed(ObservableValue<? extends Labeled> observable, Labeled oldValue, Labeled newValue) {
                    CodeStyle codeStyle = CodeStyle.valueOf(newValue.getText().toUpperCase());
                    // codeArea.setCodeStyle(codeStyle);
                    sideBar.setCodeStyle(codeStyle, false, true);
                }
            });
        }
        comboBox.getSelectionModel().selectFirst();
    }

    /**
     * synchronized dividers
     * @deprecated
     */
    private void bindDividerPosition(SplitPane splitPane) {
        if (splitPane.getDividers().size() < 2) {
            return;
        }
        ObservableList<SplitPane.Divider> dividers = splitPane.getDividers();
        dividers.get(0).positionProperty().addListener(((observable, oldValue, newValue) -> {
            if (dividerUpdating || splitPane.getDividers().size() < 2 || reqParamPane.isExpanded()) {
                return;
            }
            // System.out.println("Divider-0: " + newValue);
            if (newValue.doubleValue() > 0.95) {
                dividers.get(0).setPosition(0.95);
                dividers.get(1).setPosition(1.0);
                return;
            }
            dividerUpdating = true;
            double delta = newValue.doubleValue() - oldValue.doubleValue();
            dividers.get(1).setPosition(dividers.get(1).positionProperty().doubleValue() + delta);
            dividerUpdating = false;
        }));

        dividers.get(1).positionProperty().addListener(((observable, oldValue, newValue) -> {
            if (dividerUpdating || splitPane.getDividers().size() < 2 || reqParamPane.isExpanded()) {
                return;
            }
            // System.out.println("Divider-1: " + newValue);
            if (!midTitleCollapse) {
                return;
            }
            if (newValue.doubleValue() < 0.05) {
                dividers.get(0).setPosition(0.0);
                dividers.get(1).setPosition(0.05);
                return;
            }
            dividerUpdating = true;
            double delta = newValue.doubleValue() - oldValue.doubleValue();
            dividers.get(0).setPosition(dividers.get(0).positionProperty().doubleValue() + delta);
            dividerUpdating = false;
        }));
    }

    private void addTitleListener(TitledPane pane, SplitPane splitPane) {
        pane.expandedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                // open
                pane.maxHeightProperty().set(Double.POSITIVE_INFINITY);
                if (splitPane.getItems().size() == 2) {
                    splitPane.setDividerPositions(dividerPositionMap.get(splitPane));
                }
            } else {
                // close
                if (getExpandedNum(splitPane) > 1) {
                    dividerPositionMap.put(splitPane, splitPane.getDividerPositions().clone());
                }
                pane.maxHeightProperty().set(Double.NEGATIVE_INFINITY);
            }
        });
    }
    private int getExpandedNum(SplitPane splitPane) {
        int res = 0;
        for (Node item : splitPane.getItems()) {
            if (item instanceof TitledPane titledPane) {
                res += titledPane.isExpanded() ? 1 : 0;
            }
        }
        return res;
    }

    public String getActiveRequestTab() {
        Tab selectedTab = this.mainTabPane.getSelectionModel().getSelectedItem();
        return selectedTab.getText();
    }

    public void hideRequestOnlyTabs() {
        if (CollectionUtils.isEmpty(requestOnlyTabs)) {
            return;
        }
        Platform.runLater(() -> {
            requestOnlyTabs.forEach(tab -> tab.setDisable(true));
        });
        mainTabPane.getSelectionModel().select(overviewTab);
    }

    public void showRequestOnlyTabs() {
        if (CollectionUtils.isEmpty(requestOnlyTabs)) {
            return;
        }
        Platform.runLater(() -> {
            // mainTabPane.getTabs().removeAll(requestOnlyTabs);
            requestOnlyTabs.forEach(tab -> tab.setDisable(false));
        });
    }
}

package com.catas.wicked.proxy.gui.componet;

import com.catas.wicked.common.bean.PairEntry;
import com.jfoenix.controls.cells.editors.base.EditorNodeBuilder;
import com.jfoenix.controls.cells.editors.base.GenericEditableTreeTableCell;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.text.Text;

import java.util.Arrays;
import java.util.List;

/**
 * selectable table cell for treeTableView
 */
public class SelectableTreeTableCell extends GenericEditableTreeTableCell<PairEntry, PairEntry> {

    private final Text text;

    public SelectableTreeTableCell(EditorNodeBuilder builder, TreeTableColumn<PairEntry, PairEntry> valColumn) {
        super(builder);
        this.text = new Text();
        setGraphic(text);
        text.wrappingWidthProperty().bind(valColumn.widthProperty());
        // text.textProperty().bind(this.itemProperty());

        text.getStyleClass().add("overview-table-value-text");
        // getStyleClass().add("overview-table-value");
    }
    @Override
    public void cancelEdit() {
        super.cancelEdit();
        setGraphic(text);
        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    }

    @Override
    public void updateItem(PairEntry item, boolean empty) {
        super.updateItem(item, empty);
        if (!empty && !isEditing()) {
            text.setText(item.getVal());

            // toggle style
            List<String> styleList = Arrays.stream(PairEntry.ColumnStyle.values()).map(PairEntry.ColumnStyle::getStyleClass).toList();
            text.getStyleClass().removeIf(styleList::contains);
            if (item.getColumnStyleClass() != null) {
                text.getStyleClass().add(item.getColumnStyleClass());
            }

            setGraphic(text);
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        }
    }

    public void addTextStyle(String style) {
        this.text.getStyleClass().add(style);
    }
}

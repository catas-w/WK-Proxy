package com.catas.wicked.proxy.gui.componet;

import com.catas.wicked.common.bean.PairEntry;
import com.jfoenix.controls.cells.editors.base.EditorNodeBuilder;
import com.jfoenix.controls.cells.editors.base.GenericEditableTreeTableCell;
import javafx.geometry.Insets;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Arrays;
import java.util.List;

/**
 * selectable table cell for treeTableView
 */
public class SelectableTreeTableCell extends GenericEditableTreeTableCell<PairEntry, PairEntry> {

    private final Text text;

    private PairEntry.ColumnStyle columnStyle;

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
        // setGraphic(text);
        // setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        refreshGraphic();
    }

    @Override
    public void updateItem(PairEntry item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setText(null);
            setGraphic(null);
        } else {
            setText(item.getVal());
            // setGraphic(null);
            text.setText(item.getVal());

            // toggle style
            List<String> styleList = Arrays.stream(PairEntry.ColumnStyle.values()).map(PairEntry.ColumnStyle::getStyleClass).toList();
            text.getStyleClass().removeIf(styleList::contains);

            // set icon
            this.columnStyle = item.getColumnStyle();
            refreshGraphic();
        }
    }

    private void refreshGraphic() {
        if (columnStyle != null) {
            text.getStyleClass().add(columnStyle.getStyleClass());

            HBox hBox = new HBox();
            hBox.getStyleClass().add("overview-table-value-box");

            FontIcon icon = new FontIcon(columnStyle.getIconStr());
            icon.getStyleClass().add(columnStyle.getStyleClass());

            hBox.getChildren().addAll(icon, text);
            HBox.setMargin(icon, new Insets(2, 4, 2, 0));

            setGraphic(hBox);
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        } else {
            setGraphic(text);
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        }
    }
}

package com.catas.wicked.proxy.gui.componet;

import com.catas.wicked.common.bean.PairEntry;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.util.Duration;

public class OverviewTreeTableCell extends TreeTableCell<PairEntry, String> {

    private final Button button = new Button();

    private final HBox hBox = new HBox();

    private final Label label = new Label();

    private static final double RADIUS = 6.5;

    private static final String DEFAULT_STYLE = "overview-key-btn";

    public OverviewTreeTableCell() {
        Circle circle = new Circle(RADIUS);
        button.setShape(circle);

        // Set the button size to match the circle's size
        button.setMinSize(RADIUS * 2, RADIUS * 2);
        button.setMaxSize(RADIUS * 2, RADIUS * 2);

        // Remove the default background insets and padding to make it a perfect circle
        button.setStyle("-fx-background-radius: %f; -fx-padding: 0;".formatted(RADIUS));
        button.getStyleClass().add(DEFAULT_STYLE);

        button.setText("!");
        hBox.getChildren().addAll(label, button);
    }

    @Override
    protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (item == null || empty || getTableRow() == null) {
            setText(null);
            setGraphic(null);
        } else {
            TreeItem<PairEntry> currentTreeItem = getTableRow().getTreeItem();
            if (currentTreeItem == null || currentTreeItem.getValue().tooltipProperty() == null) {
                setText(item);
                setGraphic(null);
                return;
            }

            // set tooltip button
            String tooltipText = currentTreeItem.getValue().getTooltip();
            Tooltip tooltip = new Tooltip(tooltipText);
            tooltip.setFont(Font.font(13));
            tooltip.setShowDelay(new Duration(100));
            button.setTooltip(tooltip);

            // Set the button in the cell
            label.setText(item + "  ");
            setGraphic(hBox);
            setText(null);
        }
    }
}

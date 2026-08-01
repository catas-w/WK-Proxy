package com.catas.wicked.proxy.gui.componet;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Orientation;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;

/**
 * to display time segment
 */
public class TimeSplitPane extends SplitPane {

    private Region[] regions = new Region[3];
    private StringProperty displayColor = new SimpleStringProperty("aquamarine");
    private IntegerProperty displayOrder = new SimpleIntegerProperty(0);
    private boolean segmentVisible = true;

    private static final String STYLE = "time-split-pane";

    public TimeSplitPane() {
        super();
        this.setOrientation(Orientation.HORIZONTAL);
        for (int i = 0; i < 3; i++) {
            AnchorPane anchorPane = new AnchorPane();
            if (i == 0) {
                anchorPane.setMinWidth(1);
            }
            regions[i] = anchorPane;
        }
        getItems().addAll(regions);
        // getChildren().addAll(regions);
        getStyleClass().add(STYLE);
        setDividerPositions(0.33, 0.66);
        this.setMouseTransparent(true);
        refreshSeg();
    }

    private void refreshSeg() {
        for (int i = 0; i < 3; i++) {
            Region pane = regions[i];
            if (i == displayOrder.get()) {
                pane.setVisible(segmentVisible);
                pane.setStyle("-fx-background-color: " + displayColor.get());
            } else {
                pane.setVisible(false);
            }
            regions[i] = pane;
        }
    }

    public String getDisplayColor() {
        return displayColor.get();
    }

    public StringProperty displayColorProperty() {
        return displayColor;
    }

    public void setDisplayColor(String displayColor) {
        this.displayColor.set(displayColor);
        refreshSeg();
    }

    public int getDisplayOrder() {
        return displayOrder.get();
    }

    public IntegerProperty displayOrderProperty() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder.set(displayOrder);
        refreshSeg();
    }

    public void setSegmentVisible(boolean visible) {
        segmentVisible = visible;
        refreshSeg();
    }
}

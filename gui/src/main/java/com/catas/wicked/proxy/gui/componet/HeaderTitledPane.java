package com.catas.wicked.proxy.gui.componet;

import com.jfoenix.controls.JFXCheckBox;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;

/**
 * select checkbox to display specific child node
 */
public class HeaderTitledPane extends TitledPane {

    private HBox hBox;
    private CheckBox checkBox;
    private StringProperty checkBoxTitle = new SimpleStringProperty("Raw");

    /**
     * which child to display when checked
     * 选中时展示第 checkIndex 个元素
     */
    private IntegerProperty checkIndex = new SimpleIntegerProperty(0);
    private static final String STYLE = "header-titled-pane";
    private static final String CHECKBOX_STYLE = "header-check-box";

    public HeaderTitledPane() {
        super();
        init("");
    }

    public HeaderTitledPane(String title, Node content) {
        super(title, content);
        init(title);
    }

    public void setCheckBoxTitle(String checkBoxTitle) {
        if (checkBoxTitle == null) {
            return;
        }
        this.checkBoxTitle.setValue(checkBoxTitle);
    }

    public String getCheckBoxTitle() {
        return checkBoxTitle.get();
    }

    public int getCheckIndex() {
        return checkIndex.get();
    }

    public IntegerProperty checkIndexProperty() {
        return checkIndex;
    }

    public void setCheckIndex(int checkIndex) {
        this.checkIndex.set(checkIndex);
    }

    public StringProperty checkBoxTitleProperty() {
        return checkBoxTitle;
    }

    private void init(String title) {
        Label label = new Label(title);
        checkBox = new JFXCheckBox();
        checkBox.textProperty().bindBidirectional(checkBoxTitleProperty());
        checkBox.getStyleClass().add(CHECKBOX_STYLE);
        checkBox.selectedProperty().addListener(observable -> {
            Node content = getContent();
            if (content instanceof AnchorPane anchorPane) {
                ObservableList<Node> children = anchorPane.getChildren();
                if (children.size() < 2) {
                    return;
                }
                BooleanProperty selected = (BooleanProperty) observable;
                for (int i = 0; i < children.size(); i++) {
                    // ignore message label
                    if (children.get(i) instanceof MessageLabel) {
                        continue;
                    }
                    if (i == checkIndex.get()) {
                        children.get(i).setVisible(selected.get());
                    } else {
                        children.get(i).setVisible(!selected.get());
                    }
                }
            }
        });
        checkBox.visibleProperty().bind(this.expandedProperty());

        hBox = new HBox();
        hBox.getChildren().addAll(label, checkBox);

        this.setGraphic(hBox);
        this.setContentDisplay(ContentDisplay.RIGHT);
        this.getStyleClass().add(STYLE);
    }
}

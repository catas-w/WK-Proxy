package com.catas.wicked.proxy.gui.componet.builder;

import com.catas.wicked.common.bean.PairEntry;
import com.jfoenix.controls.JFXTextArea;
import com.jfoenix.controls.cells.editors.base.EditorNodeBuilder;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumnBase;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;
import org.apache.commons.lang3.StringUtils;

public class PairTextAreaEditorNodeBuilder implements EditorNodeBuilder<PairEntry> {

    protected TextArea textArea;

    protected Text text;

    private final TableColumnBase tableColumn;

    public PairTextAreaEditorNodeBuilder(TableColumnBase treeItem) {
        this.tableColumn = treeItem;
    }

    @Override
    public void startEdit() {
        if (textArea == null || textArea.getText().isEmpty()) {
            return;
        }
        Platform.runLater(() -> {
            textArea.selectAll();
            textArea.requestFocus();
        });
    }

    @Override
    public void cancelEdit() {
        if (textArea != null) {
            textArea.deselect();
        }
    }

    @Override
    public void updateItem(PairEntry item, boolean empty) {
        if (textArea == null || textArea.getText().isEmpty()) {
            return;
        }
        Platform.runLater(() -> {
            textArea.selectAll();
            // textArea.requestFocus();
        });
    }

    @Override
    public Region createNode(PairEntry value, EventHandler<KeyEvent> keyEventsHandler, ChangeListener<Boolean> focusChangeListener) {
        if (value == null || StringUtils.isEmpty(value.getVal())) {
            return new Label();
        }

        textArea = new JFXTextArea();
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.getStyleClass().add("editor-text-area");
        textArea.setSkin(new TextAreaEditorNodeBuilder.EditorTextAreaSkin(textArea));

        text = new Text();
        text.wrappingWidthProperty().bind(tableColumn.widthProperty());
        textArea.textProperty().addListener((observable, oldValue, newValue) -> {
            computeHeight();
        });
        // textArea.setOnKeyPressed(keyEventsHandler);

        // fixme: text became invisible
        // textArea.focusedProperty().addListener(focusChangeListener);

        textArea.setText(value.getVal());
        return textArea;
    }

    private void computeHeight() {
        if (textArea == null) {
            return;
        }
        // Set the text of the Text object to the TextArea's content
        text.setText(textArea.getText().isEmpty() ? " " : textArea.getText());

        // Measure the required height
        double computedHeight = text.getBoundsInLocal().getHeight() + 7.5;

        textArea.setPrefHeight(computedHeight);
    }

    @Override
    public void setValue(PairEntry value) {

    }

    @Override
    public PairEntry getValue() {
        return null;
    }

    @Override
    public void validateValue() throws Exception {

    }

    @Override
    public void nullEditorNode() {

    }
}

package com.catas.wicked.proxy.gui.componet.builder;

import com.jfoenix.controls.JFXTextField;
import com.jfoenix.controls.cells.editors.base.EditorNodeBuilder;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;

/**
 * Table cell: selectable when double-clicked
 */
public class SelectableNodeBuilder implements EditorNodeBuilder<String> {

    protected TextField textField;

    public SelectableNodeBuilder() {}

    @Override
    public void startEdit() {
        if (textField == null || textField.getText().isEmpty()) {
            return;
        }
        Platform.runLater(() -> {
            textField.selectAll();
            textField.requestFocus();
        });
    }

    @Override
    public void cancelEdit() {

    }

    @Override
    public void updateItem(String item, boolean empty) {
        if (textField == null || textField.getText().isEmpty()) {
            return;
        }
        Platform.runLater(() -> {
            textField.selectAll();
            textField.requestFocus();
        });
    }

    @Override
    public Region createNode(String value, EventHandler<KeyEvent> keyEventsHandler, ChangeListener<Boolean> focusChangeListener) {
        if (value == null || value.length() == 0) {
            return new Label();
        }
        textField = new JFXTextField(value);
        textField.setEditable(false);
        textField.setOnKeyPressed(keyEventsHandler);
        // textField.getValidators().addAll(validators);
        textField.focusedProperty().addListener(focusChangeListener);
        // if (value == null) {
        //     textField.setDisable(true);
        // }
        return textField;
    }

    @Override
    public void setValue(String value) {
        if (textField == null) {
            return;
        }
        textField.setText(value);
    }

    @Override
    public String getValue() {
        if (textField == null) {
            return "";
        }
        return textField.getText();
    }

    @Override
    public void validateValue() throws Exception {
    }

    @Override
    public void nullEditorNode() {
        textField = null;
    }
}

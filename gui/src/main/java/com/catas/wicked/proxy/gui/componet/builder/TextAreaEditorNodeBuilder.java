package com.catas.wicked.proxy.gui.componet.builder;

import com.jfoenix.controls.JFXTextArea;
import com.jfoenix.controls.cells.editors.base.EditorNodeBuilder;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.event.EventHandler;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumnBase;
import javafx.scene.control.TextArea;
import javafx.scene.control.skin.TextAreaSkin;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;
import org.apache.commons.lang3.StringUtils;

public class TextAreaEditorNodeBuilder implements EditorNodeBuilder<String> {

    protected TextArea textArea;

    protected Text text;

    private final TableColumnBase tableColumn;

    // private TableColumnBase tableColumnBase;

    public TextAreaEditorNodeBuilder(TableColumnBase treeItem) {
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

    }

    @Override
    public void updateItem(String item, boolean empty) {
        if (textArea == null || textArea.getText().isEmpty()) {
            return;
        }
        Platform.runLater(() -> {
            textArea.selectAll();
            textArea.requestFocus();
        });
    }

    @Override
    public Region createNode(String value, EventHandler<KeyEvent> keyEventsHandler, ChangeListener<Boolean> focusChangeListener) {
        if (StringUtils.isEmpty(value)) {
            return new Label();
        }

        textArea = new JFXTextArea();
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.getStyleClass().add("editor-text-area");
        textArea.setSkin(new EditorTextAreaSkin(textArea));

        text = new Text();
        // text.setStyle("-fx-font-size: 13px;");
        text.wrappingWidthProperty().bind(tableColumn.widthProperty());
        textArea.textProperty().addListener((observable, oldValue, newValue) -> {
            computeHeight();
        });
        textArea.setOnKeyPressed(keyEventsHandler);
        textArea.focusedProperty().addListener(focusChangeListener);

        textArea.setText(value);
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
        // System.out.println("computedHeight: " + computedHeight + ": " + text.getText());

        textArea.setPrefHeight(computedHeight);
    }

    @Override
    public void setValue(String value) {
        if (textArea != null) {
            textArea.setText(value);
        }
    }

    @Override
    public String getValue() {
        if (textArea != null) {
            return textArea.getText();
        }
        return "";
    }

    @Override
    public void validateValue() throws Exception {

    }

    @Override
    public void nullEditorNode() {

    }

    public static class EditorTextAreaSkin extends TextAreaSkin {

        /**
         * Creates a new TextAreaSkin instance, installing the necessary child
         * nodes into the Control {@link Control#getChildren() children} list, as
         * well as the necessary input mappings for handling key, mouse, etc events.
         *
         * @param control The control that this skin should be installed onto.
         */
        public EditorTextAreaSkin(TextArea control) {
            super(control);
        }
    }
}

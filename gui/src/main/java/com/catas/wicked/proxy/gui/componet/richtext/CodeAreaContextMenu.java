package com.catas.wicked.proxy.gui.componet.richtext;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import org.apache.commons.lang3.StringUtils;
import org.fxmisc.richtext.CodeArea;

public class CodeAreaContextMenu extends ContextMenu {

    private final MenuItem copyItem = new MenuItem("Copy");

    public CodeAreaContextMenu(CodeArea codeArea) {
        MenuItem foldItem = new MenuItem("Fold selected text");
        foldItem.setOnAction(event -> {
            hide();
            fold();
        });

        MenuItem unfoldItem = new MenuItem("Unfold from cursor");
        unfoldItem.setOnAction(event -> {
            hide();
            unfold();
        });

        MenuItem selectAllItem = new MenuItem("Select All");
        selectAllItem.setOnAction(event -> {
            hide();
            codeArea.selectAll();
        });

        copyItem.setDisable(true);
        copyItem.setOnAction(event -> {
            hide();
            String selectedText = ((CodeArea) getOwnerNode()).getSelectedText();
            if (!StringUtils.isEmpty(selectedText)) {
                Clipboard clipboard = Clipboard.getSystemClipboard();
                ClipboardContent content = new ClipboardContent();
                content.putString(selectedText);
                clipboard.setContent(content);
            }
        });

        codeArea.selectedTextProperty().addListener((observable, oldValue, newValue) -> {
            copyItem.setDisable(StringUtils.isEmpty(newValue));
        });
        // MenuItem print = new MenuItem("Print");
        // print.setOnAction(AE -> { hide(); print(); } );

        getItems().addAll(selectAllItem, copyItem, foldItem, unfoldItem);
    }

    /**
     * Folds multiple lines of selected text, only showing the first line and hiding the rest.
     */
    private void fold() {
        ((CodeArea) getOwnerNode()).foldSelectedParagraphs();
    }

    /**
     * Unfold the CURRENT line/paragraph if it has a fold.
     */
    private void unfold() {
        CodeArea area = (CodeArea) getOwnerNode();
        area.unfoldParagraphs( area.getCurrentParagraph() );
    }

    private void print() {
        System.out.println( ((CodeArea) getOwnerNode()).getText() );
    }
}


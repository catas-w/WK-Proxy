package com.catas.wicked.proxy.gui.componet.richtext;

import com.catas.wicked.common.bean.message.OutputMessage;
import com.catas.wicked.common.config.ApplicationConfig;
import com.catas.wicked.common.factory.MessageSourceFactory;
import com.catas.wicked.common.pipeline.MessageQueue;
import com.catas.wicked.proxy.event.OutputFileEventHandler;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.FileChooser;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.fxmisc.richtext.CodeArea;

@Slf4j
public class CodeAreaContextMenu extends ContextMenu {

    private static final String STYLE = "code-area-context";

    private final MenuItem copyItem;

    private SimpleBooleanProperty saveItemDisabled = new SimpleBooleanProperty(false);

    private FileChooser fileChooser;

    private final OutputMessage.Source source;

    private final MessageQueue messageQueue;

    private final ApplicationConfig appConfig;

    @Setter
    private CodeArea codeArea;

    public CodeAreaContextMenu(MessageQueue messageQueue, ApplicationConfig appConfig, OutputMessage.Source source) {
        this.messageQueue = messageQueue;
        this.appConfig = appConfig;
        this.source = source;

        // MenuItem foldItem = new MenuItem("Fold selected text");
        // foldItem.setOnAction(event -> {
        //     hide();
        //     fold();
        // });
        // MenuItem unfoldItem = new MenuItem("Unfold from cursor");
        // unfoldItem.setOnAction(event -> {
        //     hide();
        //     unfold();
        // });

        MenuItem selectAllItem = new MenuItem(MessageSourceFactory.getMessage("context.menu.select-all"));
        selectAllItem.setOnAction(event -> {
            hide();
            if (codeArea != null) {
                codeArea.selectAll();
            }
        });

        copyItem = new MenuItem(MessageSourceFactory.getMessage("context.menu.copy"));
        copyItem.setDisable(true); // TODO
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

        MenuItem saveItem = new MenuItem(MessageSourceFactory.getMessage("context.menu.save"));
        saveItem.disableProperty().bind(saveItemDisabled);
        saveItem.setOnAction(new OutputFileEventHandler<>(source, messageQueue, appConfig, () -> getOwnerNode().getScene().getWindow()));

        getItems().addAll(selectAllItem, copyItem, saveItem);
        getStyleClass().add(STYLE);
    }

    public void setCodeArea(CodeArea codeArea) {
        this.codeArea = codeArea;
        codeArea.selectedTextProperty().addListener((observable, oldValue, newValue) -> {
            copyItem.setDisable(StringUtils.isEmpty(newValue));
        });
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
}


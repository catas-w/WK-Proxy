package com.catas.wicked.proxy.gui.componet.richtext;

import com.catas.wicked.common.constant.CodeStyle;
import com.catas.wicked.common.util.CommonUtils;
import com.catas.wicked.proxy.gui.componet.highlight.Formatter;
import com.catas.wicked.proxy.gui.componet.highlight.Highlighter;
import com.catas.wicked.proxy.gui.componet.highlight.HighlighterFactory;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.entity.ContentType;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.GenericStyledArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.Paragraph;
import org.fxmisc.richtext.model.StyleSpans;
import org.reactfx.collection.ListModification;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * codeArea wrapped in a scrollPane
 * support text highlight
 */
@Slf4j
public class DisplayCodeArea extends VirtualizedScrollPane<CodeArea> {

    private static final String STYLE = "display-code-area";

    public static final int MAX_TEXT_LENGTH = 50000;

    private final CodeArea codeArea;

    private final StringProperty codeStyle = new SimpleStringProperty(CodeStyle.PLAIN.name());

    private String originText;

    private int appendixLen = 0;

    @Getter
    private ContentType contentType;

    public DisplayCodeArea(CodeArea codeArea) {
        super(codeArea);
        this.codeArea = codeArea;
        initCodeArea();
    }

    public DisplayCodeArea() {
        super(new CodeArea());
        this.codeArea = this.getContent();
        initCodeArea();
    }

    public void setWrapText(boolean wrapText) {
        // TODO performance
        codeArea.setWrapText(wrapText);
    }

    public String getCodeStyle() {
        return codeStyle.get();
    }

    public StringProperty codeStyleProperty() {
        return codeStyle;
    }

    public void setCodeStyle(String codeStyle) {
        // this.codeStyle.set(codeStyle);
        setCodeStyle(CodeStyle.valueOf(codeStyle), false);
    }

    public void setContentType(ContentType contentType) {
        this.contentType = contentType;
    }

    /**
     * switch text highlight
     * @param codeStyle codeStyle
     * @param refreshStyle force to update codeStyle
     */
    public void setCodeStyle(CodeStyle codeStyle, boolean refreshStyle) {
        this.codeStyle.set(codeStyle.name());

        // refresh style
        if (refreshStyle) {
            refreshStyle();
        }
    }

    public void replaceText(String text, boolean refreshStyle) {
        if (text != null && text.length() > MAX_TEXT_LENGTH) {
            log.info("Text is too long, truncate it.");
            text = CommonUtils.truncate(text, MAX_TEXT_LENGTH);
            appendixLen = text.length() - MAX_TEXT_LENGTH;
        } else {
            appendixLen = 0;
        }

        this.originText = text;
        if (refreshStyle) {
            refreshStyle();
            // ThreadPoolService.getInstance().run(this::refreshStyle);
        } else {
            String finalText = text;
            Platform.runLater(() -> {
                codeArea.replaceText(finalText);
            });
        }
    }

    /**
     * force to update style
     */
    private void refreshStyle() {
        if (StringUtils.isEmpty(originText)) {
            return;
        }
        Highlighter<Collection<String>> highlighter = getCurrentHighlighter();
        assert highlighter != null;

        String formatText = originText;
        if (highlighter instanceof Formatter formatter) {
            formatText = formatter.format(originText, this.contentType);
        }

        if (!StringUtils.equals(formatText, codeArea.getText())) {
            String finalText = formatText;
            Platform.runLater(() -> {
                codeArea.replaceText(finalText);
            });
        }

        Platform.runLater(() -> {
            StyleSpans<Collection<String>> styleSpans = highlighter.computeHighlight(codeArea.getText());
            codeArea.setStyleSpans(0, styleSpans);
            if (appendixLen > 0) {
                codeArea.setStyle(codeArea.getLength() - appendixLen, codeArea.getLength(), Collections.singleton("appendix"));
            }
        });
    }

    private Highlighter<Collection<String>> getCurrentHighlighter() {
        return HighlighterFactory.getHighlightComputer(CodeStyle.valueOf(getCodeStyle()));
    }

    private void initCodeArea() {
        AnchorPane.setLeftAnchor(this, 0.0);
        AnchorPane.setRightAnchor(this, 0.0);
        AnchorPane.setTopAnchor(this, 0.0);
        AnchorPane.setBottomAnchor(this, 0.0);

        this.getStyleClass().add(STYLE);
        codeArea.setEditable(false);
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.setContextMenu(new CodeAreaContextMenu(this.codeArea));

        // this.visibleParagraphStyler =new VisibleParagraphStyler<>(codeArea, getCurrentHighlighter());
        // codeArea.getVisibleParagraphs().addModificationObserver(visibleParagraphStyler);

        // 改为手动刷新 code style
        // this.textStyler = new TextStyler(codeArea, getCurrentHighlighter());
        // codeArea.textProperty().addListener(textStyler);

        // auto-indent: insert previous line's indents on enter
        final Pattern whiteSpace = Pattern.compile( "^\\s+" );
        codeArea.addEventHandler( KeyEvent.KEY_PRESSED, KE ->
        {
            if ( KE.getCode() == KeyCode.ENTER ) {
                int caretPosition = codeArea.getCaretPosition();
                int currentParagraph = codeArea.getCurrentParagraph();
                Matcher m0 = whiteSpace.matcher( codeArea.getParagraph( currentParagraph-1 ).getSegments().get( 0 ) );
                if ( m0.find() ) Platform.runLater( () -> codeArea.insertText( caretPosition, m0.group() ) );
            }
        });
    }

    /**
     * set style by listener
     */
    private static class TextStyler implements ChangeListener<String> {

        private final CodeArea area;

        private Highlighter<Collection<String>> highlightComputer;

        public TextStyler(CodeArea area, Highlighter<Collection<String>> highlightComputer) {
            this.area = area;
            this.highlightComputer = highlightComputer;
        }

        public void setHighlightComputer(Highlighter<Collection<String>> highlightComputer) {
            this.highlightComputer = highlightComputer;
        }

        @Override
        public void changed(ObservableValue observable, String oldValue, String newValue) {
            // TODO efficiency
            area.setStyleSpans(0, highlightComputer.computeHighlight(newValue));
        }
    }

    /**
     * set style for visible paragraph
     */
    private static class VisibleParagraphStyler<PS, SEG, S> implements
            Consumer<ListModification<? extends Paragraph<PS, SEG, S>>>
    {
        private final GenericStyledArea<PS, SEG, S> area;

        private Highlighter<S> highlightComputer;

        private int prevParagraph, prevTextLength;

        public VisibleParagraphStyler(GenericStyledArea<PS, SEG, S> area, Highlighter<S> highlightComputer) {
            this.area = area;
            this.highlightComputer = highlightComputer;
        }

        public void setHighlightComputer(Highlighter<S> highlightComputer) {
            this.highlightComputer = highlightComputer;
        }

        @Override
        public void accept(ListModification<? extends Paragraph<PS, SEG, S>> lm)
        {
            if ( lm.getAddedSize() > 0 ) Platform.runLater( () ->
            {
                int paragraph = Math.min( area.firstVisibleParToAllParIndex() + lm.getFrom(), area.getParagraphs().size()-1 );
                String text = area.getText( paragraph, 0, paragraph, area.getParagraphLength( paragraph ) );

                if ( paragraph != prevParagraph || text.length() != prevTextLength )
                {
                    if ( paragraph < area.getParagraphs().size()-1 )
                    {
                        int startPos = area.getAbsolutePosition( paragraph, 0 );
                        area.setStyleSpans(startPos, highlightComputer.computeHighlight(text));
                    }
                    prevTextLength = text.length();
                    prevParagraph = paragraph;
                }
            });
        }
    }
}

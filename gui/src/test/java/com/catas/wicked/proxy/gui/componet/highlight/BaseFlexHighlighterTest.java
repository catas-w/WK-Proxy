package com.catas.wicked.proxy.gui.componet.highlight;

import com.catas.wicked.proxy.lexer.XmlLexer;
import org.fxmisc.richtext.model.StyleSpans;
import org.junit.Test;

import java.util.Collection;

import static org.junit.Assert.assertEquals;

public class BaseFlexHighlighterTest {

    private final BaseFlexHighlighter<XmlLexer> highlighter =
            new BaseFlexHighlighter<>(new XmlLexer());

    @Test
    public void returnsDefaultSpanWhenLexerFindsNoTokens() {
        String text = "plain response body";

        StyleSpans<Collection<String>> spans = highlighter.computeHighlight(text);

        assertEquals(text.length(), spans.length());
        assertEquals(1, spans.getSpanCount());
    }

    @Test
    public void returnsValidSpanForEmptyText() {
        StyleSpans<Collection<String>> spans = highlighter.computeHighlight("");

        assertEquals(0, spans.length());
        assertEquals(1, spans.getSpanCount());
    }

    @Test
    public void coversTrailingUnstyledText() {
        String text = "<node>tail";

        StyleSpans<Collection<String>> spans = highlighter.computeHighlight(text);

        assertEquals(text.length(), spans.length());
    }
}

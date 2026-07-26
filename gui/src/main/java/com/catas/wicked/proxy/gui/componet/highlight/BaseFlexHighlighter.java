package com.catas.wicked.proxy.gui.componet.highlight;

import com.catas.wicked.proxy.lexer.DefaultJFlexLexer;
import com.catas.wicked.proxy.lexer.Token;
import com.catas.wicked.proxy.lexer.TokenType;
import lombok.extern.slf4j.Slf4j;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.io.StringReader;
import java.util.Collection;
import java.util.Collections;

@Slf4j
public class BaseFlexHighlighter<T extends DefaultJFlexLexer> implements Highlighter<Collection<String>> {

    private final T lexer;

    public BaseFlexHighlighter(T lexer) {
        this.lexer = lexer;
    }

    @Override
    public StyleSpans<Collection<String>> computeHighlight(String text) {
        String source = text == null ? "" : text;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        Collection<String> defaultStyle = Collections.singleton(TokenType.DEFAULT.name().toLowerCase());
        try {
            lexer.yyreset(new StringReader(source));

            int lastEnd = 0;

            for (Token token: lexer.parse()) {
                if (token.start > lastEnd) {
                    spansBuilder.add(defaultStyle, token.start - lastEnd);
                }
                if (token.length > 0) {
                    spansBuilder.add(Collections.singleton(token.type.name().toLowerCase()), token.length);
                }
                lastEnd = token.end();
            }
            if (lastEnd < source.length()) {
                spansBuilder.add(defaultStyle, source.length() - lastEnd);
            } else if (source.isEmpty()) {
                spansBuilder.add(defaultStyle, 0);
            }
        } catch (Exception e) {
            log.error("Error in computing highlight: ", e);
            return defaultHighlight(source, defaultStyle);
        }
        return spansBuilder.create();
    }

    private StyleSpans<Collection<String>> defaultHighlight(
            String text, Collection<String> defaultStyle) {
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        spansBuilder.add(defaultStyle, text.length());
        return spansBuilder.create();
    }
}

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
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        try {
            lexer.yyreset(new StringReader(text));

            int lastEnd = 0;

            for (Token token: lexer.parse()) {
                spansBuilder.add(Collections.singleton(TokenType.DEFAULT.name().toLowerCase()), token.start - lastEnd);
                spansBuilder.add(Collections.singleton(token.type.name().toLowerCase()), token.length);
                lastEnd = token.end();
            }
        } catch (Exception e) {
            log.error("Error in computing highlight: ", e);
        }
        return spansBuilder.create();
    }
}

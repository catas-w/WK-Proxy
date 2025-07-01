package com.catas.wicked.proxy.gui.componet.highlight;

import com.catas.wicked.common.constant.CodeStyle;
import com.catas.wicked.proxy.lexer.XHTMLLexer;
import com.catas.wicked.proxy.lexer.XmlLexer;

import java.util.HashMap;
import java.util.Map;

public class HighlighterFactory {

    private static volatile Map<CodeStyle, Highlighter> map;

    private static final Object lock = new Object();

    private static final OriginHighlighter defaultHighlighter = new OriginHighlighter();

    @SuppressWarnings("unchecked")
    public static <S> Highlighter<S> getHighlightComputer(CodeStyle codeStyle) {
        if (map == null) {
            synchronized (lock) {
                if (map == null) {
                    map = new HashMap<>();
                    // map.put(CodeStyle.JSON, new JsonHighlighter());
                    map.put(CodeStyle.JSON, new JsonFlexHighlighter());
                    map.put(CodeStyle.XML, new BaseFlexHighlighter<>(new XmlLexer()));
                    map.put(CodeStyle.HEADER, new HeaderHighlighter());
                    map.put(CodeStyle.HTML, new BaseFlexHighlighter<>(new XHTMLLexer()));
                    map.put(CodeStyle.QUERY_FORM, new QueryHighlighter());
                    map.put(CodeStyle.MULTIPART_FORM, new MultipartHighlighter());
                    map.put(CodeStyle.HEX, new HexHighlighter());
                }
            }
        }
        return map.getOrDefault(codeStyle, defaultHighlighter);
    }
}

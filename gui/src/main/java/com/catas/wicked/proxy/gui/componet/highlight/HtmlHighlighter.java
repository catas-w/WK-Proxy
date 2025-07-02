package com.catas.wicked.proxy.gui.componet.highlight;

import org.fxmisc.richtext.model.StyleSpans;

import java.util.Collection;

@Deprecated
public class HtmlHighlighter extends XmlHighlighter {

    @Override
    public StyleSpans<Collection<String>> computeHighlight(String text) {
        return super.computeHighlight(text);
    }
}

package com.catas.wicked.proxy.gui.componet.highlight;

import com.catas.wicked.proxy.lexer.JsonLexer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.entity.ContentType;
import org.fxmisc.richtext.model.StyleSpans;

import java.util.Collection;

@Slf4j
public class JsonFlexHighlighter implements Highlighter<Collection<String>>, Formatter{

    private final Highlighter<Collection<String>> jsonLexer;
    private final ObjectMapper objectMapper;
    private final DefaultPrettyPrinter printer;


    public JsonFlexHighlighter() {
        jsonLexer = new BaseFlexHighlighter<>(new JsonLexer());

        objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        DefaultPrettyPrinter.Indenter indenter =
                new DefaultIndenter("\t", DefaultIndenter.SYS_LF);
        printer = new DefaultPrettyPrinter();
        printer.indentObjectsWith(indenter);
        printer.indentArraysWith(indenter);
    }

    @Override
    public String format(String text, ContentType contentType) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        try {
            Object json = objectMapper.readValue(text, Object.class);
            return objectMapper.writer(printer).writeValueAsString(json);
        } catch (JsonProcessingException e) {
            return text;
        }
    }

    @Override
    public StyleSpans<Collection<String>> computeHighlight(String text) {
        return jsonLexer.computeHighlight(text);
    }
}

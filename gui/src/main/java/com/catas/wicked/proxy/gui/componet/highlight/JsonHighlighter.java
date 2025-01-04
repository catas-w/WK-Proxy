package com.catas.wicked.proxy.gui.componet.highlight;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.entity.ContentType;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JSON highlighter by regular expression
 */
@Slf4j
public class JsonHighlighter implements Highlighter<Collection<String>>, Formatter {

    private static final String STRING_PATTERN = "\"([^\"\\\\]|\\\\.)*\"";
    private static final String KEY_PATTERN = "\"(?<KEY>\\w+?)\"\\s*?:";
    private static final String VAL_STR_PATTERN = "\"(?<STR>.+)\",{0,1}$";
    private static final String VAL_NUM_PATTERN = "(?<NUM>\\d+),{0,1}$";
    private static final String BOOLEAN_PATTERN = "(?<BOOL>true|false|null),{0,1}$";
    private static final String BRACE_PATTERN = "(?<BRACE>\\{|\\})";
    private static final String BRACKET_PATTERN = "(?<BRACKET>\\[|\\])";

    private static final Pattern PATTERN = Pattern.compile(
            String.join("|", KEY_PATTERN, VAL_NUM_PATTERN, VAL_STR_PATTERN,
                    BOOLEAN_PATTERN, BRACE_PATTERN, BRACKET_PATTERN),
            Pattern.MULTILINE);

    private final ObjectMapper objectMapper;
    private final DefaultPrettyPrinter printer;
    private final Map<String, String> styleMap;

    public JsonHighlighter() {
        objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        styleMap = new HashMap<>();
        styleMap.put("KEY", "keyword");
        styleMap.put("NUM", "number");
        styleMap.put("BOOL", "bool");
        styleMap.put("STR", "string");
        styleMap.put("BRACE", "brace");
        styleMap.put("BRACKET", "bracket");

        DefaultPrettyPrinter.Indenter indenter =
                new DefaultIndenter("\t", DefaultIndenter.SYS_LF);
        printer = new DefaultPrettyPrinter();
        printer.indentObjectsWith(indenter);
        printer.indentArraysWith(indenter);
    }

    @Override
    public StyleSpans<Collection<String>> computeHighlight(String text) {
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        try {
            JsonNode ignored = objectMapper.readTree(text);
        } catch (JsonProcessingException e) {
            log.info("Error parsing json text.");
            return spansBuilder.create();
        }

        Matcher matcher = PATTERN.matcher(text);
        int lastKwEnd = 0;
        while(matcher.find()) {
            String group =
                    matcher.group("KEY") != null ? "KEY" :
                    matcher.group("STR") != null ? "STR" :
                    matcher.group("NUM") != null ? "NUM" :
                    matcher.group("BOOL") != null ? "BOOL" :
                    matcher.group("BRACE") != null ? "BRACE" :
                    matcher.group("BRACKET") != null ? "BRACKET" :
                    null;
            if (group == null) {
                continue;
            }

            String styleClass = styleMap.getOrDefault(group, "");
            spansBuilder.add(Collections.emptyList(), matcher.start(group) - lastKwEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end(group) - matcher.start(group));
            lastKwEnd = matcher.end(group);
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
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
            // System.out.println("Json format error");
            return text;
        }
    }
}

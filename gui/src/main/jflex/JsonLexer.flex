package com.catas.wicked.proxy.lexer;

%%

%public
%class JsonLexer
%extends DefaultJFlexLexer
%final
%unicode
%char
%type Token

%{
    /**
     * Create an empty lexer, yyrset will be called later to reset and assign
     * the reader
     */
    public JsonLexer() {
        super();
    }

    @Override
    public int yychar() {
        return yychar;
    }

    private static final byte PARAN     = 1;
    private static final byte BRACKET   = 2;
    private static final byte CURLY     = 3;
%}

WHITESPACE = [ \t\r\n]+
NUMBER     = -?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+-]?[0-9]+)?
STRING     = \"([^\"\\]|\\.)*\"
KEYWORD    = true|false|null
%state AFTER_COLON
%state INITIAL
%state IN_ARRAY_VALUE

%%


"{" {
    // start of object → back to key mode
    yybegin(INITIAL);
    return token(TokenType.OPERATOR, CURLY);
}
"}" {
    return token(TokenType.OPERATOR, -CURLY);
}
<INITIAL,AFTER_COLON,IN_ARRAY_VALUE> "[" {
    // entering array → special array value mode
    yybegin(IN_ARRAY_VALUE);
    return token(TokenType.OPERATOR, BRACKET);
}
<IN_ARRAY_VALUE> "]" {
    yybegin(INITIAL); // exit array → back to key mode
    return token(TokenType.OPERATOR, -BRACKET);
}
":" {
    yybegin(AFTER_COLON);
    return token(TokenType.OPERATOR);
}
<INITIAL,AFTER_COLON,IN_ARRAY_VALUE> "," {
    // do not change state, caller handles it
    return token(TokenType.OPERATOR);
}

// === KEY ===
<INITIAL> {STRING} {
    return token(TokenType.KEY);
}

// === VALUE ===
// After colon → next string/number/etc is a value
<AFTER_COLON> {STRING}  {
    yybegin(INITIAL);
    return token(TokenType.STRING);
}
<AFTER_COLON> {NUMBER}  {
    yybegin(INITIAL);
    return token(TokenType.NUMBER);
}
<AFTER_COLON> {KEYWORD} {
    yybegin(INITIAL);
    return token(TokenType.KEYWORD2);
}
<AFTER_COLON> "{" {
    yybegin(INITIAL);  // nested object
    return token(TokenType.OPERATOR, CURLY);
}
<AFTER_COLON> "}" {
    return token(TokenType.OPERATOR, -CURLY);
}

// === ARRAY VALUES ===
<IN_ARRAY_VALUE> {STRING}  { return token(TokenType.STRING); }
<IN_ARRAY_VALUE> {NUMBER}  { return token(TokenType.NUMBER); }
<IN_ARRAY_VALUE> {KEYWORD} { return token(TokenType.KEYWORD2); }
<IN_ARRAY_VALUE> "{" {
    yybegin(INITIAL); // handle nested object inside array
    return token(TokenType.OPERATOR, CURLY);
}
<IN_ARRAY_VALUE> "}" {
    yybegin(IN_ARRAY_VALUE); // return from nested object
    return token(TokenType.OPERATOR, -CURLY);
}

{WHITESPACE}             { /* skip whitespace */ }
.|\n                     { /* skip unknown character */ }

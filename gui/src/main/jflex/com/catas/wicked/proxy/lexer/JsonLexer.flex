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

%%

"{"            { return token(TokenType.OPERATOR,  CURLY); }
"}"            { return token(TokenType.OPERATOR, -CURLY); }
"["            { return token(TokenType.OPERATOR,  BRACKET); }
"]"            { return token(TokenType.OPERATOR, -BRACKET); }
":"            { return token(TokenType.OPERATOR); }
","            { return token(TokenType.OPERATOR); }
{NUMBER}       { return token(TokenType.NUMBER); }
{STRING}       { return token(TokenType.STRING); }
{KEYWORD}      { return token(TokenType.KEYWORD); }

.|\n                          {  }

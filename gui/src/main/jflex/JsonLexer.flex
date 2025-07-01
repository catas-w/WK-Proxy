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

%%

"{"             { yybegin(INITIAL); return token(TokenType.OPERATOR,  CURLY); }
"}"             { yybegin(INITIAL); return token(TokenType.OPERATOR, -CURLY); }
"["             { yybegin(INITIAL); return token(TokenType.OPERATOR,  BRACKET); }
"]"             { yybegin(INITIAL); return token(TokenType.OPERATOR, -BRACKET); }
":"            {
                  yybegin(AFTER_COLON);  // 进入 value 状态
                  return token(TokenType.OPERATOR);
               }
","            {
                  yybegin(INITIAL); // 重置回 key 状态（或数组值）
                  return token(TokenType.OPERATOR);
               }
// 在 INITIAL 状态中：匹配 key
<INITIAL>       {STRING}   { return token(TokenType.KEY); }
<AFTER_COLON>   {STRING}   { yybegin(INITIAL); return token(TokenType.STRING); }
<AFTER_COLON>   {NUMBER}   { yybegin(INITIAL); return token(TokenType.NUMBER); }
<AFTER_COLON>   {KEYWORD}  { yybegin(INITIAL); return token(TokenType.KEYWORD2); }
<AFTER_COLON>   "{"        { yybegin(INITIAL); return token(TokenType.OPERATOR,  CURLY); }
<AFTER_COLON>   "}"        { yybegin(INITIAL); return token(TokenType.OPERATOR, -CURLY); }
<AFTER_COLON>   "["        { yybegin(INITIAL); return token(TokenType.OPERATOR,  BRACKET); }
<AFTER_COLON>   "]"        { yybegin(INITIAL); return token(TokenType.OPERATOR, -BRACKET); }

{WHITESPACE}             { /* skip whitespace */ }
.|\n                     { /* skip unknown character */ }

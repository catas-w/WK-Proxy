/*
 * Copyright 2008 Ayman Al-Sairafi ayman.alsairafi@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License
 *       at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.catas.wicked.proxy.lexer;

/**
 * These are the various token types supported by SyntaxPane.
 *
 * @author ayman
 */
public enum TokenType {

    OPERATOR, // Language operators
    DELIMITER, // Delimiters.  Constructs that are not necessarily operators for a language
    KEYWORD, // language reserved keywords
    KEYWORD2, // Other language reserved keywords, like C #defines
    IDENTIFIER, // identifiers, variable names, class names
    NUMBER,     // numbers in various formats
    STRING,     // String
    STRING2,    // For highlighting meta chars within a String
    COMMENT,    // comments
    COMMENT2,   // special stuff within comments
    REGEX,      // regular expressions
    REGEX2,     // special chars within regular expressions
    TYPE,       // Types, usually not keywords, but supported by the language
    TYPE2,      // Types from standard libraries
    TYPE3,      // Types for users
    DEFAULT,    // any other text
    WARNING,    // Text that should be highlighted as a warning
    ERROR,      // Text that signals an error
    KEY,        // Key value
    ;

    /**
     * Tests if the given token is a Comment Token.
     * @param t token
     * @return true if comment
     */
    public static boolean isComment(Token t) {
        return t != null && (t.type == COMMENT || t.type == COMMENT2);
    }

    /**
     * Tests if the given token is a Keyword Token.
     * @param t token
     * @return true if keyword
     */
    public static boolean isKeyword(Token t) {
        return t != null && (t.type == KEYWORD || t.type == KEYWORD2);
    }


    /**
     * Tests if the given token is a String Token.
     * @param t token
     * @return true if string
     */
    public static boolean isString(Token t) {
        return t != null && (t.type == STRING || t.type == STRING2);
    }
}
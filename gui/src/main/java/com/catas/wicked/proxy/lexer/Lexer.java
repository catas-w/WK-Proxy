/*
 * Copyright 2021 Eugene Ryzhikov eryzhikov@hotmail.com
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

import java.util.Collection;

/**
 * Lexers must implement these methods.  These are used in the Tokenizer
 *
 * A Lexer should be tied to one document.
 *
 * @author Ayman Al-Sairafi
 */
public interface Lexer {

    /**
     * This is the only method a Lexer needs to implement.  It will be passed
     * a Reader, and it should return non-overlapping Tokens for each recognized token
     * in the stream.
     * @return collection of Tokens obtained.
     */
    Collection<Token> parse();
}

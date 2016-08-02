/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.commons.net.imap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

/**
 * IMAPClientLineReader implements a readLine() method that requires exactly CRLF to terminate an input line
 * (unlike BufferedReader, where CR or LF can also mark the end of a line). Also, it doesn't finish a line if
 * it contains a CRLF inside a string of type literal
 *
 * @since 3.5.1
 */
public final class IMAPClientLineReader extends BufferedReader
{

    /**
     * Creates an IMAPClientLineReader that wraps an existing Reader
     * input source.
     * @param reader  The Reader input source.
     */
    public IMAPClientLineReader(Reader reader)
    {
        super(reader);
    }

    /**
     * Read a line of text.
     * A line is considered to be terminated by carriage return followed immediately by a linefeed, unless it happens
     * inside a string of type literal.
     * This contrasts with BufferedReader which also allows other combinations.
     * @since 3.5.1
     */
    @Override
    public String readLine() throws IOException {
        StringBuilder buffer = new StringBuilder();
        long literalLength = 0;
        int ch;

        ParseState state = ParseState.NORMAL;

        synchronized(lock) { // make thread-safe (hopefully!)
            while((ch = read()) != -1) {

                switch(state) {
                    case NORMAL:
                        switch(ch) {
                            case '\r': state = ParseState.CR; break;
                            case '{': state = ParseState.LITERAL_LENGTH; literalLength = 0; break;
                            default: buffer.append((char)ch);
                        }
                        break;
                    case CR:
                        if(ch == '\n') {
                            return buffer.toString();
                        } else {
                            buffer.append('\r').append((char)ch);
                            state = ParseState.NORMAL;
                        }
                        break;
                    case LITERAL_LENGTH:
                        if(ch >= '0' && ch <= '9') {
                            literalLength = literalLength * 10 + ch - 0x30;
                        } else if(ch == '}') {
                            state = ParseState.LITERAL_END;
                        } else {
                            throw new IOException("Invalid character '" + (char)ch + "', expected digit after '" +
                                    buffer + "{" + literalLength + "'");
                        }
                        break;
                    case LITERAL_END:
                        if(ch != '\r')
                            throw new IOException("Invalid character '" + (char)ch + "', expected CR after '" +
                                    buffer + "{" + literalLength + "}'");
                        state = ParseState.LITERAL_CR;
                        break;
                    case LITERAL_CR:
                        if(ch != '\n')
                            throw new IOException("Invalid character '" + (char)ch + "', expected LF after '" +
                                    buffer + "{" + literalLength + "}'[CR]");

                        buffer.append('{').append(literalLength).append("}\r\n");
                        while(literalLength-- > 0 && (ch = read()) != -1) buffer.append((char)ch);

                        if(literalLength > 0)
                            throw new IOException("Unexpected end of stream. Unfinished line '" + buffer + "'");

                        state = ParseState.NORMAL;
                        break;
                }

            }
        }
        return buffer.length() > 0? buffer.toString(): null;
    }

    private enum ParseState { NORMAL, CR, LITERAL_LENGTH, LITERAL_END, LITERAL_CR }
}

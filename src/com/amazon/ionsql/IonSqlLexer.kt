/*
 * Copyright 2016 Amazon.com, Inc. or its affiliates.  All rights reserved.
 */

package com.amazon.ionsql

import com.amazon.ion.IonSystem
import com.amazon.ionsql.IonSqlLexer.LexType.*
import com.amazon.ionsql.IonSqlLexer.StateType.*
import com.amazon.ionsql.TokenType.*
import java.math.BigDecimal
import java.math.BigInteger
import java.util.*


/**
 * Simple tokenizer for IonSQL++.
 */
class IonSqlLexer(private val ion: IonSystem) : Lexer {
    /** Transition types. */
    internal enum class StateType(val beginsToken: Boolean = false,
                                  val endsToken: Boolean = false) {
        /** Indicates the initial state for recognition. */
        INITIAL(),
        /** Indicates an error state. */
        ERROR(),
        /** Indicates the middle of a token. */
        INCOMPLETE(),
        /** Indicates the begining of a new token. */
        START(beginsToken = true),
        /** A state that is both a start and terminal. */
        START_AND_TERMINAL(beginsToken = true, endsToken = true),
        /** Indicates a possible termination point for a token. */
        TERMINAL(endsToken = true),
    }

    /** Lexer types. */
    internal enum class LexType {
        /** No particular lexer type. */
        NONE,
        /* Integral number. */
        INTEGER,
        /* Decimal number. */
        DECIMAL,
        /** Single quoted string. */
        SQ_STRING,
        /** Double quoted string. */
        DQ_STRING,
        /** Ion literals. */
        ION_LITERAL,
        /** Whitespace. */
        WHITESPACE,
    }

    /** Representation of a Lexer state machine node. */
    internal interface State {
        val stateType: StateType
        val tokenType: TokenType?
            get() = null
        val lexType: LexType
            get() = NONE
        val replacement: Int
            get() = REPLACE_SAME

        /** Retrieves the next state from this one with the transition code point. */
        operator fun get(next: Int): State
    }

    /** Simple repeating state. */
    internal class RepeatingState(override val stateType: StateType) : State {
        override fun get(next: Int): State = this
    }
    
    /** State node and corresponding state table. */
    internal class TableState(override val stateType: StateType,
                              override val tokenType: TokenType? = null,
                              override val lexType: LexType = NONE,
                              override val replacement: Int = REPLACE_SAME,
                              var delegate: State = ERROR_STATE,
                              setup: TableState.() -> Unit = { }) : State {
        /** Default table with null states. */
        val table = Array<State?>(TABLE_SIZE) { null }

        init {
            setup()
        }

        operator fun Array<State?>.set(chars: String, new: State) = chars.forEach {
            val cp = it.toInt()
            val old = this[cp]
            this[cp] = when(old) {
                null -> new
                else -> throw IllegalStateException("Cannot replace existing state $old with $new")
            }
        }

        private fun getFromTable(next: Int): State? = when {
            next < TABLE_SIZE -> table[next]
            else -> null
        }

        override fun get(next: Int): State = getFromTable(next) ?: delegate[next]

        fun selfRepeatingDelegate(stateType: StateType) {
            delegate = object : State {
                override val stateType: StateType = stateType
                override fun get(next: Int): State = getFromTable(next) ?: this
            }
        }

        fun delta(chars: String,
                  stateType: StateType,
                  tokenType: TokenType? = null,
                  lexType: LexType = NONE,
                  replacement: Int = REPLACE_SAME,
                  delegate: State = this,
                  setup: TableState.(String) -> Unit = { }): TableState {
            val child = TableState(stateType, tokenType, lexType, replacement, delegate) {
                setup(chars)
            }
            table[chars] = child
            return child
        }

        fun noRepeat(chars: String) {
            table[chars] = ERROR_STATE
        }
    }

    /** Simple line/column tracker. */
    internal class PositionTracker {
        var line = 1L
        var col = 0L
        var sawCR = false

        fun newline() {
            line++
            col = 0L
        }

        fun advance(next: Int) {
            when (next) {
                CR -> when {
                    sawCR -> newline()
                    else -> sawCR = true
                }
                LF -> {
                    newline()
                    sawCR = false
                }
                else -> {
                    if (sawCR) {
                        newline()
                        sawCR = false
                    }
                    col++
                }
            }
        }

        val position: SourcePosition
            get() = SourcePosition(line, col)

        override fun toString(): String = position.toString()
    }

    companion object {
        private val CR = '\r'.toInt()
        private val LF = '\n'.toInt()

        /** The synthetic EOF code point. */
        private val EOF = -1

        /** Code point range that table-driven lexing will operate on. */
        private val TABLE_SIZE = 127

        /** Do not replace character. */
        private val REPLACE_SAME = -1

        /** Replace with nothing. */
        private val REPLACE_NOTHING = -2

        /** Synthetic state for EOF to trigger a flush of the last token. */
        private val EOF_STATE = RepeatingState(START)

        /** Error state. */
        private val ERROR_STATE = RepeatingState(ERROR)

        /** Initial state. */
        private val INITIAL_STATE = TableState(StateType.INITIAL) {
            val initialState = this

            delta("(", START_AND_TERMINAL, LEFT_PAREN)
            delta(")", START_AND_TERMINAL, RIGHT_PAREN)
            delta("[", START_AND_TERMINAL, LEFT_BRACKET)
            delta("]", START_AND_TERMINAL, RIGHT_BRACKET)
            delta("{", START_AND_TERMINAL, LEFT_CURLY)
            delta("}", START_AND_TERMINAL, RIGHT_CURLY)
            delta(":", START_AND_TERMINAL, COLON)
            delta(",", START_AND_TERMINAL, COMMA)
            delta("*", START_AND_TERMINAL, STAR)

            delta(NON_OVERLOADED_OPERATOR_CHARS, START_AND_TERMINAL, OPERATOR) {
                delta(OPERATOR_CHARS, TERMINAL, OPERATOR)
            }

            delta(IDENT_START_CHARS, START_AND_TERMINAL,IDENTIFIER) {
                delta(IDENT_CONTINUE_CHARS, TERMINAL, IDENTIFIER)
            }

            fun TableState.deltaDecimalInteger(stateType: StateType, lexType: LexType, setup: TableState.(String) -> Unit = { }): Unit {
                delta(DIGIT_CHARS, stateType, LITERAL, lexType, delegate = initialState) {
                    delta(DIGIT_CHARS, TERMINAL, LITERAL, lexType)
                    setup(it)
                }
            }

            fun TableState.deltaDecimalFraction(setup: TableState.(String) -> Unit = { }): Unit {
                delta(".", TERMINAL, LITERAL, DECIMAL) {
                    deltaDecimalInteger(TERMINAL, DECIMAL, setup)
                }
            }

            fun TableState.deltaExponent(setup: TableState.(String) -> Unit = { }): Unit {
                delta(E_NOTATION_CHARS, INCOMPLETE, delegate = ERROR_STATE) {
                    delta(SIGN_CHARS, INCOMPLETE, delegate = ERROR_STATE) {
                        deltaDecimalInteger(TERMINAL, DECIMAL, setup)
                    }
                    deltaDecimalInteger(TERMINAL, DECIMAL, setup)
                }
            }

            fun TableState.deltaNumber(stateType: StateType) {
                deltaDecimalInteger(stateType, INTEGER) {
                    deltaDecimalFraction {
                        deltaExponent { }
                    }
                    deltaExponent { }
                }
                when (stateType) {
                    START_AND_TERMINAL -> {
                        // at the top-level we need to support dot as a special
                        delta(".", START_AND_TERMINAL, DOT) {
                            deltaDecimalInteger(TERMINAL, DECIMAL) {
                                deltaExponent {  }
                            }
                        }
                    }
                    else -> {
                        deltaDecimalFraction {
                            deltaExponent {  }
                        }
                    }
                }

            }
            
            deltaNumber(START_AND_TERMINAL)

            delta("+", START_AND_TERMINAL, OPERATOR) {
                deltaNumber(TERMINAL)
            }

            fun TableState.deltaQuote(quoteChar: String, tokenType: TokenType, lexType: LexType): Unit {
                delta(quoteChar, START, replacement = REPLACE_NOTHING) {
                    selfRepeatingDelegate(INCOMPLETE)
                    val quoteState = this
                    delta(quoteChar, TERMINAL, tokenType, lexType = lexType, replacement = REPLACE_NOTHING, delegate = initialState) {
                        delta(quoteChar, INCOMPLETE, delegate = quoteState)
                    }
                }
            }

            deltaQuote(SINGLE_QUOTE_CHARS, LITERAL, SQ_STRING)
            deltaQuote(DOUBLE_QUOTE_CHARS, IDENTIFIER, DQ_STRING)

            // Ion literals - very partial lexing of Ion to support nested back-tick
            // in Ion strings/symbols/comments
            delta(BACKTICK_CHARS, START, replacement = REPLACE_NOTHING) {
                selfRepeatingDelegate(INCOMPLETE)
                val quoteState = this

                delta("/", INCOMPLETE) {
                    delta("/", INCOMPLETE) {
                        val ionCommentState = this
                        selfRepeatingDelegate(INCOMPLETE)
                        delta(BACKTICK_CHARS, INCOMPLETE, delegate = ionCommentState)
                        delta(NL_WHITESPACE_CHARS, INCOMPLETE, delegate = quoteState)
                    }
                    delta("*", INCOMPLETE) {
                        val ionCommentState = this
                        selfRepeatingDelegate(INCOMPLETE)
                        delta(BACKTICK_CHARS, INCOMPLETE, delegate = ionCommentState)
                        delta("*", INCOMPLETE) {
                            delta("/", INCOMPLETE, delegate = quoteState)
                        }
                    }
                }
                delta(DOUBLE_QUOTE_CHARS, INCOMPLETE) {
                    val ionStringState = this
                    selfRepeatingDelegate(INCOMPLETE)

                    delta("\\", INCOMPLETE) {
                        delta(DOUBLE_QUOTE_CHARS, INCOMPLETE, delegate = ionStringState)
                    }
                    delta(BACKTICK_CHARS, INCOMPLETE, delegate = ionStringState)
                    delta(DOUBLE_QUOTE_CHARS, INCOMPLETE, delegate = quoteState)
                }
                delta(SINGLE_QUOTE_CHARS, INCOMPLETE) {
                    val ionStringState = this
                    selfRepeatingDelegate(INCOMPLETE)

                    delta("\\", INCOMPLETE) {
                        delta(SINGLE_QUOTE_CHARS, INCOMPLETE, delegate = ionStringState)
                    }
                    delta(BACKTICK_CHARS, INCOMPLETE, delegate = ionStringState)
                    delta(SINGLE_QUOTE_CHARS, INCOMPLETE, delegate = quoteState) {
                        delta(SINGLE_QUOTE_CHARS, INCOMPLETE, delegate = ionStringState) {
                            val ionLongStringState = this
                            selfRepeatingDelegate(INCOMPLETE)

                            delta("\\", INCOMPLETE) {
                                delta(SINGLE_QUOTE_CHARS, INCOMPLETE, delegate = ionLongStringState)
                            }
                            delta(BACKTICK_CHARS, INCOMPLETE, delegate = ionLongStringState)
                            delta(SINGLE_QUOTE_CHARS, INCOMPLETE, delegate = ionLongStringState) {
                                delta(SINGLE_QUOTE_CHARS, INCOMPLETE, delegate = ionLongStringState) {
                                    delta(SINGLE_QUOTE_CHARS, INCOMPLETE, delegate = quoteState)
                                }
                            }
                        }
                    }
                }

                delta(BACKTICK_CHARS, TERMINAL, LITERAL, ION_LITERAL, replacement = REPLACE_NOTHING, delegate = initialState)
            }

            delta(ALL_WHITESPACE_CHARS, START_AND_TERMINAL, null, WHITESPACE)

            // block comment and divide operator
            delta("/", START_AND_TERMINAL, OPERATOR) {
                delta("*", INCOMPLETE) {
                    selfRepeatingDelegate(INCOMPLETE)
                    delta("*", INCOMPLETE) {
                        delta("/", TERMINAL, null, WHITESPACE, delegate = initialState)
                    }
                }
            }
            // line comment, subtraction operator, and signed positive integer
            delta("-", START_AND_TERMINAL, OPERATOR) {
                delta("-", TERMINAL) {
                    selfRepeatingDelegate(INCOMPLETE)
                    delta(NL_WHITESPACE_CHARS, TERMINAL, delegate = initialState)
                }
                deltaNumber(TERMINAL)
            }

            // TODO datetime/hex/bin literals (not required for SQL-92 Entry compliance)
        }
    }

    private fun repr(codepoint: Int): String = when {
        codepoint == -1 -> "<EOF>"
        codepoint < -1 -> "<INVALID: ${codepoint}>"
        else -> "'${String(Character.toChars(codepoint))}' [U+${Integer.toHexString(codepoint)}]"
    }

    override fun tokenize(source: String): List<Token> {
        val codePoints = source.codePointSequence() + EOF

        val tokens = ArrayList<Token>()
        val tracker = PositionTracker()
        var currPos = tracker.position
        var curr: State = INITIAL_STATE
        val buffer = StringBuilder()

        for (cp in codePoints) {
            fun err(prefix: String = "Invalid character ${repr(cp)}"): Nothing {
                throw IllegalArgumentException("$prefix at $tracker")
            }

            tracker.advance(cp)

            // retrieve the next state
            val next = when(cp) {
                EOF -> EOF_STATE
                else -> curr[cp]
            }

            val currType = curr.stateType
            val nextType = next.stateType
            when {
                nextType == ERROR -> err()
                nextType.beginsToken -> {
                    // we can only start a token if we've properly ended another one.
                    if (currType != INITIAL && !currType.endsToken) {
                        err()
                    }
                    if (currType.endsToken && curr.lexType != WHITESPACE) {
                        // flush out the previous token
                        val text = buffer.toString()

                        var tokenType = curr.tokenType!!
                        val ionValue = when (tokenType) {
                            OPERATOR -> {
                                val unaliased = OPERATOR_ALIASES[text] ?: text
                                when (unaliased) {
                                    in ALL_OPERATORS -> ion.newSymbol(unaliased)
                                    else -> err("Unknown operator $unaliased")
                                }
                            }
                            IDENTIFIER -> {
                                val lower = text.toLowerCase()
                                when {
                                    curr.lexType == DQ_STRING -> ion.newSymbol(text)
                                    lower in ALL_OPERATORS -> {
                                        // an operator that looks like a keyword
                                        tokenType = OPERATOR
                                        ion.newSymbol(lower)
                                    }
                                    lower in BOOLEAN_KEYWORDS -> {
                                        // literal boolean
                                        tokenType = LITERAL
                                        ion.newBool(lower == "true")
                                    }
                                    lower in KEYWORDS -> {
                                        // unquoted identifier that is a keyword
                                        tokenType = KEYWORD
                                        ion.newSymbol(lower)
                                    }
                                    else -> ion.newSymbol(text)
                                }
                            }
                            LITERAL -> when (curr.lexType) {
                                SQ_STRING -> ion.newString(text)
                                INTEGER -> ion.newInt(BigInteger(text, 10))
                                DECIMAL -> ion.newDecimal(BigDecimal(text))
                                ION_LITERAL -> ion.singleValue(text)
                                else -> err("Invalid literal $text")
                            }
                            else -> ion.newSymbol(text)
                        }

                        tokens.add(Token(tokenType, ionValue, currPos))
                    }

                    // get ready for next token
                    buffer.setLength(0)
                    currPos = tracker.position
                }
            }
            val replacement = next.replacement
            if (cp != EOF && replacement != REPLACE_NOTHING) {
                buffer.appendCodePoint(when (replacement) {
                    REPLACE_SAME -> cp
                    else -> replacement
                })
            }

            curr = next
        }

        return tokens
    }
}

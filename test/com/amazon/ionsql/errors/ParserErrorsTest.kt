package com.amazon.ionsql.errors

import com.amazon.ionsql.syntax.*
import org.junit.*

class ParserErrorsTest : ErrorsBase() {

    private val parser = IonSqlParser(ion)

    private fun checkInputTrowingParserException(input: String,
                                                 errorCode: ErrorCode,
                                                 expectErrorContextValues: Map<Property, Any>) {
        try {
            parser.parse(input)
            fail("Expected ParserException but there was no Exception")
        } catch (pex: ParserException) {
            checkErrorAndErrorContext(errorCode, pex, expectErrorContextValues)
        } catch (ex: Exception) {
            fail("Expected ParserException but a different exception was thrown \n\t  $ex")
        }

    }

    @Test
    fun expectedKeyword() {
        checkInputTrowingParserException("5 BETWEEN 1  10",
            ErrorCode.PARSE_EXPECTED_KEYWORD,
            mapOf(
                Property.LINE_NUMBER to 1L,
                Property.COLUMN_NUMBER to 14L,
                Property.KEYWORD to "AND",
                Property.TOKEN_TYPE to TokenType.LITERAL,
                Property.TOKEN_VALUE to ion.newInt(10)))
    }

    @Test
    fun expectedTypeName() {
        checkInputTrowingParserException("NULL is `null`",
            ErrorCode.PARSE_EXPECTED_TYPE_NAME,
            mapOf(
                Property.LINE_NUMBER to 1L,
                Property.COLUMN_NUMBER to 9L,
                Property.TOKEN_TYPE to TokenType.LITERAL,
                Property.TOKEN_VALUE to ion.newNull()))

    }

    @Test
    fun expectedIdentAfterAT() {
        checkInputTrowingParserException("@",
            ErrorCode.PARSE_MISSING_IDENT_AFTER_AT,
            mapOf(
                Property.LINE_NUMBER to 1L,
                Property.COLUMN_NUMBER to 1L,
                Property.TOKEN_TYPE to TokenType.OPERATOR,
                Property.TOKEN_VALUE to ion.newSymbol("@")))

    }

    @Test
    fun expectedExpectedTypeName() {
        checkInputTrowingParserException("a is 'missing'",
            ErrorCode.PARSE_EXPECTED_TYPE_NAME,
            mapOf(
                Property.LINE_NUMBER to 1L,
                Property.COLUMN_NUMBER to 6L,
                Property.TOKEN_TYPE to TokenType.LITERAL,
                Property.TOKEN_VALUE to ion.newString("missing")))

    }

    @Test
    fun expectedUnexpectedToken() {
        checkInputTrowingParserException("SELECT ord, val FROM table1 AT ord AS val",
            ErrorCode.PARSE_UNEXPECTED_TOKEN,
            mapOf(
                Property.LINE_NUMBER to 1L,
                Property.COLUMN_NUMBER to 36L,
                Property.TOKEN_TYPE to TokenType.AS,
                Property.TOKEN_VALUE to ion.newSymbol("as")))

    }

    @Test
    fun expectedUnexpectedKeyword() {
        checkInputTrowingParserException("SELECT FROM table1",
            ErrorCode.PARSE_UNEXPECTED_KEYWORD,
            mapOf(
                Property.LINE_NUMBER to 1L,
                Property.COLUMN_NUMBER to 8L,
                Property.TOKEN_TYPE to TokenType.KEYWORD,
                Property.TOKEN_VALUE to ion.newSymbol("from")))

    }

    @Test
    fun expectedInvalidPathComponent() {
        checkInputTrowingParserException("x...a",
            ErrorCode.PARSE_INVALID_PATH_COMPONENT,
            mapOf(
                Property.LINE_NUMBER to 1L,
                Property.COLUMN_NUMBER to 1L,
                Property.TOKEN_TYPE to TokenType.IDENTIFIER,
                Property.TOKEN_VALUE to ion.newSymbol("x")))

    }

    @Test
    fun expectedCastArity() {
        checkInputTrowingParserException("CAST(5 AS INTEGER(10))",
            ErrorCode.PARSE_CAST_ARITY,
            mapOf(
                Property.LINE_NUMBER to 1L,
                Property.COLUMN_NUMBER to 18L,
                Property.TOKEN_TYPE to TokenType.LEFT_PAREN,
                Property.EXPECTED_ARITY_MIN to 0,
                Property.EXPECTED_ARITY_MAX to 0,
                Property.CAST_TO to "integer",
                Property.TOKEN_VALUE to ion.newSymbol("(")))

    }

    @Test
    fun expectedInvalidTypeParameter() {
        checkInputTrowingParserException("CAST(5 AS VARCHAR(a))",
            ErrorCode.PARSE_INVALID_TYPE_PARAM,
            mapOf(
                Property.LINE_NUMBER to 1L,
                Property.COLUMN_NUMBER to 11L,
                Property.TOKEN_TYPE to TokenType.KEYWORD,
                Property.TOKEN_VALUE to ion.newSymbol("character_varying")))

    }

    @Test
    fun expectedExpectedWhenClause() {
        checkInputTrowingParserException("CASE name ELSE 1 END",
            ErrorCode.PARSE_EXPECTED_WHEN_CLAUSE,
            mapOf(
                Property.LINE_NUMBER to 1L,
                Property.COLUMN_NUMBER to 11L,
                Property.TOKEN_TYPE to TokenType.KEYWORD,
                Property.TOKEN_VALUE to ion.newSymbol("else")))

    }

    @Test
    fun expectedUnexpectedOperator() {
        checkInputTrowingParserException("SELECT a, b FROM data WHERE LIKE a b",
            ErrorCode.PARSE_UNEXPECTED_OPERATOR,
            mapOf(
                Property.LINE_NUMBER to 1L,
                Property.COLUMN_NUMBER to 29L,
                Property.TOKEN_TYPE to TokenType.OPERATOR,
                Property.TOKEN_VALUE to ion.newSymbol("like")))

    }

    @Test
    fun expectedExpression() {
        checkInputTrowingParserException("SELECT a, b FROM data WHERE a LIKE b ESCAPE",
            ErrorCode.PARSE_EXPECTED_EXPRESSION,
            mapOf(
                Property.LINE_NUMBER to 1L,
                Property.COLUMN_NUMBER to 38L,
                Property.TOKEN_TYPE to TokenType.KEYWORD,
                Property.TOKEN_VALUE to ion.newSymbol("escape")))

    }

    @Test
    fun expectedExpressionTernaryOperator() {
        checkInputTrowingParserException("SELECT a, b FROM data WHERE a LIKE",
            ErrorCode.PARSE_EXPECTED_EXPRESSION,
            mapOf(
                Property.LINE_NUMBER to 1L,
                Property.COLUMN_NUMBER to 31L,
                Property.TOKEN_TYPE to TokenType.OPERATOR,
                Property.TOKEN_VALUE to ion.newSymbol("like")))

    }

    @Test
    fun expectedTokenType() {
        checkInputTrowingParserException("(1 + 2",
            ErrorCode.PARSE_EXPECTED_TOKEN_TYPE,
            mapOf(
                Property.EXPECTED_TOKEN_TYPE to TokenType.RIGHT_PAREN,
                Property.LINE_NUMBER to 1L,
                Property.COLUMN_NUMBER to 7L,
                Property.TOKEN_TYPE to TokenType.EOF,
                Property.TOKEN_VALUE to ion.newSymbol("EOF")))

    }

    @Test
    fun expectedCastMissingLeftParen() {
        checkInputTrowingParserException("CAST 5 as integer",
            ErrorCode.PARSE_EXPECTED_LEFT_PAREN_AFTER_CAST,
            mapOf(
                Property.LINE_NUMBER to 1L,
                Property.COLUMN_NUMBER to 6L,
                Property.TOKEN_TYPE to TokenType.LITERAL,
                Property.TOKEN_VALUE to ion.newInt(5)))

    }

    @Test
    fun expectedLeftParenValueConstructor() {
        checkInputTrowingParserException("values 1,2)",
            ErrorCode.PARSE_EXPECTED_LEFT_PAREN_VALUE_CONSTRUCTOR,
            mapOf(
                Property.LINE_NUMBER to 1L,
                Property.COLUMN_NUMBER to 8L,
                Property.TOKEN_TYPE to TokenType.LITERAL,
                Property.TOKEN_VALUE to ion.newInt(1)))

    }

    @Test
    fun expectedUnexpectedTerm() {
        checkInputTrowingParserException("select () from data",
            ErrorCode.PARSE_UNEXPECTED_TERM,
            mapOf(
                Property.LINE_NUMBER to 1L,
                Property.COLUMN_NUMBER to 9L,
                Property.TOKEN_TYPE to TokenType.RIGHT_PAREN,
                Property.TOKEN_VALUE to ion.newSymbol(")")))

    }

    @Test
    fun expectedSelectMissingFrom() {
        checkInputTrowingParserException("select a  data",
            ErrorCode.PARSE_SELECT_MISSING_FROM,
            mapOf(
                Property.LINE_NUMBER to 1L,
                Property.COLUMN_NUMBER to 15L,
                Property.TOKEN_TYPE to TokenType.EOF,
                Property.TOKEN_VALUE to ion.newSymbol("EOF")))

    }

    @Test
    fun expectedUnsupportedLiteralsGroupBy() {
        checkInputTrowingParserException("select a from data group by 1",
                                         ErrorCode.PARSE_UNSUPPORTED_LITERALS_GROUPBY,
                                         mapOf(Property.LINE_NUMBER to 1L,
                                               Property.COLUMN_NUMBER to 29L,
                                               Property.TOKEN_TYPE to TokenType.LITERAL,
                                               Property.TOKEN_VALUE to ion.newInt(1)))

    }

    @Test
    fun expectedIdentForAlias() {
        checkInputTrowingParserException("select a as true from data",
            ErrorCode.PARSE_EXPECTED_IDENT_FOR_ALIAS,
            mapOf(
                Property.LINE_NUMBER to 1L,
                Property.COLUMN_NUMBER to 13L,
                Property.TOKEN_TYPE to TokenType.LITERAL,
                Property.TOKEN_VALUE to ion.newBool(true)))

    }

    @Test
    fun expectedIdentForAt() {
        checkInputTrowingParserException("select a from data at true",
            ErrorCode.PARSE_EXPECTED_IDENT_FOR_AT,
            mapOf(
                Property.LINE_NUMBER to 1L,
                Property.COLUMN_NUMBER to 23L,
                Property.TOKEN_TYPE to TokenType.LITERAL,
                Property.TOKEN_VALUE to ion.newBool(true)))

    }

    @Test
    fun substringMissingLeftParen() {
                                        //12345678901234567890123456789
        checkInputTrowingParserException("select substring from 'asdf' for 1) FROM foo",
                ErrorCode.PARSE_EXPECTED_LEFT_PAREN_BUILTIN_FUNCTION_CALL,
                mapOf(
                    Property.LINE_NUMBER to 1L,
                    Property.COLUMN_NUMBER to 18L,
                    Property.TOKEN_TYPE to TokenType.KEYWORD,
                    Property.TOKEN_VALUE to ion.newSymbol("from")))
    }

    @Test
    fun substringMissingFromOrComma() {
                                        //12345678901234567890123456789
        checkInputTrowingParserException("select substring('str' 1) from foo",
                ErrorCode.PARSE_EXPECTED_ARGUMENT_DELIMITER,
                mapOf(
                    Property.LINE_NUMBER to 1L,
                    Property.COLUMN_NUMBER to 24L,
                    Property.TOKEN_TYPE to TokenType.LITERAL,
                    Property.TOKEN_VALUE to ion.newInt(1)))
    }

    @Test
    fun substringSql92WithoutLengthMissingRightParen() {
                                        //123456789012345678901234567890123456789
        checkInputTrowingParserException("select substring('str' from 1 from foo ",
                ErrorCode.PARSE_EXPECTED_2_TOKEN_TYPES,
                mapOf(
                    Property.LINE_NUMBER to 1L,
                    Property.EXPECTED_TOKEN_TYPE_1_OF_2 to TokenType.FOR,
                    Property.EXPECTED_TOKEN_TYPE_2_OF_2 to TokenType.RIGHT_PAREN,
                    Property.COLUMN_NUMBER to 31L,
                    Property.TOKEN_TYPE to TokenType.KEYWORD,
                    Property.TOKEN_VALUE to ion.newSymbol("from")))
    }

    @Test
    fun substringSql92WithLengthMissingRightParen() {
                                        //123456789012345678901234567890123456789
        checkInputTrowingParserException("select substring('str' from 1 for 1 from foo ",
                ErrorCode.PARSE_EXPECTED_TOKEN_TYPE,
                mapOf(
                    Property.LINE_NUMBER to 1L,
                    Property.COLUMN_NUMBER to 37L,
                    Property.TOKEN_TYPE to TokenType.KEYWORD,
                    Property.EXPECTED_TOKEN_TYPE to TokenType.RIGHT_PAREN,
                    Property.TOKEN_VALUE to ion.newSymbol("from")))
    }

    @Test
    fun substringWithoutLengthMissingRightParen() {
                                        //123456789012345678901234567890123456789
        checkInputTrowingParserException("select substring('str', 1 from foo ",
                ErrorCode.PARSE_EXPECTED_2_TOKEN_TYPES,
                mapOf(
                        Property.LINE_NUMBER to 1L,
                        Property.COLUMN_NUMBER to 27L,
                        Property.TOKEN_TYPE to TokenType.KEYWORD,
                        Property.EXPECTED_TOKEN_TYPE_1_OF_2 to TokenType.COMMA,
                        Property.EXPECTED_TOKEN_TYPE_2_OF_2 to TokenType.RIGHT_PAREN,
                        Property.TOKEN_VALUE to ion.newSymbol("from")))
    }

    @Test
    fun substringMissingRightParen() {
                                        //123456789012345678901234567890123456789
        checkInputTrowingParserException("select substring('str', 1, 1 from foo ",
                ErrorCode.PARSE_EXPECTED_TOKEN_TYPE,
                mapOf(
                        Property.LINE_NUMBER to 1L,
                        Property.COLUMN_NUMBER to 30L,
                        Property.TOKEN_TYPE to TokenType.KEYWORD,
                        Property.EXPECTED_TOKEN_TYPE to TokenType.RIGHT_PAREN,
                        Property.TOKEN_VALUE to ion.newSymbol("from")))

    }

    @Test
    fun callTrimNoLeftParen() {
        checkInputTrowingParserException("trim ' ')",
                                         ErrorCode.PARSE_EXPECTED_LEFT_PAREN_BUILTIN_FUNCTION_CALL,
                                         mapOf(
                                             Property.LINE_NUMBER to 1L,
                                             Property.COLUMN_NUMBER to 6L,
                                             Property.TOKEN_TYPE to TokenType.LITERAL,
                                             Property.TOKEN_VALUE to ion.newString(" ")))
    }

    @Test
    fun callTrimNoRightParen() {
        checkInputTrowingParserException("trim (' '",
                                         ErrorCode.PARSE_EXPECTED_RIGHT_PAREN_BUILTIN_FUNCTION_CALL,
                                         mapOf(
                                             Property.LINE_NUMBER to 1L,
                                             Property.COLUMN_NUMBER to 10L,
                                             Property.TOKEN_TYPE to TokenType.EOF,
                                             Property.TOKEN_VALUE to ion.newSymbol("EOF")))
    }

    @Test
    fun callTrimFourArguments() {
        checkInputTrowingParserException("trim(both ' ' from 'test' 2)",
                                         ErrorCode.PARSE_EXPECTED_RIGHT_PAREN_BUILTIN_FUNCTION_CALL,
                                         mapOf(
                                             Property.LINE_NUMBER to 1L,
                                             Property.COLUMN_NUMBER to 27L,
                                             Property.TOKEN_TYPE to TokenType.LITERAL,
                                             Property.TOKEN_VALUE to ion.newInt(2)))
    }

    @Test
    fun callTrimSpecificationWithoutFrom() {
        checkInputTrowingParserException("trim(both 'test')",
                                         ErrorCode.PARSE_EXPECTED_KEYWORD,
                                         mapOf(
                                             Property.LINE_NUMBER to 1L,
                                             Property.COLUMN_NUMBER to 17L,
                                             Property.KEYWORD to "FROM",
                                             Property.TOKEN_TYPE to TokenType.RIGHT_PAREN,
                                             Property.TOKEN_VALUE to ion.newSymbol(")")))
    }

    @Test
    fun callTrimSpecificationAndRemoveWithoutFrom() {
        checkInputTrowingParserException("trim(both '' 'test')",
                                         ErrorCode.PARSE_EXPECTED_KEYWORD,
                                         mapOf(
                                             Property.LINE_NUMBER to 1L,
                                             Property.COLUMN_NUMBER to 14L,
                                             Property.TOKEN_TYPE to TokenType.LITERAL,
                                             Property.KEYWORD to "FROM",
                                             Property.TOKEN_VALUE to ion.newString("test")))
    }

    @Test
    fun callTrimWithoutString() {
        checkInputTrowingParserException("trim(from)",
                                         ErrorCode.PARSE_UNEXPECTED_TERM,
                                         mapOf(
                                             Property.LINE_NUMBER to 1L,
                                             Property.COLUMN_NUMBER to 10L,
                                             Property.TOKEN_TYPE to TokenType.RIGHT_PAREN,
                                             Property.TOKEN_VALUE to ion.newSymbol(")")))
    }

}
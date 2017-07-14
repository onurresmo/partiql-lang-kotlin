/*
 * Copyright 2016 Amazon.com, Inc. or its affiliates.  All rights reserved.
 */


package com.amazon.ionsql.eval

import org.junit.Test


class LikePredicateTest : EvaluatorBase() {

    @Test
    fun noEscapeAllArgsLiteralsMatches() =
        assertEval(
            """SELECT * FROM animals as a WHERE 'Kumo' LIKE 'Kumo' """,
            """
          [
            {name: "Kumo", type: "dog"},
            {name:"Mochi",type:"dog"},
            {name:"Lilikoi",type:"unicorn"}
          ]
        """
        )

    @Test
    fun noEscapeAllArgsLiteralsMismatchCase() =
        assertEval(
            """SELECT * FROM animals as a WHERE 'Kumo' LIKE 'KuMo' """,
            """
          []
        """
        )


    @Test
    fun noEscapeAllArgsLiteralsMismatchPattern() =
        assertEval(
            """SELECT * FROM animals as a WHERE 'xxx' LIKE 'Kumo' """,
            """
          []
        """
        )

    @Test
    fun noEscapeAllArgsLiteralsMatchUnderscore() =
        assertEval(
            """SELECT * FROM animals as a WHERE 'Kumo' LIKE 'K_mo' """,
            """
          [
            {name: "Kumo", type: "dog"},
            {name:"Mochi",type:"dog"},
            {name:"Lilikoi",type:"unicorn"}
          ]
        """
        )

    @Test
    fun noEscapeAllArgsLiteralsNoMatchUnderscore() =
        assertEval(
            """SELECT * FROM animals as a WHERE 'Kuumo' LIKE 'K_mo' """,
            """
          []
        """
        )

    @Test
    fun noEscapeAllArgsLiteralsNoMatchUnderscoreExtraChar() =
        assertEval(
            """SELECT * FROM animals as a WHERE 'KKumo' LIKE 'K_mo' """,
            """
          []
        """
        )

    @Test
    fun noEscapeAllArgsLiteralsMatchConsecutiveUnderscores() =
        assertEval(
            """SELECT * FROM animals as a WHERE 'Kumo' LIKE 'K__o' """,
            """
          [
            {name: "Kumo", type: "dog"},
            {name:"Mochi",type:"dog"},
            {name:"Lilikoi",type:"unicorn"}
          ]
        """
        )

    @Test
    fun noEscapeAllArgsLiteralsMatch2UnderscoresNonConsecutive() =
        assertEval(
            """SELECT * FROM animals as a WHERE 'Kumo' LIKE '_u_o' """,
            """
          [
            {name: "Kumo", type: "dog"},
            {name:"Mochi",type:"dog"},
            {name:"Lilikoi",type:"unicorn"}
          ]
        """
        )

    @Test
    fun noEscapeAllArgsLiteralsMatchUnderscoresAtEnd() =
        assertEval(
            """SELECT * FROM animals as a WHERE 'Kumo' LIKE 'Kum_' """,
            """
          [
            {name: "Kumo", type: "dog"},
            {name:"Mochi",type:"dog"},
            {name:"Lilikoi",type:"unicorn"}
          ]
        """
        )

    @Test
    fun noEscapeAllArgsLiteralsMatchPercentage() =
        assertEval(
            """SELECT * FROM animals as a WHERE 'Kumo' LIKE 'Ku%o' """,
            """
          [
            {name: "Kumo", type: "dog"},
            {name:"Mochi",type:"dog"},
            {name:"Lilikoi",type:"unicorn"}
          ]
        """
        )

    @Test
    fun noEscapeAllArgsLiteralsNoMatchPercentageExtraCharBefore() =
        assertEval(
            """SELECT * FROM animals as a WHERE 'KKumo' LIKE 'Ku%o' """,
            """
          []
        """
        )

    @Test
    fun noEscapeAllArgsLiteralsNoMatchPercentageExtraCharAfter() =
        assertEval(
            """SELECT * FROM animals as a WHERE 'Kumol' LIKE 'Ku%o' """,
            """
          []
        """
        )


    @Test
    fun noEscapeAllArgsLiteralsMatch2PercentagesConsecutive() =
        assertEval(
            """SELECT * FROM animals as a WHERE 'Kumo' LIKE 'K%%o' """,
            """
          [
            {name: "Kumo", type: "dog"},
            {name:"Mochi",type:"dog"},
            {name:"Lilikoi",type:"unicorn"}
          ]
        """
        )

    @Test
    fun noEscapeAllArgsLiteralsMatch2PercentagesNonConsecutive() =
        assertEval(
            """SELECT * FROM animals as a WHERE 'Kumo' LIKE 'K%m%' """,
            """
          [
            {name: "Kumo", type: "dog"},
            {name:"Mochi",type:"dog"},
            {name:"Lilikoi",type:"unicorn"}
          ]
        """
        )

    @Test
    fun noEscapeAllArgsLiteralsMatchPercentageAsFirst() =
        assertEval(
            """SELECT * FROM animals as a WHERE 'Kumo' LIKE '%umo' """,
            """
          [
            {name: "Kumo", type: "dog"},
            {name:"Mochi",type:"dog"},
            {name:"Lilikoi",type:"unicorn"}
          ]
        """
        )

    @Test
    fun noEscapeAllArgsLiteralsMatchPercentageAsLast() =
        assertEval(
            """SELECT * FROM animals as a WHERE 'Kumo' LIKE 'Kum%' """,
            """
          [
            {name: "Kumo", type: "dog"},
            {name:"Mochi",type:"dog"},
            {name:"Lilikoi",type:"unicorn"}
          ]
        """
        )


    @Test
    fun noEscapeAllArgsLiteralsPercentageAndUnderscore() =
        assertEval(
            """SELECT * FROM animals as a WHERE 'Kumo' LIKE 'K_%mo' """,
            """
          [
            {name: "Kumo", type: "dog"},
            {name:"Mochi",type:"dog"},
            {name:"Lilikoi",type:"unicorn"}
          ]
        """
        )

    @Test
    fun noEscapeAllArgsLiteralsPercentageAndUnderscoreNonConsecutive() =
        assertEval(
            """SELECT * FROM animals as a WHERE 'Kumo' LIKE 'K_m%' """,
            """
          [
            {name: "Kumo", type: "dog"},
            {name:"Mochi",type:"dog"},
            {name:"Lilikoi",type:"unicorn"}
          ]
        """
        )

    @Test
    fun noEscapeAllArgsLiteralsAllUnderscores() =
        assertEval(
            """SELECT * FROM animals as a WHERE 'Kumo' LIKE '____' """,
            """
          [
            {name: "Kumo", type: "dog"},
            {name:"Mochi",type:"dog"},
            {name:"Lilikoi",type:"unicorn"}
          ]
        """
        )

    @Test
    fun noEscapeAllArgsLiteralsJustPercentage() =
        assertEval(
            """SELECT * FROM animals as a WHERE 'Kumo' LIKE '%' """,
            """
          [
            {name: "Kumo", type: "dog"},
            {name:"Mochi",type:"dog"},
            {name:"Lilikoi",type:"unicorn"}
          ]
        """
        )

    @Test
    fun noEscapeAllArgsLiteralsEmptyStringAndJustPercentage() =
        assertEval(
            """SELECT * FROM animals as a WHERE '' LIKE '%' """,
            """
          [
            {name: "Kumo", type: "dog"},
            {name:"Mochi",type:"dog"},
            {name:"Lilikoi",type:"unicorn"}
          ]
        """
        )

    @Test
    fun EscapePercentageAllArgsLiterals() =
        assertEval(
            """SELECT * FROM animals as a WHERE '%' LIKE '[%' ESCAPE '[' """,
            """
          [
            {name: "Kumo", type: "dog"},
            {name:"Mochi",type:"dog"},
            {name:"Lilikoi",type:"unicorn"}
          ]
        """
        )


    @Test
    fun EscapePercentageAllArgsLiteralsPatternWithMetaPercentage() =
        assertEval(
            """SELECT * FROM animals as a WHERE '100%' LIKE '1%[%' ESCAPE '[' """,
            """
          [
            {name:"Kumo", type: "dog"},
            {name:"Mochi",type:"dog"},
            {name:"Lilikoi",type:"unicorn"}
          ]
        """
        )

    @Test
    fun EscapePercentageWithBackSlashAllArgsLiteralsPatternWithMetaPercentage() =
        assertEval(
            """SELECT * FROM animals as a WHERE '100%' LIKE '1%\%' ESCAPE '\' """,
            """
          [
            {name: "Kumo", type: "dog"},
            {name:"Mochi",type:"dog"},
            {name:"Lilikoi",type:"unicorn"}
          ]
        """
        )

    @Test
    fun EscapePercentageAllArgsLiteralsPatternWithMetaUnderscore() =
        assertEval(
            """SELECT * FROM animals as a WHERE '100%' LIKE '1__[%' ESCAPE '[' """,
            """
          [
            {name: "Kumo", type: "dog"},
            {name:"Mochi",type:"dog"},
            {name:"Lilikoi",type:"unicorn"}
          ]
        """
        )

    @Test
    fun EscapePercentageAllArgsLiteralsPatternWithMetaPercentAtStart() =
        assertEval(
            """SELECT * FROM animals as a WHERE '%100' LIKE '[%%' ESCAPE '[' """,
            """
          [
            {name: "Kumo", type: "dog"},
            {name:"Mochi",type:"dog"},
            {name:"Lilikoi",type:"unicorn"}
          ]
        """
        )

    @Test
    fun EscapePercentageAllArgsLiteralsPatternWithMetaPercentAtStartFollowedByUnderscore() =
        assertEval(
            """SELECT * FROM animals as a WHERE '%100' LIKE '[%_00' ESCAPE '[' """,
            """
          [
            {name: "Kumo", type: "dog"},
            {name:"Mochi",type:"dog"},
            {name:"Lilikoi",type:"unicorn"}
          ]
        """
        )

    @Test
    fun EscapePercentageAllArgsLiteralsPatternWithMetaPercentAtStartFollowedByUnderscoreNoMatch() =
        assertEval(
            """SELECT * FROM animals as a WHERE '%1XX' LIKE '[%_00' ESCAPE '[' """,
            """
          []
        """
        )

    @Test
    fun MultipleEscapesNoMeta() =
        assertEval(
            """SELECT * FROM animals as a WHERE '1_000_000%' LIKE '1[_000[_000[%' ESCAPE '[' """,
            """
          [
            {name: "Kumo", type: "dog"},
            {name:"Mochi",type:"dog"},
            {name:"Lilikoi",type:"unicorn"}
          ]
        """
        )

    @Test
    fun MultipleEscapesWithMeta() =
        assertEval(
            """SELECT * FROM animals as a WHERE '1_000_000%' LIKE '1[____[_%[%' ESCAPE '[' """,
            """
          [
            {name: "Kumo", type: "dog"},
            {name:"Mochi",type:"dog"},
            {name:"Lilikoi",type:"unicorn"}
          ]
        """
        )

    @Test
    fun MultipleEscapesWithMetaAtStart() =
        assertEval(
            """SELECT * FROM animals as a WHERE '1_000_000%' LIKE '_[_%[_%[%' ESCAPE '[' """,
            """
          [
            {name: "Kumo", type: "dog"},
            {name:"Mochi",type:"dog"},
            {name:"Lilikoi",type:"unicorn"}
          ]
        """
        )

    @Test
    fun noEscapeValueIsBinding() =
        assertEval(
            """SELECT * FROM animals as a WHERE a.name LIKE 'Kumo' """,
            """
          [
            {name: "Kumo", type: "dog"}
          ]
        """
        )

    @Test
    fun noEscapeValueIsStringAppendExpression() =
        assertEval(
            """SELECT * FROM animals as a WHERE a.name || 'xx' LIKE '%xx' """,
            """
          [
            {name: "Kumo", type: "dog"},
            {name:"Mochi",type:"dog"},
            {name:"Lilikoi",type:"unicorn"}
          ]
        """
        )

    @Test
    fun noEscapeValueAndPatternAreBindings() =
        assertEval(
            """SELECT a.name FROM
                  `[
                   { name:"Abcd", pattern:"A___" },
                   { name:"100",  pattern:"1%0" }
                  ]` as a
               WHERE a.name LIKE a.pattern """,
            """
          [
             { name:"Abcd" },
             { name:"100"}
          ]
        """
        )

    @Test
    fun EscapeLiteralValueAndPatternAreBindings() =
        assertEval(
            """SELECT a.name FROM
                  `[
                   { name:"Abcd", pattern:"A___" },
                   { name:"100%",  pattern:"1%0\\%" }
                  ]` as a
               WHERE a.name LIKE a.pattern ESCAPE '\' """,
            """
          [
             { name:"Abcd" },
             { name:"100%"}
          ]
        """
        )

    @Test
    fun EscapeValueAndPatternAreBindings() =
        assertEval(
            """SELECT a.name FROM
                  `[
                   { name:"Abcd", pattern:"A___" , escapeChar:'['},
                   { name:"100%",  pattern:"1%0[%", escapeChar: '['}
                  ]` as a
               WHERE a.name LIKE a.pattern ESCAPE a.escapeChar """,
            """
          [
             { name:"Abcd" },
             { name:"100%"}
          ]
        """
        )

    @Test
    fun NotLikeEscapeValueAndPatternAreBindings() =
        assertEval(
            """SELECT a.name FROM
                  `[
                   { name:"Abcd", pattern:"A__" , escapeChar:'['},
                   { name:"1000%",  pattern:"1_0[%", escapeChar: '['}
                  ]` as a
               WHERE a.name NOT LIKE a.pattern ESCAPE a.escapeChar """,
            """
          [
             { name:"Abcd" },
             { name:"1000%"}
          ]
        """
        )
}
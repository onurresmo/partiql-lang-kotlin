/*
 * Copyright 2016 Amazon.com, Inc. or its affiliates.  All rights reserved.
 */

package com.amazon.ionsql

import org.junit.Test
import java.math.BigDecimal

class NumbersTest : Base() {
    fun dec(text: String): BigDecimal = BigDecimal(text)
    fun dec(value: Double): BigDecimal = BigDecimal.valueOf(value)

    fun assertCoerce(num1: Number, num2: Number, expected1: Number, expected2: Number) {
        val (actual1, actual2) = coerceNumbers(num1, num2)
        assertEquals(expected1, actual1)
        assertEquals(expected2, actual2)
    }

    @Test fun coerceLongLong() = assertCoerce(1L, 2L, 1L, 2L)
    @Test fun coerceLongDouble() = assertCoerce(1L, 2.0, 1.0, 2.0)
    @Test fun coerceLongBigDecimal() = assertCoerce(1L, dec("10"), dec("1"), dec("10"))

    @Test fun coerceDoubleDouble() = assertCoerce(1.0, 2.0, 1.0, 2.0)
    @Test fun coerceDoubleLong() = assertCoerce(1.0, 2L, 1.0, 2.0)
    @Test fun coerceDoubleBigDecimal() = assertCoerce(1.0, dec("10"), dec(1.0), dec("10"))

    @Test fun coerceBigDecimalBigDecimal() =
        assertCoerce(dec("100"), dec("200"), dec("100"), dec("200"))
    @Test fun coerceBigDecimalLong() = assertCoerce(dec("1.1"), 2L, dec("1.1"), dec("2"))
    @Test fun coerceBigDecimalDouble() = assertCoerce(dec("1.1"), 2.0, dec("1.1"), dec(2.0))

    @Test fun unaryMinusLong() = assertEquals(-1L,-(1L as Number))
    @Test fun unaryMinusDouble() = assertEquals(-1.0, -(1.0 as Number))
    @Test fun unaryMinusBigDecimal() =
        assertEquals(dec("-100.1"), -(dec("100.1") as Number))

    @Test fun plusLong() = assertEquals(4L, 1L as Number + 3L as Number)
    @Test fun plusDouble() = assertEquals(4.0, 1L as Number + 3.0 as Number)
    @Test fun plusBigDecimal() = assertEquals(dec("4.1"), 1L as Number + dec("3.1") as Number)

    @Test fun minusLong() = assertEquals(-2L, 1L as Number - 3L as Number)
    @Test fun minusDouble() = assertEquals(-2.0, 1L as Number - 3.0 as Number)
    @Test fun minusBigDecimal() = assertEquals(dec("-2.1"), 1L as Number - dec("3.1") as Number)

    @Test fun timesLong() = assertEquals(3L, 1L as Number * 3L as Number)
    @Test fun timesDouble() = assertEquals(3.0, 1L as Number * 3.0 as Number)
    @Test fun timesBigDecimal() = assertEquals(dec("3.1"), 1L as Number * dec("3.1") as Number)

    @Test fun divLong() = assertEquals(2L, 4L as Number / 2L as Number)
    @Test fun divDouble() = assertEquals(2.0, 4.0 as Number / 2L as Number)
    @Test fun divBigDecimal() = assertEquals(dec("2"), dec("4") as Number / 2L as Number)

    @Test fun modLong() = assertEquals(0L, 4L as Number % 2L as Number)
    @Test fun modDouble() = assertEquals(0.0, 4.0 as Number % 2L as Number)
    @Test fun modBigDecimal() = assertEquals(dec("0"), dec("4") as Number % 2L as Number)

    @Test fun cmpLongLess() = assertTrue((1L as Number).compareTo(2L) < 0)
    @Test fun cmpLongEquals() = assertEquals(0, (1L as Number).compareTo(1L))
    @Test fun cmpLongMore() = assertTrue((2L as Number).compareTo(1L) > 0)

    @Test fun cmpDoubleLess() = assertTrue((1L as Number).compareTo(2.0) < 0)
    @Test fun cmpDoubleEquals() = assertEquals(0, (1.0 as Number).compareTo(1L))
    @Test fun cmpDoubleMore() = assertTrue((2L as Number).compareTo(1.0) > 0)

    @Test fun cmpBigDecimalLess() = assertTrue((1L as Number).compareTo(dec("2")) < 0)
    @Test fun cmpBigDecimalEquals() = assertEquals(0, (dec("1") as Number).compareTo(1L))
    @Test fun cmpBigDecimalMore() = assertTrue((2L as Number).compareTo(dec("1")) > 0)
}
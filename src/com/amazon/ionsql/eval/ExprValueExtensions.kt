/*
 * Copyright 2017 Amazon.com, Inc. or its affiliates.  All rights reserved.
 */

package com.amazon.ionsql.eval

import com.amazon.ion.*
import com.amazon.ionsql.errors.*
import com.amazon.ionsql.eval.ExprValueType.*
import com.amazon.ionsql.syntax.*
import com.amazon.ionsql.util.*
import java.math.*

/**
 * Wraps the given [ExprValue] with a delegate that provides the [OrderedBindNames] facet.
 */
fun ExprValue.orderedNamesValue(names: List<String>): ExprValue =
    object : ExprValue by this, OrderedBindNames {
        override val orderedNames = names
        override fun <T : Any?> asFacet(type: Class<T>?): T? =
            downcast(type) ?: this@orderedNamesValue.asFacet(type)
        override fun toString(): String = stringify()
    }

val ExprValue.orderedNames: List<String>?
    get() = asFacet(OrderedBindNames::class.java)?.orderedNames

/** Wraps this [ExprValue] as a [Named] instance. */
fun ExprValue.asNamed(): Named = object : Named {
    override val name: ExprValue
        get() = this@asNamed
}

/** Binds the given name value as a [Named] facet delegate over this [ExprValue]. */
fun ExprValue.namedValue(nameValue: ExprValue): ExprValue = object : ExprValue by this, Named {
    override val name = nameValue
    override fun <T : Any?> asFacet(type: Class<T>?): T? =
        downcast(type) ?: this@namedValue.asFacet(type)
    override fun toString(): String = stringify()
}

/** Wraps this [ExprValue] in a delegate that always masks the [Named] facet. */
fun ExprValue.unnamedValue(): ExprValue = when (asFacet(Named::class.java)) {
    null -> this
    else -> object : ExprValue by this {
        override fun <T : Any?> asFacet(type: Class<T>?): T? =
            when (type) {
                // always mask the name facet
                Named::class.java -> null
                else -> this@unnamedValue.asFacet(type)
            }
        override fun toString(): String = stringify()
    }
}

val ExprValue.name: ExprValue?
    get() = asFacet(Named::class.java)?.name

/** A special wrapper for `UNPIVOT` values as a BAG. */
private class UnpivotedExprValue(private val values: Iterable<ExprValue>) : BaseExprValue() {
    override val type = BAG
    override fun iterator() = values.iterator()

    // XXX this value is only ever produced in a FROM iteration, thus none of these should ever be called
    override val ionValue
        get() = throw UnsupportedOperationException("Synthetic value cannot provide ion value")
}

/** Unpivots a `struct`, and synthesizes a synthetic singleton `struct` for other [ExprValue]. */
internal fun ExprValue.unpivot(ion: IonSystem): ExprValue = when {
    // special case for our special UNPIVOT value to avoid double wrapping
    this is UnpivotedExprValue -> this
    // Wrap into a pseudo-BAG
    type == STRUCT -> UnpivotedExprValue(this)
    // for non-struct, this wraps any value into a BAG with a synthetic name
    else -> UnpivotedExprValue(
        listOf(
            this.namedValue(ion.newString(syntheticColumnName(0)).exprValue())
        )
    )
}

fun ExprValue.booleanValue(): Boolean =
    scalar.booleanValue() ?: errNoContext("Expected non-null boolean: $ionValue", internal = false)

fun ExprValue.numberValue(): Number =
    scalar.numberValue() ?: errNoContext("Expected non-null number: $ionValue", internal = false)

fun ExprValue.timestampValue(): Timestamp =
    scalar.timestampValue() ?: errNoContext("Expected non-null timestamp: $ionValue", internal = false)

fun ExprValue.stringValue(): String =
    scalar.stringValue() ?: errNoContext("Expected non-null string: $ionValue", internal = false)

fun ExprValue.bytesValue(): ByteArray =
    scalar.bytesValue() ?: errNoContext("Expected non-null LOB: $ionValue", internal = false)

internal fun ExprValue.datePartValue(): DatePart =
    try {
        DatePart.valueOf(this.stringValue().toUpperCase())
    }
    catch (e : IllegalArgumentException)  {
        throw EvaluationException(cause = e,
                                  message = "invalid date part, valid values: [${DATE_PART_KEYWORDS.joinToString()}]",
                                  internal = false)
    }

internal fun ExprValue.intValue(): Int = this.numberValue().toInt()

/**
 * Implements the `FROM` range operation.
 * Specifically, this is distinct from the normal [ExprValue.iterator] in that
 * types that are **not** [ExprValueType.isRangeFrom] get treated as a singleton
 * as per SQL++ specification.
 */
fun ExprValue.rangeOver(): Iterable<ExprValue> = when {
    type.isRangedFrom -> this
    // everything else ranges as a singleton unnamed value
    else -> listOf(this.unnamedValue())
}


/** A very simple string representation--to be used for diagnostic purposes only. */
fun ExprValue.stringify(): String = when (type) {
    MISSING -> "MISSING"
    BAG -> StringBuilder().apply {
        append("<<")
        this@stringify.forEachIndexed { i, e ->
            if (i > 0) {
                append(",")
            }
            append(e)
        }
        append(">>")
    }.toString()
    else -> ionValue.toString()
}

val DEFAULT_COMPARATOR = NaturalExprValueComparators.NULLS_FIRST

/** Provides SQL's equality function. */
fun ExprValue.exprEquals(other: ExprValue): Boolean = DEFAULT_COMPARATOR.compare(this, other) == 0

/**
 * Provides the comparison predicate--which is not a total ordering.
 *
 * In particular, this operation will fail for non-comparable types.
 * For a total ordering over the IonSQL++ type space, see [NaturalExprValueComparators]
 */
operator fun ExprValue.compareTo(other: ExprValue): Int {
    return when {
        type.isNull || other.type.isNull ->
            throw EvaluationException("Null value cannot be compared: $this, $other", internal = false)
        type.isDirectlyComparableTo(other.type) -> DEFAULT_COMPARATOR.compare(this, other)
        else -> errNoContext("Cannot compare values: $this, $other", internal = false)
    }
}

/** Types that are cast to the [ExprValueType.isText] types by calling `IonValue.toString()`. */
private val ION_TEXT_STRING_CAST_TYPES = setOf(BOOL, TIMESTAMP)

/**
 * Casts this [ExprValue] to the target type.
 *
 * `MISSING` and `NULL` always convert to themselves no matter the target type.  When the
 * source type and target type are the same, this operation is a no-op.
 *
 * The conversion *to* a particular type is as follows, any conversion not specified raises
 * an [EvaluationException]:
 *
 *  * `BOOL`
 *      * Number types will convert to `false` if numerically equal to zero, `true` otherwise.
 *      * Text types will convert to `true` if case-insensitive compared to the text `"true"`,
 *    `false` otherwise.
 *  * `INT`, `FLOAT`, and `DECIMAL`
 *      * `BOOL` converts as `1` for `true` and `0` for `false`
 *      * Number types will narrow or widen from the source type.  Narrowing is a truncation
 *      * Text types will convert using base-10 integral notation
 *          * For `FLOAT` and `DECIMAL` targets, decimal and e-notation is also supported.
 *  * `TIMESTAMP`
 *      * Text types will convert using the Ion text notation for timestamp (W3C/ISO-8601).
 *  * `STRING` and `SYMBOL`
 *      * `BOOL` converts to `STRING` as `"true"` and `"false"`;
 *        converts to `SYMBOL` as `'true'` and `'false'`.
 *      * Number types convert to decimal form with optional e-notation.
 *      * `TIMESTAMP` converts to the ISO-8601 format.
 *  * `BLOB` and `CLOB` can only convert between each other directly.
 *  * `LIST` and `SEXP`
 *      * Convert directly between each other.
 *      * `BAG` converts with an *arbitrary* order.
 *  * `STRUCT` only supports casting from itself.
 *  * `BAG` converts from `LIST` and `SEXP` by drops order guarantees.
 *
 * Note that *text types* is defined by [ExprValueType.isText], *number types* is defined by
 * [ExprValueType.isNumber], and *LOB types* is defined by [ExprValueType.isLob]
 *
 * @param ion The ion system to synthesize values with.
 * @param targetType The target type to cast this value to.
 */
fun ExprValue.cast(ion: IonSystem, targetType: ExprValueType, metadata: NodeMetadata?): ExprValue {
    fun castExceptionContext(): PropertyValueMap {
        val errorContext = PropertyValueMap().also {
            it[Property.CAST_FROM] = this.type.toString()
            it[Property.CAST_TO] = targetType.toString()
        }

        metadata?.fillErrorContext(errorContext)

        return errorContext
    }

    fun castFailedErr(message: String, internal: Boolean = false, cause: Throwable? = null): Nothing {
        val errorContext = castExceptionContext()

        val errorCode = if (metadata == null) {
            ErrorCode.EVALUATOR_CAST_FAILED_NO_LOCATION
        }
        else {
            ErrorCode.EVALUATOR_CAST_FAILED
        }

        throw EvaluationException(message = message,
                                  errorCode = errorCode,
                                  errorContext = errorContext,
                                  internal = internal,
                                  cause = cause)
    }

    fun Number.exprValue() = ionValue(ion).seal().exprValue()

    fun String.exprValue(type: ExprValueType) = when (type) {
        STRING -> ion.newString(this)
        SYMBOL -> ion.newSymbol(this)

        else -> castFailedErr("Invalid type for textual conversion: $type (this code should be unreachable)", internal = true)

    }.seal().exprValue()

    when {
        type.isNull || type == targetType -> return this
        else -> {
            when (targetType) {
                BOOL -> when {
                    type.isNumber -> return when {
                        numberValue().compareTo(0L) == 0 -> ion.newBool(false).seal().exprValue()
                        else -> ion.newBool(true).seal().exprValue()
                    }
                    type.isText -> return when (stringValue().toLowerCase()) {
                        "true" -> ion.newBool(true).seal().exprValue()
                        else -> ion.newBool(false).seal().exprValue()
                    }
                }
                INT -> when {
                    type == BOOL -> return if (booleanValue()) 1L.exprValue() else 0L.exprValue()
                    type.isNumber -> return numberValue().toLongFailingOverflow(metadata).exprValue()
                    type.isText -> {
                        val value: IonInt

                        try {
                            val normalized = stringValue().normalizeForCastToInt()
                            value = ion.singleValue(normalized) as IonInt // Note: Can throw on invalid ION
                        } catch (e : Exception) {
                            castFailedErr("can't convert string value to INT", internal = false, cause = e)
                        }

                        return when (value.integerSize) {
                            IntegerSize.BIG_INTEGER -> errIntOverflow(metadata?.toErrorContext())
                            else -> value.longValue().exprValue()
                        }
                    }
                }
                FLOAT -> when {
                    type == BOOL -> return if (booleanValue()) 1.0.exprValue() else 0.0.exprValue()
                    type.isNumber -> return numberValue().toDouble().exprValue()
                    type.isText ->
                        try {
                            return stringValue().toDouble().exprValue()
                        } catch(e: NumberFormatException) {
                            castFailedErr("can't convert string value to FLOAT", internal = false, cause = e)
                        }
                }
                DECIMAL -> when {
                    type == BOOL -> return if (booleanValue()) BigDecimal.ONE.exprValue() else BigDecimal.ZERO.exprValue()
                    type.isNumber -> return numberValue().coerce(BigDecimal::class.java).exprValue()
                    type.isText -> try {
                        return bigDecimalOf(stringValue()).exprValue()
                    }
                    catch (e: NumberFormatException)
                    {
                        castFailedErr("can't convert string value to DECIMAL", internal = false, cause = e)
                    }
                }
                TIMESTAMP -> when {
                    type.isText -> try {
                        return ion.newTimestamp(Timestamp.valueOf(stringValue())).seal().exprValue()
                    }
                    catch (e: IllegalArgumentException)
                    {
                        castFailedErr("can't convert string value to TIMESTAMP", internal = false, cause = e)
                    }
                }
                STRING, SYMBOL -> when {
                    type.isNumber -> return numberValue().toString().exprValue(targetType)
                    type.isText -> return stringValue().exprValue(targetType)
                    type in ION_TEXT_STRING_CAST_TYPES -> return ionValue.toString().exprValue(targetType)
                }
                CLOB -> when {
                    type.isLob -> return ion.newClob(bytesValue()).seal().exprValue()
                }
                BLOB -> when {
                    type.isLob -> return ion.newBlob(bytesValue()).seal().exprValue()
                }
                LIST, SEXP, BAG -> when {
                    type.isSequence -> return SequenceExprValue(ion, targetType, asSequence())
                }
                // no support for anything else
                else -> {}
            }
        }
    }

    val errorCode = if (metadata == null) {
        ErrorCode.EVALUATOR_INVALID_CAST_NO_LOCATION
    }
    else {
        ErrorCode.EVALUATOR_INVALID_CAST
    }

    // incompatible types
    err("Cannot convert $type to $targetType", errorCode, castExceptionContext(), internal = false)
}

/**
 * Remove leading spaces in decimal notation and the plus sign
 *
 * Examples:
 * - `"00001".normalizeForIntCast() == "1"`
 * - `"-00001".normalizeForIntCast() == "-1"`
 * - `"0x00001".normalizeForIntCast() == "0x00001"`
 * - `"+0x00001".normalizeForIntCast() == "0x00001"`
 * - `"000a".normalizeForIntCast() == "a"`
 */
private fun String.normalizeForCastToInt(): String {
    fun Char.isSign() = this == '-' || this == '+'
    fun Char.isHexOrBase2Marker(): Boolean {
        val c = this.toLowerCase()

        return c == 'x' || c == 'b'
    }

    fun String.possiblyHexOrBase2() = (length >= 2 && this[1].isHexOrBase2Marker()) ||
                                      (length >= 3 && this[0].isSign() && this[2].isHexOrBase2Marker())

    return when {
        length == 0          -> this
        possiblyHexOrBase2() -> {
            if (this[0] == '+') {
                this.drop(1)
            }
            else {
                this
            }
        }
        else                 -> {
            val (isNegative, startIndex) = when (this[0]) {
                '-'  -> Pair(true, 1)
                '+'  -> Pair(false, 1)
                else -> Pair(false, 0)
            }

            var toDrop = startIndex
            while (toDrop < length && this[toDrop] == '0') {
                toDrop += 1
            }

            when {
                toDrop == length          -> "0"  // string is all zeros
                toDrop == 0               -> this
                toDrop == 1 && isNegative -> this
                toDrop > 1 && isNegative  -> '-' + this.drop(toDrop)
                else                      -> this.drop(toDrop)
            }
        }
    }
}


private fun Number.toLongFailingOverflow(metadata: NodeMetadata?): Long {
    if(Long.MIN_VALUE > this || Long.MAX_VALUE < this) {
        errIntOverflow(metadata?.toErrorContext())
    }

    return when {
        // BigDecimal.toLong inflates the internal BigInteger to the scale before converting it to a long.
        // For example to convert 1e-6000 it needs to create a BigInteger with value equal to
        // `unscaledNumber^(10^abs(scale))` to them drop it and return 0L. The BigInteger creation is very
        // expensive and completely wasted
        //
        // The division to integral skips all that, see benchmarks in
        // https://code.amazon.com/packages/IonSqlBenchmarks/commits/257d4842fbd4b8f1481467fb99aa48029ba03a77
        this is BigDecimal -> this.divideToIntegralValue(BigDecimal.ONE).toLong()
        else                                     -> this.toLong()
    }
}



/**
 * An Unknown value is one of `MISSING` or `NULL`
 */
internal fun ExprValue.isUnknown() : Boolean = this.type.isNull
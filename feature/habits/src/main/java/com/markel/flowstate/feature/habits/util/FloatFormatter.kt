package com.markel.flowstate.feature.habits.util

import java.math.RoundingMode
import java.text.NumberFormat
import java.text.ParsePosition
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

/**
 * Formats a Float value for display in the UI.
 *
 * - Whole numbers (or values very close to a whole number due to floating-point
 *   precision artifacts) are shown without decimals: `2.0f` → `"2"`
 * - True decimal values are shown with up to 2 decimal places, trimming
 *   unnecessary trailing zeros: `2.50f` → `"2.5"`, `1.33f` → `"1.33"`
 *
 * This function exists because Kotlin's `Float.toString()` can produce
 * representations like `"1.0"` or `"2.0000001"`, and the naive pattern
 * `if (value % 1 == 0f) value.toInt().toString() else value.toString()`
 * fails when floating-point arithmetic leaves the value just above or below
 * an integer (e.g. `1.9999998f` or `2.0000002f`).
 */
fun formatFloat(value: Float): String {
    val rounded = round(value)
    return if (abs(value - rounded) < 0.001f) {
        // The value is effectively a whole number
        rounded.toInt().toString()
    } else {
        // True decimal – format to 2 dp using the active app locale.
        NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            maximumFractionDigits = 2
            minimumFractionDigits = 0
            isGroupingUsed = false
            roundingMode = RoundingMode.HALF_UP
        }.format(value.toDouble())
    }
}

/** Parses an editable decimal using either the active locale or a decimal point. */
fun parseFloat(text: String): Float? {
    val value = text.trim()
    if (value.isEmpty()) return null

    value.toFloatOrNull()?.let { parsed ->
        if (parsed.isFinite()) return parsed
    }

    val position = ParsePosition(0)
    val parsed = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        isGroupingUsed = false
    }.parse(value, position) ?: return null

    if (position.index != value.length) return null
    return parsed.toFloat().takeIf { it.isFinite() }
}

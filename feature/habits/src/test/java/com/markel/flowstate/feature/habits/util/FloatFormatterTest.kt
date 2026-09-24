package com.markel.flowstate.feature.habits.util

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class FloatFormatterTest {

    @Test
    fun formatFloat_usesCurrentLocaleDecimalSeparator() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("es-ES"))
            assertEquals("1,5", formatFloat(1.5f))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun parseFloat_roundTripsLocalizedEditableValue() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("es-ES"))
            assertEquals(1.5f, parseFloat(formatFloat(1.5f)))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun parseFloat_acceptsDecimalPointInCommaLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("es-ES"))
            assertEquals(1.5f, parseFloat("1.5"))
        } finally {
            Locale.setDefault(original)
        }
    }
}

package com.marrakechguide.feature.quote

import org.junit.Assert.assertEquals
import org.junit.Test

class QuoteActionModifierImpactFormatterTest {

    @Test
    fun `null factors produce neutral impact`() {
        assertEquals("0%", QuoteActionModifierImpactFormatter.format(null, null))
    }

    @Test
    fun `positive factors produce signed ascending range`() {
        assertEquals("+10% to +30%", QuoteActionModifierImpactFormatter.format(1.10, 1.30))
    }

    @Test
    fun `mixed factors produce negative to positive range`() {
        assertEquals("-10% to +10%", QuoteActionModifierImpactFormatter.format(0.90, 1.10))
    }

    @Test
    fun `equal factors produce single value`() {
        assertEquals("+25%", QuoteActionModifierImpactFormatter.format(1.25, 1.25))
    }

    @Test
    fun `reversed bounds are normalized`() {
        assertEquals("+10% to +30%", QuoteActionModifierImpactFormatter.format(1.30, 1.10))
    }

    @Test
    fun `non finite factors are treated as neutral`() {
        assertEquals("0%", QuoteActionModifierImpactFormatter.format(Double.NaN, Double.POSITIVE_INFINITY))
    }

    @Test
    fun `non finite bound is ignored while finite bound is preserved`() {
        assertEquals("0% to +10%", QuoteActionModifierImpactFormatter.format(1.10, Double.NaN))
    }

    @Test
    fun `very large finite factors are clamped instead of overflowing`() {
        assertEquals("+2147483647%", QuoteActionModifierImpactFormatter.format(Double.MAX_VALUE, Double.MAX_VALUE))
    }

    @Test
    fun `very small finite factors are clamped instead of overflowing`() {
        assertEquals("-2147483648%", QuoteActionModifierImpactFormatter.format(-Double.MAX_VALUE, -Double.MAX_VALUE))
    }
}

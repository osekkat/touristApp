package com.marrakechguide.core.engine

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class HoursEngineTest {

    private val vectors: JSONArray by lazy {
        val path = findVectorsPath()
        val json = JSONObject(File(path).readText())
        json.getJSONArray("cases")
    }

    @Test
    fun `open status matches shared vectors`() {
        for (i in 0 until vectors.length()) {
            val testCase = vectors.getJSONObject(i)
            val name = testCase.getString("name")
            val at = Instant.parse(testCase.getString("at"))
            val weekly = jsonArrayToStrings(testCase.optJSONArray("weekly"))
            val hoursText = testCase.optStringOrNull("hours_text")
            val exceptions = parseExceptions(testCase.optJSONArray("exceptions"))
            val expected = testCase.getJSONObject("expected")

            val status = HoursEngine.isOpen(
                weekly = weekly,
                hoursText = hoursText,
                at = at,
                exceptions = exceptions
            )

            assertStatus(name, status, expected)
        }
    }

    @Test
    fun `next change matches shared vectors`() {
        for (i in 0 until vectors.length()) {
            val testCase = vectors.getJSONObject(i)
            val name = testCase.getString("name")
            val at = Instant.parse(testCase.getString("at"))
            val weekly = jsonArrayToStrings(testCase.optJSONArray("weekly"))
            val hoursText = testCase.optStringOrNull("hours_text")
            val exceptions = parseExceptions(testCase.optJSONArray("exceptions"))
            val expected = testCase.getJSONObject("expected")

            val change = HoursEngine.getNextChange(
                weekly = weekly,
                hoursText = hoursText,
                from = at,
                exceptions = exceptions
            )

            val expectedNext = expected.optStringOrNull("next_change_local")
            if (expectedNext != null) {
                assertNotNull("Expected next change for $name", change)
                assertEquals("Next change mismatch for $name", expectedNext, formatLocal(change!!.time))
            } else {
                assertNull("Expected no next change for $name", change)
            }
        }
    }

    @Test
    fun `display formatting matches shared vectors`() {
        for (i in 0 until vectors.length()) {
            val testCase = vectors.getJSONObject(i)
            val name = testCase.getString("name")
            val at = Instant.parse(testCase.getString("at"))
            val weekly = jsonArrayToStrings(testCase.optJSONArray("weekly"))
            val hoursText = testCase.optStringOrNull("hours_text")
            val hoursVerifiedAt = testCase.optStringOrNull("hours_verified_at")
            val exceptions = parseExceptions(testCase.optJSONArray("exceptions"))
            val expected = testCase.getJSONObject("expected")
            val expectedDisplay = expected.getString("display")

            val display = HoursEngine.formatHoursForDisplay(
                weekly = weekly,
                hoursText = hoursText,
                hoursVerifiedAt = hoursVerifiedAt,
                at = at,
                exceptions = exceptions
            )

            assertEquals("Display mismatch for $name", expectedDisplay, display)
        }
    }

    @Test
    fun `hours staleness logic`() {
        val reference = Instant.parse("2026-01-10T11:00:00Z")

        assertTrue(HoursEngine.isHoursStale("2025-01-01", at = reference))
        assertFalse(HoursEngine.isHoursStale("2025-10-15", at = reference))
        assertFalse(HoursEngine.isHoursStale(null, at = reference))
    }

    private fun assertStatus(name: String, status: HoursEngine.OpenStatus, expected: JSONObject) {
        val expectedState = expected.getString("state")
        val expectedNext = expected.optStringOrNull("next_change_local")

        when (status) {
            is HoursEngine.OpenStatus.Open -> {
                assertEquals("State mismatch for $name", "open", expectedState)
                assertEquals("Close time mismatch for $name", expectedNext, formatLocal(status.closesAt))
            }

            is HoursEngine.OpenStatus.Closed -> {
                assertEquals("State mismatch for $name", "closed", expectedState)
                if (status.opensAt != null) {
                    assertEquals("Open time mismatch for $name", expectedNext, formatLocal(status.opensAt))
                } else {
                    assertNull("Expected nil next change for $name", expectedNext)
                }
            }

            HoursEngine.OpenStatus.Unknown -> {
                assertEquals("State mismatch for $name", "unknown", expectedState)
                assertNull("Unknown state should not have next change for $name", expectedNext)
            }
        }
    }

    private fun parseExceptions(raw: JSONArray?): List<HoursEngine.ExceptionRule> {
        if (raw == null) return emptyList()

        val items = mutableListOf<HoursEngine.ExceptionRule>()
        for (i in 0 until raw.length()) {
            val item = raw.getJSONObject(i)
            items += HoursEngine.ExceptionRule(
                date = item.optStringOrNull("date"),
                period = item.optStringOrNull("period"),
                open = item.optStringOrNull("open"),
                close = item.optStringOrNull("close"),
                closed = item.optBoolean("closed", false)
            )
        }
        return items
    }

    private fun jsonArrayToStrings(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { idx -> array.getString(idx) }
    }

    private fun formatLocal(instant: Instant): String {
        return LOCAL_OUTPUT_FORMATTER.format(instant.atZone(CASABLANCA_ZONE))
    }

    private fun findVectorsPath(): String {
        val userDir = System.getProperty("user.dir").orEmpty()
        val candidates = mutableListOf(
            "../../../shared/tests/hours-engine-vectors.json",
            "../../shared/tests/hours-engine-vectors.json",
            "../shared/tests/hours-engine-vectors.json",
            "shared/tests/hours-engine-vectors.json"
        )

        if (userDir.isNotEmpty()) {
            candidates.addAll(
                listOf(
                    "$userDir/shared/tests/hours-engine-vectors.json",
                    "$userDir/../shared/tests/hours-engine-vectors.json",
                    "$userDir/../../shared/tests/hours-engine-vectors.json",
                    "$userDir/../../../shared/tests/hours-engine-vectors.json"
                )
            )
        }

        for (candidate in candidates) {
            val file = File(candidate)
            if (file.exists()) {
                return file.absolutePath
            }
        }

        val env = System.getenv("HOURS_ENGINE_VECTORS_PATH")
        if (env != null && File(env).exists()) {
            return env
        }

        throw IllegalStateException(
            "Could not find hours-engine-vectors.json. Current dir: ${System.getProperty("user.dir")}"
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return getString(key)
    }

    companion object {
        private val CASABLANCA_ZONE: ZoneId = ZoneId.of("Africa/Casablanca")
        private val LOCAL_OUTPUT_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm", Locale.US)
    }
}

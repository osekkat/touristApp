package com.marrakechguide.core.engine

import com.marrakechguide.core.model.Place
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Deterministic opening-hours evaluator used by Explore and Place detail surfaces.
 *
 * Rules are evaluated in Morocco local time (`Africa/Casablanca`) with this precedence:
 * 1. Date-specific exception
 * 2. Period exception (currently Ramadan)
 * 3. Weekly schedule
 */
object HoursEngine {

    data class ExceptionRule(
        val date: String? = null,
        val period: String? = null,
        val open: String? = null,
        val close: String? = null,
        val closed: Boolean = false
    )

    sealed class OpenStatus {
        data class Open(val closesAt: Instant) : OpenStatus()
        data class Closed(val opensAt: Instant?) : OpenStatus()
        data object Unknown : OpenStatus()
    }

    enum class ChangeType {
        OPENS,
        CLOSES
    }

    data class HoursChange(
        val time: Instant,
        val type: ChangeType
    )

    // MARK: Public API (Place)

    fun isOpen(
        place: Place,
        at: Instant = Instant.now(),
        exceptions: List<ExceptionRule> = emptyList()
    ): OpenStatus {
        return isOpen(
            weekly = place.hoursWeekly,
            hoursText = place.hoursText,
            at = at,
            exceptions = exceptions
        )
    }

    fun getNextChange(
        place: Place,
        from: Instant = Instant.now(),
        exceptions: List<ExceptionRule> = emptyList()
    ): HoursChange? {
        return getNextChange(
            weekly = place.hoursWeekly,
            hoursText = place.hoursText,
            from = from,
            exceptions = exceptions
        )
    }

    fun formatHoursForDisplay(
        place: Place,
        at: Instant = Instant.now(),
        exceptions: List<ExceptionRule> = emptyList()
    ): String {
        return formatHoursForDisplay(
            weekly = place.hoursWeekly,
            hoursText = place.hoursText,
            hoursVerifiedAt = place.hoursVerifiedAt,
            at = at,
            exceptions = exceptions
        )
    }

    // MARK: Public API (Raw Schedule)

    fun isOpen(
        weekly: List<String>,
        hoursText: String?,
        at: Instant = Instant.now(),
        exceptions: List<ExceptionRule> = emptyList()
    ): OpenStatus {
        return evaluate(weekly, hoursText, at, exceptions).status
    }

    fun getNextChange(
        weekly: List<String>,
        hoursText: String?,
        from: Instant = Instant.now(),
        exceptions: List<ExceptionRule> = emptyList()
    ): HoursChange? {
        return evaluate(weekly, hoursText, from, exceptions).nextChange
    }

    fun formatHoursForDisplay(
        weekly: List<String>,
        hoursText: String?,
        hoursVerifiedAt: String?,
        at: Instant = Instant.now(),
        exceptions: List<ExceptionRule> = emptyList()
    ): String {
        val status = isOpen(weekly, hoursText, at, exceptions)

        val output = when (status) {
            is OpenStatus.Open -> "Open now · Closes ${formatTime(status.closesAt)}"
            is OpenStatus.Closed -> {
                val opensAt = status.opensAt
                if (opensAt == null) {
                    "Closed · Opening time unavailable"
                } else {
                    when {
                        isSameLocalDay(opensAt, at) -> "Closed · Opens today ${formatTime(opensAt)}"
                        isTomorrowLocalDay(opensAt, at) -> "Closed · Opens tomorrow ${formatTime(opensAt)}"
                        else -> "Closed · Opens ${formatDayTime(opensAt)}"
                    }
                }
            }
            OpenStatus.Unknown -> sanitizedHoursText(hoursText) ?: "Check hours locally"
        }

        return if (isHoursStale(hoursVerifiedAt, at)) {
            "$output · Hours may be outdated"
        } else {
            output
        }
    }

    fun isHoursStale(verifiedAt: String?, at: Instant = Instant.now()): Boolean {
        if (verifiedAt.isNullOrBlank()) return false
        val verifiedDate = runCatching {
            LocalDate.parse(verifiedAt, DATE_ONLY_FORMATTER)
        }.getOrNull() ?: return false

        val sixMonthsAgo = ZonedDateTime.ofInstant(at, CASABLANCA_ZONE).minusMonths(6).toLocalDate()
        return verifiedDate.isBefore(sixMonthsAgo)
    }

    // MARK: Internal Evaluation

    private data class EvaluationResult(
        val status: OpenStatus,
        val nextChange: HoursChange?
    )

    private data class DailyRule(
        val weekday: Int,
        val openMinutes: Int?,
        val closeMinutes: Int?,
        val closed: Boolean
    ) {
        val isOvernight: Boolean
            get() = openMinutes != null && closeMinutes != null && closeMinutes <= openMinutes
    }

    private fun evaluate(
        weekly: List<String>,
        hoursText: String?,
        at: Instant,
        exceptions: List<ExceptionRule>
    ): EvaluationResult {
        val rules = parseWeeklyRules(weekly, hoursText)
        val hasRuleInputs = rules.isNotEmpty() || exceptions.isNotEmpty()
        if (!hasRuleInputs) {
            return EvaluationResult(OpenStatus.Unknown, null)
        }

        val nowLocal = ZonedDateTime.ofInstant(at, CASABLANCA_ZONE)
        val currentMinutes = nowLocal.hour * 60 + nowLocal.minute

        val todayRule = resolveRule(nowLocal.toLocalDate(), nowLocal.dayOfWeek.value, rules, exceptions)
        val yesterdayDate = nowLocal.toLocalDate().minusDays(1)
        val yesterdayRule = resolveRule(yesterdayDate, yesterdayDate.dayOfWeek.value, rules, exceptions)

        if (yesterdayRule != null && isOpenFromYesterdayOvernight(yesterdayRule, currentMinutes)) {
            val closeMinutes = yesterdayRule.closeMinutes
            if (closeMinutes != null) {
                val closesAt = localDateTimeToInstant(nowLocal.toLocalDate(), closeMinutes)
                if (closesAt != null) {
                    return EvaluationResult(
                        status = OpenStatus.Open(closesAt),
                        nextChange = HoursChange(closesAt, ChangeType.CLOSES)
                    )
                }
            }
        }

        if (todayRule != null && !todayRule.closed && isOpenToday(todayRule, currentMinutes)) {
            val closeMinutes = todayRule.closeMinutes
            if (closeMinutes != null) {
                val closeDate = if (todayRule.isOvernight) {
                    nowLocal.toLocalDate().plusDays(1)
                } else {
                    nowLocal.toLocalDate()
                }
                val closesAt = localDateTimeToInstant(closeDate, closeMinutes)
                if (closesAt != null) {
                    return EvaluationResult(
                        status = OpenStatus.Open(closesAt),
                        nextChange = HoursChange(closesAt, ChangeType.CLOSES)
                    )
                }
            }
        }

        val opensAt = nextOpenTime(nowLocal, currentMinutes, rules, exceptions)
        if (opensAt != null) {
            return EvaluationResult(
                status = OpenStatus.Closed(opensAt),
                nextChange = HoursChange(opensAt, ChangeType.OPENS)
            )
        }

        return EvaluationResult(OpenStatus.Closed(null), null)
    }

    private fun isOpenToday(rule: DailyRule, currentMinutes: Int): Boolean {
        val open = rule.openMinutes ?: return false
        val close = rule.closeMinutes ?: return false

        return if (!rule.isOvernight) {
            currentMinutes >= open && currentMinutes < close
        } else {
            // Overnight windows are considered open only after their start time on the same day.
            // The post-midnight segment is handled by the previous day's overnight rule.
            currentMinutes >= open
        }
    }

    private fun isOpenFromYesterdayOvernight(rule: DailyRule, currentMinutes: Int): Boolean {
        if (!rule.isOvernight) return false
        val close = rule.closeMinutes ?: return false
        return currentMinutes < close
    }

    private fun nextOpenTime(
        nowLocal: ZonedDateTime,
        currentMinutes: Int,
        weeklyRules: List<DailyRule>,
        exceptions: List<ExceptionRule>
    ): Instant? {
        for (dayOffset in 0..7) {
            val candidateDate = nowLocal.toLocalDate().plusDays(dayOffset.toLong())
            val candidateWeekday = candidateDate.dayOfWeek.value
            val rule = resolveRule(candidateDate, candidateWeekday, weeklyRules, exceptions) ?: continue

            if (rule.closed) continue
            val openMinutes = rule.openMinutes ?: continue

            if (dayOffset == 0 && currentMinutes >= openMinutes) {
                continue
            }

            val candidate = localDateTimeToInstant(candidateDate, openMinutes)
            if (candidate != null) {
                return candidate
            }
        }

        return null
    }

    // MARK: Rule Resolution

    private fun resolveRule(
        localDate: LocalDate,
        weekdayIso: Int,
        weeklyRules: List<DailyRule>,
        exceptions: List<ExceptionRule>
    ): DailyRule? {
        val exception = resolveException(localDate, exceptions)
        if (exception != null) {
            return DailyRule(
                weekday = weekdayIsoToCalendar(weekdayIso),
                openMinutes = parseTime(exception.open),
                closeMinutes = parseTime(exception.close),
                closed = exception.closed
            )
        }

        val calendarWeekday = weekdayIsoToCalendar(weekdayIso)
        return weeklyRules.firstOrNull { it.weekday == calendarWeekday }
    }

    private fun resolveException(localDate: LocalDate, exceptions: List<ExceptionRule>): ExceptionRule? {
        if (exceptions.isEmpty()) return null

        val dateKey = localDate.format(DATE_ONLY_FORMATTER)
        val specific = exceptions.firstOrNull { it.date == dateKey }
        if (specific != null) return specific

        if (isRamadan(localDate)) {
            return exceptions.firstOrNull { it.period?.lowercase(Locale.ROOT) == "ramadan" }
        }

        return null
    }

    private fun isRamadan(localDate: LocalDate): Boolean {
        return !localDate.isBefore(LocalDate.parse("2026-03-01")) &&
            !localDate.isAfter(LocalDate.parse("2026-03-30"))
    }

    // MARK: Weekly Parsing

    private fun parseWeeklyRules(weekly: List<String>, hoursText: String?): List<DailyRule> {
        var parsed = weekly.flatMap { parseWeeklyLine(it) }
        if (parsed.isEmpty() && !hoursText.isNullOrBlank()) {
            parsed = parseWeeklyLine(hoursText)
        }

        // Keep first rule per weekday to maintain deterministic behavior.
        return parsed
            .groupBy { it.weekday }
            .toSortedMap()
            .mapNotNull { (_, rules) -> rules.firstOrNull() }
    }

    private fun parseWeeklyLine(rawLine: String): List<DailyRule> {
        val line = rawLine.trim()
        if (line.isEmpty()) return emptyList()

        val normalized = normalize(line)

        if (normalized.contains("open all day")) {
            return ALL_WEEKDAYS.map { weekday ->
                DailyRule(weekday = weekday, openMinutes = 0, closeMinutes = 23 * 60 + 59, closed = false)
            }
        }

        val timeRange = extractTimeRange(normalized)

        if (normalized.contains("closed")) {
            val beforeClosed = normalized.substringBefore("closed")
            val weekdays = parseWeekdays(beforeClosed)
            val targetDays = if (weekdays.isEmpty() && normalized.contains("daily")) ALL_WEEKDAYS else weekdays
            return targetDays.map { weekday ->
                DailyRule(weekday = weekday, openMinutes = null, closeMinutes = null, closed = true)
            }
        }

        if (timeRange == null) return emptyList()

        val weekdays = parseWeekdays(timeRange.prefix)
        val targetDays = when {
            weekdays.isNotEmpty() -> weekdays
            normalized.contains("daily") -> ALL_WEEKDAYS
            else -> emptyList()
        }

        return targetDays.map { weekday ->
            DailyRule(
                weekday = weekday,
                openMinutes = timeRange.open,
                closeMinutes = timeRange.close,
                closed = false
            )
        }
    }

    private fun parseWeekdays(rawText: String): List<Int> {
        val text = normalize(rawText)
        if (text.contains("daily") || text.contains("every day") || text.contains("everyday") || text.contains("all week")) {
            return ALL_WEEKDAYS
        }

        val cleaned = text
            .replace("to", "-")
            .replace("&", ",")
            .replace("/", ",")
            .replace(":", " ")

        val days = mutableSetOf<Int>()

        cleaned.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { part ->
            if (part.contains('-')) {
                val pieces = part.split('-', limit = 2).map { it.trim() }
                val start = weekdayFromToken(pieces.getOrNull(0).orEmpty())
                val end = weekdayFromToken(pieces.getOrNull(1).orEmpty())
                if (start != null && end != null) {
                    days.addAll(weekdayRange(start, end))
                    return@forEach
                }
            }

            val single = weekdayFromToken(part)
            if (single != null) {
                days.add(single)
            }
        }

        return days.sorted()
    }

    private fun weekdayFromToken(raw: String): Int? {
        val token = normalize(raw).substringBefore(' ').trim()
        return when {
            token.startsWith("sun") -> 1
            token.startsWith("mon") -> 2
            token.startsWith("tue") -> 3
            token.startsWith("wed") -> 4
            token.startsWith("thu") -> 5
            token.startsWith("fri") -> 6
            token.startsWith("sat") -> 7
            else -> null
        }
    }

    private fun weekdayRange(start: Int, end: Int): List<Int> {
        return if (start <= end) {
            (start..end).toList()
        } else {
            (start..7).toList() + (1..end).toList()
        }
    }

    private data class TimeRange(
        val open: Int,
        val close: Int,
        val prefix: String
    )

    private fun extractTimeRange(text: String): TimeRange? {
        val match = TIME_RANGE_REGEX.find(text) ?: return null

        val open = parseTime(match.groupValues[1]) ?: return null
        val close = parseTime(match.groupValues[2]) ?: return null
        val prefix = text.substring(0, match.range.first)

        return TimeRange(open, close, prefix)
    }

    private fun parseTime(value: String?): Int? {
        if (value.isNullOrBlank()) return null

        val parts = value.trim().split(':')
        if (parts.size != 2) return null

        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null

        if (hour !in 0..23 || minute !in 0..59) return null

        return hour * 60 + minute
    }

    // MARK: Date Helpers

    private fun localDateTimeToInstant(date: LocalDate, minutes: Int): Instant? {
        val hour = minutes / 60
        val minute = minutes % 60
        val localDateTime = LocalDateTime.of(date, LocalTime.of(hour, minute))
        return runCatching {
            localDateTime.atZone(CASABLANCA_ZONE).toInstant()
        }.getOrNull()
    }

    private fun weekdayIsoToCalendar(weekdayIso: Int): Int {
        // ISO: Monday=1..Sunday=7. Calendar mapping used by this engine: Sunday=1..Saturday=7.
        return if (weekdayIso == 7) 1 else weekdayIso + 1
    }

    // MARK: Formatting

    private fun formatTime(instant: Instant): String {
        val local = instant.atZone(CASABLANCA_ZONE)
        return TWO_DIGIT_TIME.format(local)
    }

    private fun formatDayTime(instant: Instant): String {
        val local = instant.atZone(CASABLANCA_ZONE)
        return DAY_TIME_FORMAT.format(local)
    }

    private fun sanitizedHoursText(text: String?): String? {
        val trimmed = text?.trim().orEmpty()
        return trimmed.ifEmpty { null }
    }

    private fun isSameLocalDay(lhs: Instant, rhs: Instant): Boolean {
        return lhs.atZone(CASABLANCA_ZONE).toLocalDate() == rhs.atZone(CASABLANCA_ZONE).toLocalDate()
    }

    private fun isTomorrowLocalDay(date: Instant, relativeTo: Instant): Boolean {
        val tomorrow = relativeTo.atZone(CASABLANCA_ZONE).toLocalDate().plusDays(1)
        return date.atZone(CASABLANCA_ZONE).toLocalDate() == tomorrow
    }

    private fun normalize(value: String): String {
        return value
            .lowercase(Locale.ROOT)
            .replace('\u2013', '-')
            .replace('\u2014', '-')
            .replace('\u00A0', ' ')
            .trim()
    }

    private val CASABLANCA_ZONE: ZoneId = ZoneId.of("Africa/Casablanca")
    private val DATE_ONLY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
    private val TWO_DIGIT_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val DAY_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.US)
    private val TIME_RANGE_REGEX = Regex("([0-2]?\\d:[0-5]\\d)\\s*-\\s*([0-2]?\\d:[0-5]\\d)")
    private val ALL_WEEKDAYS = listOf(1, 2, 3, 4, 5, 6, 7)
}

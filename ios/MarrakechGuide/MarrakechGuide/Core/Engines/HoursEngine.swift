import Foundation

/// Deterministic opening-hours evaluator used by Explore and Place detail surfaces.
///
/// Rules are evaluated in Morocco local time (`Africa/Casablanca`) with this precedence:
/// 1. Date-specific exception
/// 2. Period exception (currently Ramadan)
/// 3. Weekly schedule
public enum HoursEngine {

    // MARK: - Public Types

    public struct ExceptionRule: Equatable, Sendable {
        public let date: String?
        public let period: String?
        public let open: String?
        public let close: String?
        public let closed: Bool

        public init(
            date: String? = nil,
            period: String? = nil,
            open: String? = nil,
            close: String? = nil,
            closed: Bool = false
        ) {
            self.date = date
            self.period = period
            self.open = open
            self.close = close
            self.closed = closed
        }
    }

    public enum OpenStatus: Equatable {
        case open(closesAt: Date)
        case closed(opensAt: Date?)
        case unknown
    }

    public enum ChangeType: String, Equatable {
        case opens
        case closes
    }

    public struct HoursChange: Equatable {
        public let time: Date
        public let type: ChangeType

        public init(time: Date, type: ChangeType) {
            self.time = time
            self.type = type
        }
    }

    // MARK: - Public API (Place)

    public static func isOpen(
        place: Place,
        at: Date = Date(),
        exceptions: [ExceptionRule] = []
    ) -> OpenStatus {
        isOpen(
            weekly: place.hoursWeekly ?? [],
            hoursText: place.hoursText,
            at: at,
            exceptions: exceptions
        )
    }

    public static func getNextChange(
        place: Place,
        from: Date = Date(),
        exceptions: [ExceptionRule] = []
    ) -> HoursChange? {
        getNextChange(
            weekly: place.hoursWeekly ?? [],
            hoursText: place.hoursText,
            from: from,
            exceptions: exceptions
        )
    }

    public static func formatHoursForDisplay(
        place: Place,
        at: Date = Date(),
        exceptions: [ExceptionRule] = []
    ) -> String {
        formatHoursForDisplay(
            weekly: place.hoursWeekly ?? [],
            hoursText: place.hoursText,
            hoursVerifiedAt: place.hoursVerifiedAt,
            at: at,
            exceptions: exceptions
        )
    }

    // MARK: - Public API (Raw Schedule)

    public static func isOpen(
        weekly: [String],
        hoursText: String?,
        at: Date = Date(),
        exceptions: [ExceptionRule] = []
    ) -> OpenStatus {
        evaluate(weekly: weekly, hoursText: hoursText, at: at, exceptions: exceptions).status
    }

    public static func getNextChange(
        weekly: [String],
        hoursText: String?,
        from: Date = Date(),
        exceptions: [ExceptionRule] = []
    ) -> HoursChange? {
        evaluate(weekly: weekly, hoursText: hoursText, at: from, exceptions: exceptions).nextChange
    }

    public static func formatHoursForDisplay(
        weekly: [String],
        hoursText: String?,
        hoursVerifiedAt: String?,
        at: Date = Date(),
        exceptions: [ExceptionRule] = []
    ) -> String {
        let status = isOpen(weekly: weekly, hoursText: hoursText, at: at, exceptions: exceptions)
        var output: String

        switch status {
        case .open(let closesAt):
            output = "Open now · Closes \(formatTime(closesAt))"

        case .closed(let opensAt):
            guard let opensAt else {
                output = "Closed · Opening time unavailable"
                break
            }

            if isSameLocalDay(opensAt, at) {
                output = "Closed · Opens today \(formatTime(opensAt))"
            } else if isTomorrowLocalDay(opensAt, relativeTo: at) {
                output = "Closed · Opens tomorrow \(formatTime(opensAt))"
            } else {
                output = "Closed · Opens \(formatDayTime(opensAt))"
            }

        case .unknown:
            output = sanitizedHoursText(hoursText) ?? "Check hours locally"
        }

        if isHoursStale(hoursVerifiedAt, at: at) {
            output += " · Hours may be outdated"
        }

        return output
    }

    public static func isHoursStale(_ verifiedAt: String?, at: Date = Date()) -> Bool {
        guard let verifiedAt, let verifiedDate = parseDateOnly(verifiedAt) else {
            return false
        }
        guard let sixMonthsAgo = casablancaCalendar.date(byAdding: .month, value: -6, to: at) else {
            return false
        }
        return verifiedDate < sixMonthsAgo
    }

    // MARK: - Internal Evaluation

    private struct EvaluationResult {
        let status: OpenStatus
        let nextChange: HoursChange?
    }

    private struct DailyRule {
        let weekday: Int
        let openMinutes: Int?
        let closeMinutes: Int?
        let closed: Bool

        var isOvernight: Bool {
            guard let openMinutes, let closeMinutes else { return false }
            return closeMinutes <= openMinutes
        }
    }

    private static func evaluate(
        weekly: [String],
        hoursText: String?,
        at: Date,
        exceptions: [ExceptionRule]
    ) -> EvaluationResult {
        let rules = parseWeeklyRules(weekly: weekly, hoursText: hoursText)
        let hasRuleInputs = !rules.isEmpty || !exceptions.isEmpty

        guard hasRuleInputs else {
            return EvaluationResult(status: .unknown, nextChange: nil)
        }

        let nowComponents = localDateComponents(from: at)
        guard let currentMinutes = minutes(from: nowComponents),
              let todayStart = dayStart(from: nowComponents)
        else {
            return EvaluationResult(status: .unknown, nextChange: nil)
        }

        let todayRule = rule(for: nowComponents, weeklyRules: rules, exceptions: exceptions)

        if let yesterdayDate = casablancaCalendar.date(byAdding: .day, value: -1, to: todayStart) {
            let yesterdayComponents = localDateComponents(from: yesterdayDate)
            if let yesterdayRule = rule(for: yesterdayComponents, weeklyRules: rules, exceptions: exceptions),
               isOpenFromYesterdayOvernight(yesterdayRule, currentMinutes: currentMinutes),
               let closeMinutes = yesterdayRule.closeMinutes,
               let closesAt = localDate(from: nowComponents, minutes: closeMinutes)
            {
                let change = HoursChange(time: closesAt, type: .closes)
                return EvaluationResult(status: .open(closesAt: closesAt), nextChange: change)
            }
        }

        if let todayRule,
           !todayRule.closed,
           let openMinutes = todayRule.openMinutes,
           let closeMinutes = todayRule.closeMinutes,
           isOpenToday(todayRule, currentMinutes: currentMinutes),
           let closesAt = localDate(
               from: nowComponents,
               minutes: closeMinutes,
               dayOffset: todayRule.isOvernight ? 1 : 0
           )
        {
            _ = openMinutes
            let change = HoursChange(time: closesAt, type: .closes)
            return EvaluationResult(status: .open(closesAt: closesAt), nextChange: change)
        }

        let opensAt = nextOpenTime(
            from: at,
            currentMinutes: currentMinutes,
            weeklyRules: rules,
            exceptions: exceptions
        )

        if let opensAt {
            let change = HoursChange(time: opensAt, type: .opens)
            return EvaluationResult(status: .closed(opensAt: opensAt), nextChange: change)
        }

        return EvaluationResult(status: .closed(opensAt: nil), nextChange: nil)
    }

    private static func isOpenToday(_ rule: DailyRule, currentMinutes: Int) -> Bool {
        guard let openMinutes = rule.openMinutes,
              let closeMinutes = rule.closeMinutes
        else {
            return false
        }

        if !rule.isOvernight {
            return currentMinutes >= openMinutes && currentMinutes < closeMinutes
        }

        // Overnight windows are considered open only after their start time on the same day.
        // The post-midnight segment is handled by the previous day's overnight rule.
        return currentMinutes >= openMinutes
    }

    private static func isOpenFromYesterdayOvernight(_ rule: DailyRule, currentMinutes: Int) -> Bool {
        guard rule.isOvernight,
              let closeMinutes = rule.closeMinutes
        else {
            return false
        }
        return currentMinutes < closeMinutes
    }

    private static func nextOpenTime(
        from: Date,
        currentMinutes: Int,
        weeklyRules: [DailyRule],
        exceptions: [ExceptionRule]
    ) -> Date? {
        let nowComponents = localDateComponents(from: from)
        guard let nowDayStart = dayStart(from: nowComponents) else { return nil }

        for dayOffset in 0...7 {
            guard let candidateDay = casablancaCalendar.date(byAdding: .day, value: dayOffset, to: nowDayStart) else {
                continue
            }

            let candidateComponents = localDateComponents(from: candidateDay)
            guard let rule = rule(for: candidateComponents, weeklyRules: weeklyRules, exceptions: exceptions),
                  !rule.closed,
                  let openMinutes = rule.openMinutes
            else {
                continue
            }

            if dayOffset == 0 && currentMinutes >= openMinutes {
                continue
            }

            if let openDate = localDate(from: candidateComponents, minutes: openMinutes) {
                return openDate
            }
        }

        return nil
    }

    // MARK: - Rule Resolution

    private static func rule(
        for localComponents: DateComponents,
        weeklyRules: [DailyRule],
        exceptions: [ExceptionRule]
    ) -> DailyRule? {
        if let exceptionRule = exception(for: localComponents, in: exceptions),
           let weekday = localComponents.weekday
        {
            return DailyRule(
                weekday: weekday,
                openMinutes: parseTime(exceptionRule.open),
                closeMinutes: parseTime(exceptionRule.close),
                closed: exceptionRule.closed
            )
        }

        guard let weekday = localComponents.weekday else { return nil }
        return weeklyRules.first { $0.weekday == weekday }
    }

    private static func exception(
        for localComponents: DateComponents,
        in exceptions: [ExceptionRule]
    ) -> ExceptionRule? {
        guard !exceptions.isEmpty,
              let dateKey = localDateKey(localComponents)
        else {
            return nil
        }

        if let specific = exceptions.first(where: { $0.date == dateKey }) {
            return specific
        }

        if isRamadan(localComponents) {
            return exceptions.first { $0.period?.lowercased() == "ramadan" }
        }

        return nil
    }

    private static func isRamadan(_ components: DateComponents) -> Bool {
        guard let dateKey = localDateKey(components) else { return false }
        return dateKey >= "2026-03-01" && dateKey <= "2026-03-30"
    }

    // MARK: - Weekly Parsing

    private static func parseWeeklyRules(weekly: [String], hoursText: String?) -> [DailyRule] {
        var parsed: [DailyRule] = weekly.flatMap(parseWeeklyLine)

        if parsed.isEmpty, let fallback = hoursText {
            parsed = parseWeeklyLine(fallback)
        }

        // Keep first rule per weekday to maintain deterministic behavior.
        var byDay: [Int: DailyRule] = [:]
        for rule in parsed where byDay[rule.weekday] == nil {
            byDay[rule.weekday] = rule
        }

        return byDay.keys.sorted().compactMap { byDay[$0] }
    }

    private static func parseWeeklyLine(_ rawLine: String) -> [DailyRule] {
        let line = rawLine.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !line.isEmpty else { return [] }

        let normalized = normalize(line)

        if normalized.contains("open all day") {
            return allWeekdays.map { weekday in
                DailyRule(weekday: weekday, openMinutes: 0, closeMinutes: 23 * 60 + 59, closed: false)
            }
        }

        let timeRange = extractTimeRange(from: normalized)

        if normalized.contains("closed") {
            let beforeClosed = normalized.components(separatedBy: "closed").first ?? normalized
            let weekdays = parseWeekdays(beforeClosed)
            let targetDays = weekdays.isEmpty && normalized.contains("daily") ? allWeekdays : weekdays
            return targetDays.map { weekday in
                DailyRule(weekday: weekday, openMinutes: nil, closeMinutes: nil, closed: true)
            }
        }

        guard let (openMinutes, closeMinutes, prefix) = timeRange else {
            return []
        }

        let weekdays = parseWeekdays(prefix)
        let targetDays: [Int]
        if weekdays.isEmpty {
            targetDays = normalized.contains("daily") ? allWeekdays : []
        } else {
            targetDays = weekdays
        }

        return targetDays.map { weekday in
            DailyRule(weekday: weekday, openMinutes: openMinutes, closeMinutes: closeMinutes, closed: false)
        }
    }

    private static func parseWeekdays(_ rawText: String) -> [Int] {
        let text = normalize(rawText)
        if text.contains("daily") || text.contains("every day") || text.contains("everyday") || text.contains("all week") {
            return allWeekdays
        }

        let cleaned = text
            .replacingOccurrences(of: "to", with: "-")
            .replacingOccurrences(of: "&", with: ",")
            .replacingOccurrences(of: "/", with: ",")
            .replacingOccurrences(of: ":", with: " ")

        let commaParts = cleaned.split(separator: ",")
        var weekdays: [Int] = []

        for partSub in commaParts {
            let part = partSub.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !part.isEmpty else { continue }

            if part.contains("-") {
                let pieces = part.split(separator: "-", maxSplits: 1).map { String($0).trimmingCharacters(in: .whitespacesAndNewlines) }
                if pieces.count == 2,
                   let start = weekday(from: pieces[0]),
                   let end = weekday(from: pieces[1])
                {
                    weekdays.append(contentsOf: weekdayRange(start: start, end: end))
                    continue
                }
            }

            if let single = weekday(from: part) {
                weekdays.append(single)
            }
        }

        return Array(Set(weekdays)).sorted()
    }

    private static func weekday(from raw: String) -> Int? {
        let token = normalize(raw)
            .split(separator: " ")
            .first
            .map(String.init) ?? ""

        switch token {
        case let value where value.hasPrefix("sun"):
            return 1
        case let value where value.hasPrefix("mon"):
            return 2
        case let value where value.hasPrefix("tue"):
            return 3
        case let value where value.hasPrefix("wed"):
            return 4
        case let value where value.hasPrefix("thu"):
            return 5
        case let value where value.hasPrefix("fri"):
            return 6
        case let value where value.hasPrefix("sat"):
            return 7
        default:
            return nil
        }
    }

    private static func weekdayRange(start: Int, end: Int) -> [Int] {
        if start <= end {
            return Array(start...end)
        }

        // Wrap-around range (e.g. Fri-Mon).
        let first = Array(start...7)
        let second = Array(1...end)
        return first + second
    }

    private static func extractTimeRange(from text: String) -> (Int, Int, String)? {
        let regex = try? NSRegularExpression(pattern: #"([0-2]?\d:[0-5]\d)\s*-\s*([0-2]?\d:[0-5]\d)"#)
        let fullRange = NSRange(text.startIndex..., in: text)

        guard let regex,
              let match = regex.firstMatch(in: text, options: [], range: fullRange),
              match.numberOfRanges >= 3,
              let firstRange = Range(match.range(at: 1), in: text),
              let secondRange = Range(match.range(at: 2), in: text),
              let prefixRange = Range(NSRange(location: 0, length: match.range.location), in: text)
        else {
            return nil
        }

        let openString = String(text[firstRange])
        let closeString = String(text[secondRange])
        let prefix = String(text[prefixRange])

        guard let open = parseTime(openString), let close = parseTime(closeString) else {
            return nil
        }

        return (open, close, prefix)
    }

    private static func parseTime(_ value: String?) -> Int? {
        guard let value else { return nil }
        let cleaned = value.trimmingCharacters(in: .whitespacesAndNewlines)
        let parts = cleaned.split(separator: ":")
        guard parts.count == 2,
              let hour = Int(parts[0]),
              let minute = Int(parts[1]),
              (0...23).contains(hour),
              (0...59).contains(minute)
        else {
            return nil
        }
        return hour * 60 + minute
    }

    // MARK: - Date Helpers

    private static let casablancaTimeZone = TimeZone(identifier: "Africa/Casablanca") ?? .current

    private static var casablancaCalendar: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = casablancaTimeZone
        return calendar
    }

    private static let allWeekdays: [Int] = [1, 2, 3, 4, 5, 6, 7]

    private static func localDateComponents(from date: Date) -> DateComponents {
        casablancaCalendar.dateComponents([.year, .month, .day, .weekday, .hour, .minute], from: date)
    }

    private static func minutes(from components: DateComponents) -> Int? {
        guard let hour = components.hour, let minute = components.minute else {
            return nil
        }
        return hour * 60 + minute
    }

    private static func dayStart(from components: DateComponents) -> Date? {
        guard let year = components.year, let month = components.month, let day = components.day else {
            return nil
        }
        return casablancaCalendar.date(from: DateComponents(
            timeZone: casablancaTimeZone,
            year: year,
            month: month,
            day: day,
            hour: 0,
            minute: 0
        ))
    }

    private static func localDate(
        from components: DateComponents,
        minutes: Int,
        dayOffset: Int = 0
    ) -> Date? {
        guard let start = dayStart(from: components),
              let shifted = casablancaCalendar.date(byAdding: .day, value: dayOffset, to: start)
        else {
            return nil
        }

        let hour = minutes / 60
        let minute = minutes % 60

        let shiftedComponents = casablancaCalendar.dateComponents([.year, .month, .day], from: shifted)
        return casablancaCalendar.date(from: DateComponents(
            timeZone: casablancaTimeZone,
            year: shiftedComponents.year,
            month: shiftedComponents.month,
            day: shiftedComponents.day,
            hour: hour,
            minute: minute
        ))
    }

    private static func localDateKey(_ components: DateComponents) -> String? {
        guard let year = components.year, let month = components.month, let day = components.day else {
            return nil
        }
        return String(format: "%04d-%02d-%02d", year, month, day)
    }

    private static func parseDateOnly(_ raw: String) -> Date? {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = casablancaTimeZone
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.date(from: raw)
    }

    // MARK: - Formatting

    private static func formatTime(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = casablancaTimeZone
        formatter.dateFormat = "HH:mm"
        return formatter.string(from: date)
    }

    private static func formatDayTime(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = casablancaTimeZone
        formatter.dateFormat = "EEE HH:mm"
        return formatter.string(from: date)
    }

    private static func sanitizedHoursText(_ text: String?) -> String? {
        guard let text else { return nil }
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }

    private static func isSameLocalDay(_ lhs: Date, _ rhs: Date) -> Bool {
        casablancaCalendar.isDate(lhs, inSameDayAs: rhs)
    }

    private static func isTomorrowLocalDay(_ date: Date, relativeTo reference: Date) -> Bool {
        guard let tomorrow = casablancaCalendar.date(byAdding: .day, value: 1, to: reference) else {
            return false
        }
        return casablancaCalendar.isDate(date, inSameDayAs: tomorrow)
    }

    private static func normalize(_ value: String) -> String {
        value
            .lowercased()
            .replacingOccurrences(of: "\u{2013}", with: "-")
            .replacingOccurrences(of: "\u{2014}", with: "-")
            .replacingOccurrences(of: "\u{00A0}", with: " ")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

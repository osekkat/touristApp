import Foundation
import CoreLocation

/// PlanEngine generates deterministic "My Day" plans from user constraints.
///
/// Test vectors: shared/tests/plan-engine-vectors.json
public enum PlanEngine {

    // MARK: - Types

    public struct Coordinate: Equatable, Sendable {
        public let lat: Double
        public let lng: Double

        public init(lat: Double, lng: Double) {
            self.lat = lat
            self.lng = lng
        }

        fileprivate var clCoordinate: CLLocationCoordinate2D {
            CLLocationCoordinate2D(latitude: lat, longitude: lng)
        }
    }

    public enum Interest: String, CaseIterable, Sendable {
        case history
        case food
        case shopping
        case nature
        case culture
        case architecture
        case relaxation
        case nightlife
        case general

        public static func from(_ rawValue: String) -> Interest? {
            Interest(rawValue: rawValue.lowercased())
        }
    }

    public enum Pace: String, CaseIterable, Sendable {
        case relaxed
        case standard
        case active

        public static func from(_ rawValue: String) -> Pace? {
            Pace(rawValue: rawValue.lowercased())
        }
    }

    public enum BudgetTier: String, CaseIterable, Sendable {
        case budget
        case mid
        case splurge

        public static func from(_ rawValue: String) -> BudgetTier? {
            BudgetTier(rawValue: rawValue.lowercased())
        }
    }

    public struct Input: Sendable {
        public let availableMinutes: Int
        public let startPoint: Coordinate?
        public let interests: [Interest]
        public let pace: Pace
        public let budgetTier: BudgetTier
        public let currentTime: Date
        public let places: [Place]
        public let recentPlaceIds: Set<String>

        public init(
            availableMinutes: Int,
            startPoint: Coordinate?,
            interests: [Interest],
            pace: Pace,
            budgetTier: BudgetTier,
            currentTime: Date,
            places: [Place],
            recentPlaceIds: Set<String> = []
        ) {
            self.availableMinutes = availableMinutes
            self.startPoint = startPoint
            self.interests = interests
            self.pace = pace
            self.budgetTier = budgetTier
            self.currentTime = currentTime
            self.places = places
            self.recentPlaceIds = recentPlaceIds
        }
    }

    public struct PriceRange: Equatable, Sendable {
        public let minMad: Int
        public let maxMad: Int

        public init(minMad: Int, maxMad: Int) {
            self.minMad = minMad
            self.maxMad = maxMad
        }
    }

    public struct PlanStop: Equatable, Sendable {
        public let placeId: String
        public let arrivalTime: Date
        public let departureTime: Date
        public let travelMinutesFromPrevious: Int
        public let visitMinutes: Int

        public init(
            placeId: String,
            arrivalTime: Date,
            departureTime: Date,
            travelMinutesFromPrevious: Int,
            visitMinutes: Int
        ) {
            self.placeId = placeId
            self.arrivalTime = arrivalTime
            self.departureTime = departureTime
            self.travelMinutesFromPrevious = travelMinutesFromPrevious
            self.visitMinutes = visitMinutes
        }
    }

    public struct Output: Sendable {
        public let stops: [PlanStop]
        public let totalMinutes: Int
        public let estimatedCostRange: PriceRange
        public let warnings: [String]

        public init(stops: [PlanStop], totalMinutes: Int, estimatedCostRange: PriceRange, warnings: [String]) {
            self.stops = stops
            self.totalMinutes = totalMinutes
            self.estimatedCostRange = estimatedCostRange
            self.warnings = warnings
        }
    }

    // MARK: - Public API

    public static func generate(_ input: Input) -> Output {
        let availableMinutes = max(0, input.availableMinutes)
        guard availableMinutes > 0 else {
            return Output(
                stops: [],
                totalMinutes: 0,
                estimatedCostRange: PriceRange(minMad: 0, maxMad: 0),
                warnings: ["Available time is too short to generate a plan."]
            )
        }

        var requiredMealSlots = mealSlotsOverlapping(
            start: input.currentTime,
            durationMinutes: availableMinutes
        )

        var warnings: [String] = []
        var candidatePlaces = input.places.filter { !input.recentPlaceIds.contains($0.id) }

        if !input.interests.isEmpty {
            candidatePlaces = candidatePlaces.filter { interestMatchCount(for: $0, interests: input.interests) > 0 }
        }

        candidatePlaces = candidatePlaces.filter { budgetAllows(place: $0, tier: input.budgetTier) }

        // For long food-focused days, require all meal slots represented by feasible food venues.
        // This keeps plans from skipping dinner simply because overlap windows omitted it.
        if input.interests.contains(.food), availableMinutes >= 360 {
            let availableMealSlots = candidatePlaces.reduce(into: Set<MealSlot>()) { partialResult, place in
                partialResult.formUnion(mealSlots(for: place))
            }
            requiredMealSlots.formUnion(availableMealSlots)
        }

        if candidatePlaces.isEmpty {
            warnings.append("No places match your constraints right now.")
            return Output(
                stops: [],
                totalMinutes: 0,
                estimatedCostRange: PriceRange(minMad: 0, maxMad: 0),
                warnings: warnings
            )
        }

        let selection = greedySelection(
            candidates: candidatePlaces,
            input: input,
            availableMinutes: availableMinutes,
            requiredMealSlots: requiredMealSlots
        )

        if selection.closedExclusionCount > 0 {
            warnings.append("Some places were excluded because they are closed at the planned visit time.")
        }

        if selection.places.isEmpty {
            warnings.append("No plan could fit your time and constraints. Try increasing available time or broadening interests.")
            return Output(
                stops: [],
                totalMinutes: 0,
                estimatedCostRange: PriceRange(minMad: 0, maxMad: 0),
                warnings: warnings
            )
        }

        let directSchedule = buildSchedule(
            places: selection.places,
            startTime: input.currentTime,
            startPoint: input.startPoint,
            availableMinutes: availableMinutes,
            pace: input.pace
        )
        let reorderedPlaces = reorderNearestNeighbor(places: selection.places, startPoint: input.startPoint)
        let scheduled: ScheduleResult
        if reorderedPlaces == selection.places {
            scheduled = directSchedule
        } else {
            let reorderedSchedule = buildSchedule(
                places: reorderedPlaces,
                startTime: input.currentTime,
                startPoint: input.startPoint,
                availableMinutes: availableMinutes,
                pace: input.pace
            )
            scheduled = preferredSchedule(
                primary: directSchedule,
                alternative: reorderedSchedule,
                requiredMealSlots: requiredMealSlots
            )
        }

        if scheduled.droppedCount > 0 {
            warnings.append("Some candidate stops were dropped during schedule construction.")
        }

        let selectedMealSlots = scheduled.selectedPlaces.reduce(into: Set<MealSlot>()) { partialResult, place in
            partialResult.formUnion(mealSlots(for: place))
        }

        let missingMealSlots = requiredMealSlots.subtracting(selectedMealSlots)
        if !missingMealSlots.isEmpty {
            let formatted = missingMealSlots
                .map(\.rawValue)
                .sorted()
                .joined(separator: ", ")
            warnings.append("Could not schedule meal stop(s): \(formatted).")
        }

        let costRange = estimatedCostRange(for: scheduled.selectedPlaces, tier: input.budgetTier)

        return Output(
            stops: scheduled.stops,
            totalMinutes: scheduled.totalMinutes,
            estimatedCostRange: costRange,
            warnings: warnings
        )
    }

    // MARK: - Selection

    private struct EvaluatedCandidate {
        let place: Place
        let travelMinutes: Int
        let visitMinutes: Int
        let score: Double
    }

    private struct SelectionResult {
        let places: [Place]
        let closedExclusionCount: Int
    }

    private static func greedySelection(
        candidates: [Place],
        input: Input,
        availableMinutes: Int,
        requiredMealSlots: Set<MealSlot>
    ) -> SelectionResult {
        var remainingMinutes = availableMinutes
        var elapsedMinutes = 0
        var currentCoordinate = input.startPoint
        var selected: [Place] = []
        var selectedIds: Set<String> = []
        var selectedMealSlots: Set<MealSlot> = []
        var closedExclusionCount = 0

        let minimumStopMinutes = minVisitMinutes(for: input.pace)
        let maximumStops = maxStops(for: input.pace, availableMinutes: availableMinutes)

        while remainingMinutes >= minimumStopMinutes, selected.count < maximumStops {
            var evaluations: [EvaluatedCandidate] = []
            let pendingMealSlots = requiredMealSlots.subtracting(selectedMealSlots)

            for candidate in candidates where !selectedIds.contains(candidate.id) {
                let travelMinutes = travelMinutes(from: currentCoordinate, to: candidate)
                let visitMinutes = recommendedVisitMinutes(for: candidate, pace: input.pace)
                let requiredMinutes = travelMinutes + visitMinutes

                guard requiredMinutes <= remainingMinutes else { continue }

                let arrivalTime = input.currentTime.addingTimeInterval(TimeInterval(elapsedMinutes + travelMinutes) * 60)
                if case .closed = HoursEngine.isOpen(place: candidate, at: arrivalTime) {
                    closedExclusionCount += 1
                    continue
                }

                var score = baseScore(
                    for: candidate,
                    interests: input.interests,
                    budgetTier: input.budgetTier,
                    arrivalTime: arrivalTime
                )

                if !selected.contains(where: { $0.category == candidate.category }) {
                    score += 3
                }

                let mealMatches = mealSlots(for: candidate).intersection(pendingMealSlots).count
                if mealMatches > 0 {
                    score += Double(mealMatches * 12)
                }

                score -= Double(travelMinutes) * 0.35

                evaluations.append(EvaluatedCandidate(
                    place: candidate,
                    travelMinutes: travelMinutes,
                    visitMinutes: visitMinutes,
                    score: score
                ))
            }

            guard let best = evaluations.sorted(by: { lhs, rhs in
                candidateSort(lhs: lhs, rhs: rhs)
            }).first else {
                break
            }

            selected.append(best.place)
            selectedIds.insert(best.place.id)
            selectedMealSlots.formUnion(mealSlots(for: best.place))
            remainingMinutes -= (best.travelMinutes + best.visitMinutes)
            elapsedMinutes += (best.travelMinutes + best.visitMinutes)

            if let coordinate = coordinate(for: best.place) {
                currentCoordinate = coordinate
            }
        }

        return SelectionResult(
            places: selected,
            closedExclusionCount: closedExclusionCount
        )
    }

    private static func candidateSort(lhs: EvaluatedCandidate, rhs: EvaluatedCandidate) -> Bool {
        if lhs.score != rhs.score {
            return lhs.score > rhs.score
        }

        let lhsMinutes = lhs.travelMinutes + lhs.visitMinutes
        let rhsMinutes = rhs.travelMinutes + rhs.visitMinutes
        if lhsMinutes != rhsMinutes {
            return lhsMinutes < rhsMinutes
        }

        return lhs.place.id < rhs.place.id
    }

    // MARK: - Schedule

    private struct ScheduleResult {
        let stops: [PlanStop]
        let selectedPlaces: [Place]
        let totalMinutes: Int
        let droppedCount: Int
    }

    private static func buildSchedule(
        places: [Place],
        startTime: Date,
        startPoint: Coordinate?,
        availableMinutes: Int,
        pace: Pace
    ) -> ScheduleResult {
        var stops: [PlanStop] = []
        var selectedPlaces: [Place] = []
        var currentCoordinate = startPoint
        var elapsedMinutes = 0
        var droppedCount = 0

        for place in places {
            let travel = travelMinutes(from: currentCoordinate, to: place)
            let visit = recommendedVisitMinutes(for: place, pace: pace)
            let required = travel + visit

            if elapsedMinutes + required > availableMinutes {
                droppedCount += 1
                continue
            }

            let arrival = startTime.addingTimeInterval(TimeInterval(elapsedMinutes + travel) * 60)
            if case .closed = HoursEngine.isOpen(place: place, at: arrival) {
                droppedCount += 1
                continue
            }

            let departure = arrival.addingTimeInterval(TimeInterval(visit) * 60)

            stops.append(PlanStop(
                placeId: place.id,
                arrivalTime: arrival,
                departureTime: departure,
                travelMinutesFromPrevious: travel,
                visitMinutes: visit
            ))
            selectedPlaces.append(place)

            elapsedMinutes += required
            if let nextCoordinate = coordinate(for: place) {
                currentCoordinate = nextCoordinate
            }
        }

        return ScheduleResult(
            stops: stops,
            selectedPlaces: selectedPlaces,
            totalMinutes: elapsedMinutes,
            droppedCount: droppedCount
        )
    }

    private static func reorderNearestNeighbor(places: [Place], startPoint: Coordinate?) -> [Place] {
        guard let startPoint, places.count > 1 else {
            return places
        }

        var remaining = places
        var ordered: [Place] = []
        var current = startPoint

        while !remaining.isEmpty {
            let nextIndex = remaining.indices.min { lhs, rhs in
                let lhsDistance = linearDistance(from: current, to: remaining[lhs])
                let rhsDistance = linearDistance(from: current, to: remaining[rhs])

                if lhsDistance != rhsDistance {
                    return lhsDistance < rhsDistance
                }
                return remaining[lhs].id < remaining[rhs].id
            }

            guard let index = nextIndex else { break }
            let nextPlace = remaining.remove(at: index)
            ordered.append(nextPlace)

            if let coordinate = coordinate(for: nextPlace) {
                current = coordinate
            }
        }

        return ordered
    }

    private static func preferredSchedule(
        primary: ScheduleResult,
        alternative: ScheduleResult,
        requiredMealSlots: Set<MealSlot>
    ) -> ScheduleResult {
        let primaryMealCoverage = coveredRequiredMealSlots(in: primary, requiredMealSlots: requiredMealSlots)
        let alternativeMealCoverage = coveredRequiredMealSlots(in: alternative, requiredMealSlots: requiredMealSlots)
        if alternativeMealCoverage != primaryMealCoverage {
            return alternativeMealCoverage > primaryMealCoverage ? alternative : primary
        }

        if alternative.stops.count != primary.stops.count {
            return alternative.stops.count > primary.stops.count ? alternative : primary
        }

        if alternative.totalMinutes != primary.totalMinutes {
            return alternative.totalMinutes < primary.totalMinutes ? alternative : primary
        }

        return primary
    }

    private static func coveredRequiredMealSlots(
        in schedule: ScheduleResult,
        requiredMealSlots: Set<MealSlot>
    ) -> Int {
        if requiredMealSlots.isEmpty {
            return 0
        }

        let selectedMealSlots = schedule.selectedPlaces.reduce(into: Set<MealSlot>()) { partialResult, place in
            partialResult.formUnion(mealSlots(for: place))
        }
        return selectedMealSlots.intersection(requiredMealSlots).count
    }

    // MARK: - Scoring Helpers

    private enum MealSlot: String, CaseIterable {
        case breakfast
        case lunch
        case dinner
    }

    private static func baseScore(
        for place: Place,
        interests: [Interest],
        budgetTier: BudgetTier,
        arrivalTime: Date
    ) -> Double {
        let interestScore = Double(interestMatchCount(for: place, interests: interests) * 10)
        let trapPenalty = touristTrapPenalty(for: place)
        let timeBonus = bestTimeBonus(for: place, at: arrivalTime)
        let budgetScore = budgetFitBonus(for: place, tier: budgetTier)
        return interestScore + trapPenalty + timeBonus + budgetScore
    }

    private static func interestMatchCount(for place: Place, interests: [Interest]) -> Int {
        if interests.isEmpty { return 1 }

        let normalizedCategory = place.category?.lowercased() ?? ""
        let normalizedTags = Set((place.tags ?? []).map { $0.lowercased() })
        let normalizedName = place.name.lowercased()

        return interests.reduce(0) { partialResult, interest in
            if interest == .general {
                return partialResult + 1
            }

            let categories = interestCategories[interest] ?? []
            let tokens = interestTokens[interest] ?? []

            let categoryMatch = categories.contains(normalizedCategory)
            let tagMatch = normalizedTags.contains { tag in
                tokens.contains { token in tag.contains(token) }
            }
            let nameMatch = tokens.contains { normalizedName.contains($0) }

            return partialResult + ((categoryMatch || tagMatch || nameMatch) ? 1 : 0)
        }
    }

    private static let interestCategories: [Interest: Set<String>] = [
        .history: ["museum", "historic_site", "landmark", "neighborhood"],
        .food: ["restaurant", "cafe", "market"],
        .shopping: ["market", "neighborhood"],
        .nature: ["garden", "nature"],
        .culture: ["museum", "historic_site", "landmark", "neighborhood"],
        .architecture: ["museum", "historic_site", "landmark"],
        .relaxation: ["garden", "nature", "cafe"],
        .nightlife: ["restaurant", "cafe", "market", "landmark"]
    ]

    private static let interestTokens: [Interest: [String]] = [
        .history: ["history", "heritage", "palace", "madrasa", "museum", "historic"],
        .food: ["food", "eat", "restaurant", "cafe", "snack"],
        .shopping: ["shop", "shopping", "souk", "market", "artisan"],
        .nature: ["garden", "park", "nature"],
        .culture: ["culture", "heritage", "tradition", "architecture"],
        .architecture: ["architecture", "design", "mosaic", "riads"],
        .relaxation: ["relax", "calm", "garden", "spa"],
        .nightlife: ["night", "evening", "music", "rooftop"]
    ]

    private static func touristTrapPenalty(for place: Place) -> Double {
        switch place.touristTrapLevel?.lowercased() {
        case "high":
            return -5
        case "mixed":
            return -2
        default:
            return 0
        }
    }

    private static func bestTimeBonus(for place: Place, at date: Date) -> Double {
        let currentWindow = timeWindow(for: date)
        let windows = Set((place.bestTimeWindows ?? []).map { $0.lowercased() })
        return windows.contains(currentWindow) ? 5 : 0
    }

    private static func budgetAllows(place: Place, tier: BudgetTier) -> Bool {
        let tags = Set((place.tags ?? []).map { $0.lowercased() })

        switch tier {
        case .budget:
            return !tags.contains(where: { $0.contains("luxury") || $0.contains("fine-dining") || $0.contains("upscale") })
        case .mid:
            return !tags.contains(where: { $0.contains("ultra-luxury") })
        case .splurge:
            return true
        }
    }

    private static func budgetFitBonus(for place: Place, tier: BudgetTier) -> Double {
        let tags = Set((place.tags ?? []).map { $0.lowercased() })
        switch tier {
        case .budget:
            if tags.contains(where: { $0.contains("budget") || $0.contains("local") }) {
                return 2
            }
            if tags.contains(where: { $0.contains("luxury") || $0.contains("fine-dining") }) {
                return -4
            }
            return 0

        case .mid:
            return 0

        case .splurge:
            return tags.contains(where: { $0.contains("luxury") || $0.contains("fine-dining") }) ? 3 : 0
        }
    }

    // MARK: - Meal Helpers

    private static func mealSlotsOverlapping(start: Date, durationMinutes: Int) -> Set<MealSlot> {
        let end = start.addingTimeInterval(TimeInterval(durationMinutes) * 60)
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = planningTimeZone

        func overlaps(hourStart: Int, hourEnd: Int) -> Bool {
            guard let rangeStart = calendar.date(bySettingHour: hourStart, minute: 0, second: 0, of: start),
                  let rangeEnd = calendar.date(bySettingHour: hourEnd, minute: 0, second: 0, of: start)
            else {
                return false
            }
            return max(start, rangeStart) < min(end, rangeEnd)
        }

        var slots: Set<MealSlot> = []
        if overlaps(hourStart: 7, hourEnd: 11) {
            slots.insert(.breakfast)
        }
        if overlaps(hourStart: 12, hourEnd: 15) {
            slots.insert(.lunch)
        }
        if overlaps(hourStart: 19, hourEnd: 22) {
            slots.insert(.dinner)
        }
        return slots
    }

    private static func mealSlots(for place: Place) -> Set<MealSlot> {
        let category = place.category?.lowercased() ?? ""
        guard category == "restaurant" || category == "cafe" else {
            return []
        }

        let tags = Set((place.tags ?? []).map { $0.lowercased() })
        let windows = Set((place.bestTimeWindows ?? []).map { $0.lowercased() })

        var slots: Set<MealSlot> = []

        if tags.contains(where: { $0.contains("breakfast") || $0.contains("brunch") }) || windows.contains("morning") {
            slots.insert(.breakfast)
        }
        if tags.contains(where: { $0.contains("lunch") }) || windows.contains("lunch") || windows.contains("afternoon") {
            slots.insert(.lunch)
        }
        if tags.contains(where: { $0.contains("dinner") || $0.contains("evening") }) || windows.contains("evening") {
            slots.insert(.dinner)
        }

        return slots
    }

    // MARK: - Time/Travel Helpers

    private static func recommendedVisitMinutes(for place: Place, pace: Pace) -> Int {
        let defaultVisit: Int
        switch pace {
        case .relaxed:
            defaultVisit = 90
        case .standard:
            defaultVisit = 60
        case .active:
            defaultVisit = 45
        }

        let minVisit = max(20, place.visitMinMinutes ?? defaultVisit)
        let maxVisit = max(minVisit, place.visitMaxMinutes ?? minVisit)

        switch pace {
        case .relaxed:
            return maxVisit
        case .standard:
            return Int(round(Double(minVisit + maxVisit) / 2))
        case .active:
            return minVisit
        }
    }

    private static func minVisitMinutes(for pace: Pace) -> Int {
        switch pace {
        case .relaxed:
            return 40
        case .standard:
            return 30
        case .active:
            return 20
        }
    }

    private static func maxStops(for pace: Pace, availableMinutes: Int) -> Int {
        let paceCap: Int
        switch pace {
        case .relaxed:
            paceCap = 6
        case .standard:
            paceCap = 7
        case .active:
            paceCap = 8
        }

        let timeCap = max(1, availableMinutes / 40)
        return min(paceCap, timeCap)
    }

    private static func timeWindow(for date: Date) -> String {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = planningTimeZone
        let hour = calendar.component(.hour, from: date)
        switch hour {
        case 6..<11:
            return "morning"
        case 11..<15:
            return "lunch"
        case 15..<18:
            return "afternoon"
        case 18..<23:
            return "evening"
        default:
            return "night"
        }
    }

    private static let planningTimeZone = TimeZone(identifier: "Africa/Casablanca") ?? .current

    private static func travelMinutes(from: Coordinate?, to place: Place) -> Int {
        guard let from else { return 0 }
        guard let destination = coordinate(for: place) else { return 10 }

        let distanceMeters = GeoEngine.haversine(from: from.clCoordinate, to: destination.clCoordinate)
        if distanceMeters <= 20 {
            return 0
        }

        let region = place.regionId ?? GeoEngine.determineRegion(destination.clCoordinate)
        let estimate = GeoEngine.estimateWalkTime(meters: distanceMeters, region: region)
        return min(60, max(1, estimate))
    }

    private static func linearDistance(from: Coordinate, to place: Place) -> Double {
        guard let destination = coordinate(for: place) else { return 1_000_000 }
        return GeoEngine.haversine(from: from.clCoordinate, to: destination.clCoordinate)
    }

    private static func coordinate(for place: Place) -> Coordinate? {
        guard let lat = place.lat, let lng = place.lng else {
            return nil
        }
        return Coordinate(lat: lat, lng: lng)
    }

    // MARK: - Cost

    private static func estimatedCostRange(for places: [Place], tier: BudgetTier) -> PriceRange {
        let multiplier: Double
        switch tier {
        case .budget:
            multiplier = 0.80
        case .mid:
            multiplier = 1.00
        case .splurge:
            multiplier = 1.25
        }

        let (minTotal, maxTotal) = places.reduce(into: (0, 0)) { partialResult, place in
            let (minCost, maxCost) = baseCostRange(for: place.category)
            partialResult.0 += minCost
            partialResult.1 += maxCost
        }

        let adjustedMin = Int((Double(minTotal) * multiplier).rounded())
        let adjustedMax = Int((Double(maxTotal) * multiplier).rounded())

        return PriceRange(minMad: max(0, adjustedMin), maxMad: max(0, adjustedMax))
    }

    private static func baseCostRange(for category: String?) -> (Int, Int) {
        switch category?.lowercased() {
        case "restaurant":
            return (80, 180)
        case "cafe":
            return (20, 60)
        case "museum":
            return (70, 120)
        case "historic_site":
            return (40, 90)
        case "garden":
            return (40, 100)
        case "market":
            return (0, 40)
        case "landmark", "neighborhood", "nature":
            return (0, 30)
        default:
            return (20, 80)
        }
    }
}

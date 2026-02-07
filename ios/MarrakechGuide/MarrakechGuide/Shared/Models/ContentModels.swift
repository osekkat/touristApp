import Foundation
import GRDB

// MARK: - Place

/// A curated place in Marrakech
struct Place: Codable, Identifiable, Sendable {
    let id: String
    let name: String
    let aliases: [String]?
    let regionId: String?
    let category: String?
    let shortDescription: String?
    let longDescription: String?
    let reviewedAt: String?
    let status: String?
    let confidence: String?
    let touristTrapLevel: String?
    let whyRecommended: [String]?
    let neighborhood: String?
    let address: String?
    let lat: Double?
    let lng: Double?
    let hoursText: String?
    let hoursWeekly: [String]?
    let hoursVerifiedAt: String?
    let feesMinMad: Int?
    let feesMaxMad: Int?
    let expectedCostMinMad: Int?
    let expectedCostMaxMad: Int?
    let visitMinMinutes: Int?
    let visitMaxMinutes: Int?
    let bestTimeToGo: String?
    let bestTimeWindows: [String]?
    let tags: [String]?
    let localTips: [String]?
    let scamWarnings: [String]?
    let doAndDont: [String]?
    let images: [String]?
    let sourceRefs: [Int]?

    enum CodingKeys: String, CodingKey {
        case id, name, aliases, category, neighborhood, address, lat, lng, status, confidence, images
        case regionId = "region_id"
        case shortDescription = "short_description"
        case longDescription = "long_description"
        case reviewedAt = "reviewed_at"
        case touristTrapLevel = "tourist_trap_level"
        case whyRecommended = "why_recommended"
        case hoursText = "hours_text"
        case hoursWeekly = "hours_weekly"
        case hoursVerifiedAt = "hours_verified_at"
        case feesMinMad = "fees_min_mad"
        case feesMaxMad = "fees_max_mad"
        case expectedCostMinMad = "expected_cost_min_mad"
        case expectedCostMaxMad = "expected_cost_max_mad"
        case visitMinMinutes = "visit_min_minutes"
        case visitMaxMinutes = "visit_max_minutes"
        case bestTimeToGo = "best_time_to_go"
        case bestTimeWindows = "best_time_windows"
        case tags
        case localTips = "local_tips"
        case scamWarnings = "scam_warnings"
        case doAndDont = "do_and_dont"
        case sourceRefs = "source_refs"
    }
}

extension Place: FetchableRecord, PersistableRecord {
    static let databaseTableName = "places"

    init(row: Row) throws {
        id = row["id"]
        name = row["name"]
        aliases = Self.decodeJSONArray(row["aliases"])
        regionId = row["region_id"]
        category = row["category"]
        shortDescription = row["short_description"]
        longDescription = row["long_description"]
        reviewedAt = row["reviewed_at"]
        status = row["status"]
        confidence = row["confidence"]
        touristTrapLevel = row["tourist_trap_level"]
        whyRecommended = Self.decodeJSONArray(row["why_recommended"])
        neighborhood = row["neighborhood"]
        address = row["address"]
        lat = row["lat"]
        lng = row["lng"]
        hoursText = row["hours_text"]
        hoursWeekly = Self.decodeJSONArray(row["hours_weekly"])
        hoursVerifiedAt = row["hours_verified_at"]
        feesMinMad = row["fees_min_mad"]
        feesMaxMad = row["fees_max_mad"]
        expectedCostMinMad = row["expected_cost_min_mad"]
        expectedCostMaxMad = row["expected_cost_max_mad"]
        visitMinMinutes = row["visit_min_minutes"]
        visitMaxMinutes = row["visit_max_minutes"]
        bestTimeToGo = row["best_time_to_go"]
        bestTimeWindows = Self.decodeJSONArray(row["best_time_windows"])
        tags = Self.decodeJSONArray(row["tags"])
        localTips = Self.decodeJSONArray(row["local_tips"])
        scamWarnings = Self.decodeJSONArray(row["scam_warnings"])
        doAndDont = Self.decodeJSONArray(row["do_and_dont"])
        images = Self.decodeJSONArray(row["images"])
        sourceRefs = Self.decodeJSONArray(row["source_refs"])
    }
}

// MARK: - PriceCard

/// A price reference card for common goods/services
struct PriceCard: Codable, Identifiable, Sendable {
    let id: String
    let title: String
    let category: String?
    let unit: String?
    let volatility: String?
    let confidence: String?
    let expectedCostMinMad: Int
    let expectedCostMaxMad: Int
    let expectedCostNotes: String?
    let expectedCostUpdatedAt: String?
    let provenanceNote: String?
    let whatInfluencesPrice: [String]?
    let inclusionsChecklist: [String]?
    let negotiationScripts: [NegotiationScript]?
    let redFlags: [String]?
    let whatToDoInstead: [String]?
    let contextModifiers: [ContextModifier]?
    let fairnessLowMultiplier: Double?
    let fairnessHighMultiplier: Double?
    let sourceRefs: [Int]?

    enum CodingKeys: String, CodingKey {
        case id, title, category, unit, volatility, confidence
        case expectedCostMinMad = "expected_cost_min_mad"
        case expectedCostMaxMad = "expected_cost_max_mad"
        case expectedCostNotes = "expected_cost_notes"
        case expectedCostUpdatedAt = "expected_cost_updated_at"
        case provenanceNote = "provenance_note"
        case whatInfluencesPrice = "what_influences_price"
        case inclusionsChecklist = "inclusions_checklist"
        case negotiationScripts = "negotiation_scripts"
        case redFlags = "red_flags"
        case whatToDoInstead = "what_to_do_instead"
        case contextModifiers = "context_modifiers"
        case fairnessLowMultiplier = "fairness_low_multiplier"
        case fairnessHighMultiplier = "fairness_high_multiplier"
        case sourceRefs = "source_refs"
    }
}

extension PriceCard: FetchableRecord, PersistableRecord {
    static let databaseTableName = "price_cards"

    init(row: Row) throws {
        id = row["id"]
        title = row["title"]
        category = row["category"]
        unit = row["unit"]
        volatility = row["volatility"]
        confidence = row["confidence"]
        expectedCostMinMad = row["expected_cost_min_mad"]
        expectedCostMaxMad = row["expected_cost_max_mad"]
        expectedCostNotes = row["expected_cost_notes"]
        expectedCostUpdatedAt = row["expected_cost_updated_at"]
        provenanceNote = row["provenance_note"]
        whatInfluencesPrice = Self.decodeJSONArray(row["what_influences_price"])
        inclusionsChecklist = Self.decodeJSONArray(row["inclusions_checklist"])
        negotiationScripts = Self.decodeJSON(row["negotiation_scripts"])
        redFlags = Self.decodeJSONArray(row["red_flags"])
        whatToDoInstead = Self.decodeJSONArray(row["what_to_do_instead"])
        contextModifiers = Self.decodeJSON(row["context_modifiers"])
        fairnessLowMultiplier = row["fairness_low_multiplier"]
        fairnessHighMultiplier = row["fairness_high_multiplier"]
        sourceRefs = Self.decodeJSONArray(row["source_refs"])
    }
}

/// Negotiation script for a price card
struct NegotiationScript: Codable, Sendable {
    let darijaLatin: String
    let english: String
    let darijaArabic: String?
    let french: String?

    enum CodingKeys: String, CodingKey {
        case darijaLatin = "darija_latin"
        case english
        case darijaArabic = "darija_arabic"
        case french
    }
}

/// Context modifier that affects pricing
struct ContextModifier: Codable, Identifiable, Sendable {
    let id: String
    let label: String
    let factorMin: Double?
    let factorMax: Double?
    let addMin: Double?
    let addMax: Double?
    let notes: String?

    enum CodingKeys: String, CodingKey {
        case id, label, notes
        case factorMin = "factor_min"
        case factorMax = "factor_max"
        case addMin = "add_min"
        case addMax = "add_max"
    }
}

// MARK: - Phrase

/// A Darija phrase for the phrasebook
struct Phrase: Codable, Identifiable, Sendable {
    let id: String
    let category: String?
    let arabic: String?
    let latin: String
    let english: String
    let audio: String?
    let verificationStatus: String?

    enum CodingKeys: String, CodingKey {
        case id, category, arabic, latin, english, audio
        case verificationStatus = "verification_status"
    }
}

extension Phrase: FetchableRecord, PersistableRecord {
    static let databaseTableName = "phrases"

    init(row: Row) throws {
        id = row["id"]
        category = row["category"]
        arabic = row["arabic"]
        latin = row["latin"]
        english = row["english"]
        audio = row["audio"]
        verificationStatus = row["verification_status"]
    }
}

// MARK: - Itinerary

/// A curated day itinerary
struct Itinerary: Codable, Identifiable, Sendable {
    let id: String
    let title: String
    let duration: String?
    let style: String?
    let steps: [ItineraryStep]?
    let sourceRefs: [Int]?

    enum CodingKeys: String, CodingKey {
        case id, title, duration, style, steps
        case sourceRefs = "source_refs"
    }
}

extension Itinerary: FetchableRecord, PersistableRecord {
    static let databaseTableName = "itineraries"

    init(row: Row) throws {
        id = row["id"]
        title = row["title"]
        duration = row["duration"]
        style = row["style"]
        steps = Self.decodeJSON(row["steps"])
        sourceRefs = Self.decodeJSONArray(row["source_refs"])
    }
}

/// A step in an itinerary
struct ItineraryStep: Codable, Sendable {
    let type: String
    let placeId: String?
    let activityId: String?
    let estimatedStopMinutes: Int?
    let routeHint: String?

    enum CodingKeys: String, CodingKey {
        case type
        case placeId = "place_id"
        case activityId = "activity_id"
        case estimatedStopMinutes = "estimated_stop_minutes"
        case routeHint = "route_hint"
    }
}

// MARK: - Tip

/// A practical travel tip
struct Tip: Codable, Identifiable, Sendable {
    let id: String
    let title: String
    let category: String?
    let summary: String?
    let actions: [String]?
    let severity: String?
    let updatedAt: String?
    let relatedPlaceIds: [String]?
    let relatedPriceCardIds: [String]?
    let sourceRefs: [Int]?

    enum CodingKeys: String, CodingKey {
        case id, title, category, summary, actions, severity
        case updatedAt = "updated_at"
        case relatedPlaceIds = "related_place_ids"
        case relatedPriceCardIds = "related_price_card_ids"
        case sourceRefs = "source_refs"
    }
}

extension Tip: FetchableRecord, PersistableRecord {
    static let databaseTableName = "tips"

    init(row: Row) throws {
        id = row["id"]
        title = row["title"]
        category = row["category"]
        summary = row["summary"]
        actions = Self.decodeJSONArray(row["actions"])
        severity = row["severity"]
        updatedAt = row["updated_at"]
        relatedPlaceIds = Self.decodeJSONArray(row["related_place_ids"])
        relatedPriceCardIds = Self.decodeJSONArray(row["related_price_card_ids"])
        sourceRefs = Self.decodeJSONArray(row["source_refs"])
    }
}

// MARK: - CultureArticle

/// A cultural insight article
struct CultureArticle: Codable, Identifiable, Sendable {
    let id: String
    let title: String
    let summary: String?
    let category: String?
    let doList: [String]?
    let dontList: [String]?
    let updatedAt: String?
    let sourceRefs: [Int]?

    enum CodingKeys: String, CodingKey {
        case id, title, summary, category
        case doList = "do"
        case dontList = "dont"
        case updatedAt = "updated_at"
        case sourceRefs = "source_refs"
    }
}

extension CultureArticle: FetchableRecord, PersistableRecord {
    static let databaseTableName = "culture"

    init(row: Row) throws {
        id = row["id"]
        title = row["title"]
        summary = row["summary"]
        category = row["category"]
        doList = Self.decodeJSONArray(row["do"])
        dontList = Self.decodeJSONArray(row["dont"])
        updatedAt = row["updated_at"]
        sourceRefs = Self.decodeJSONArray(row["source_refs"])
    }
}

// MARK: - Activity

/// A bookable activity or tour
struct Activity: Codable, Identifiable, Sendable {
    let id: String
    let title: String
    let category: String?
    let regionId: String?
    let durationMinMinutes: Int?
    let durationMaxMinutes: Int?
    let pickupAvailable: Bool?
    let typicalPriceMinMad: Int?
    let typicalPriceMaxMad: Int?
    let ratingSignal: String?
    let reviewCountSignal: String?
    let bestTimeWindows: [String]?
    let tags: [String]?
    let notes: String?
    let sourceRefs: [Int]?

    enum CodingKeys: String, CodingKey {
        case id, title, category, tags, notes
        case regionId = "region_id"
        case durationMinMinutes = "duration_min_minutes"
        case durationMaxMinutes = "duration_max_minutes"
        case pickupAvailable = "pickup_available"
        case typicalPriceMinMad = "typical_price_min_mad"
        case typicalPriceMaxMad = "typical_price_max_mad"
        case ratingSignal = "rating_signal"
        case reviewCountSignal = "review_count_signal"
        case bestTimeWindows = "best_time_windows"
        case sourceRefs = "source_refs"
    }
}

extension Activity: FetchableRecord, PersistableRecord {
    static let databaseTableName = "activities"

    init(row: Row) throws {
        id = row["id"]
        title = row["title"]
        category = row["category"]
        regionId = row["region_id"]
        durationMinMinutes = row["duration_min_minutes"]
        durationMaxMinutes = row["duration_max_minutes"]
        pickupAvailable = row["pickup_available"]
        typicalPriceMinMad = row["typical_price_min_mad"]
        typicalPriceMaxMad = row["typical_price_max_mad"]
        ratingSignal = row["rating_signal"]
        reviewCountSignal = row["review_count_signal"]
        bestTimeWindows = Self.decodeJSONArray(row["best_time_windows"])
        tags = Self.decodeJSONArray(row["tags"])
        notes = row["notes"]
        sourceRefs = Self.decodeJSONArray(row["source_refs"])
    }
}

// MARK: - Event

/// A time-bound event (concert, festival, etc.)
struct Event: Codable, Identifiable, Sendable {
    let id: String
    let title: String
    let category: String?
    let city: String?
    let venue: String?
    let startAt: String?
    let endAt: String?
    let priceMinMad: Int?
    let priceMaxMad: Int?
    let ticketStatus: String?
    let capturedAt: String?
    let sourceUrl: String?
    let sourceRefs: [Int]?

    enum CodingKeys: String, CodingKey {
        case id, title, category, city, venue
        case startAt = "start_at"
        case endAt = "end_at"
        case priceMinMad = "price_min_mad"
        case priceMaxMad = "price_max_mad"
        case ticketStatus = "ticket_status"
        case capturedAt = "captured_at"
        case sourceUrl = "source_url"
        case sourceRefs = "source_refs"
    }
}

extension Event: FetchableRecord, PersistableRecord {
    static let databaseTableName = "events"

    init(row: Row) throws {
        id = row["id"]
        title = row["title"]
        category = row["category"]
        city = row["city"]
        venue = row["venue"]
        startAt = row["start_at"]
        endAt = row["end_at"]
        priceMinMad = row["price_min_mad"]
        priceMaxMad = row["price_max_mad"]
        ticketStatus = row["ticket_status"]
        capturedAt = row["captured_at"]
        sourceUrl = row["source_url"]
        sourceRefs = Self.decodeJSONArray(row["source_refs"])
    }
}

// MARK: - JSON Helpers

extension FetchableRecord {
    /// Decode a JSON array from a TEXT column
    static func decodeJSONArray<T: Decodable>(_ value: String?) -> [T]? {
        guard let jsonString = value,
              let data = jsonString.data(using: .utf8) else {
            return nil
        }
        return try? JSONDecoder().decode([T].self, from: data)
    }

    /// Decode a JSON object from a TEXT column
    static func decodeJSON<T: Decodable>(_ value: String?) -> T? {
        guard let jsonString = value,
              let data = jsonString.data(using: .utf8) else {
            return nil
        }
        return try? JSONDecoder().decode(T.self, from: data)
    }
}

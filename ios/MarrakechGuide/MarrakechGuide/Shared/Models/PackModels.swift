import Foundation

// MARK: - Pack Types

/// Types of downloadable content packs.
enum PackType: String, Codable, Sendable {
    case medinaMap = "medina-map"
    case guelizMap = "gueliz-map"
    case audioPhrases = "audio-phrases"
    case highResImages = "hires-images"
}

/// Current status of a content pack download.
enum PackStatus: String, Codable, Sendable {
    /// Not downloaded, available for download
    case notDownloaded
    /// Queued for download
    case queued
    /// Currently downloading
    case downloading
    /// Download paused
    case paused
    /// Verifying downloaded content
    case verifying
    /// Installing downloaded content
    case installing
    /// Fully installed and ready to use
    case installed
    /// Update available for installed pack
    case updateAvailable
    /// Download or install failed
    case failed
}

// MARK: - Content Pack

/// A downloadable content pack.
struct ContentPack: Identifiable, Codable, Sendable {
    let id: String
    let type: PackType
    let displayName: String
    let description: String
    let version: String
    let sizeBytes: Int64
    let sha256: String
    let downloadUrl: String
    let minAppVersion: String?
    let dependencies: [String]?

    /// Formatted size for display
    var formattedSize: String {
        ByteCountFormatter.string(fromByteCount: sizeBytes, countStyle: .file)
    }
}

// MARK: - Pack State

/// State of a pack download/installation.
struct PackState: Sendable {
    let packId: String
    var status: PackStatus
    var downloadedBytes: Int64 = 0
    var totalBytes: Int64 = 0
    var errorMessage: String?
    var installedVersion: String?
    var installedAt: Date?

    /// Download progress from 0.0 to 1.0
    var progress: Double {
        guard totalBytes > 0 else { return 0 }
        return Double(downloadedBytes) / Double(totalBytes)
    }

    /// Progress percentage for display
    var progressPercent: Int {
        Int(progress * 100)
    }

    /// Check if pack is currently active (downloading or installing)
    var isActive: Bool {
        [.queued, .downloading, .verifying, .installing].contains(status)
    }

    /// Check if pack can be resumed
    var canResume: Bool {
        status == .paused && downloadedBytes > 0
    }
}

// MARK: - Pack Manifest

/// Manifest containing all available packs.
struct PackManifest: Codable, Sendable {
    let version: String
    let packs: [ContentPack]
    let updatedAt: String
}

// MARK: - Download Result

/// Result of a download operation.
enum DownloadResult: Sendable {
    case success(packId: String)
    case progress(packId: String, downloadedBytes: Int64, totalBytes: Int64)
    case error(packId: String, error: DownloadError)
}

// MARK: - Download Error

/// Errors that can occur during download operations.
enum DownloadError: Error, LocalizedError, Sendable {
    case networkUnavailable
    case insufficientStorage
    case verificationFailed
    case installationFailed
    case httpError(code: Int)
    case unknown(message: String)

    var errorDescription: String? {
        switch self {
        case .networkUnavailable:
            return "Network connection is unavailable"
        case .insufficientStorage:
            return "Not enough storage space available"
        case .verificationFailed:
            return "Downloaded file verification failed"
        case .installationFailed:
            return "Failed to install the content pack"
        case .httpError(let code):
            return "Download failed with HTTP error \(code)"
        case .unknown(let message):
            return message
        }
    }
}

// MARK: - Download Preferences

/// User preferences for downloads.
struct DownloadPreferences: Codable, Sendable {
    var wifiOnly: Bool = true
    var autoUpdate: Bool = false
}

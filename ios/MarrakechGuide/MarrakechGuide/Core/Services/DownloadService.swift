import CommonCrypto
import Foundation
import os.log

/// Service for managing content pack downloads.
///
/// Features:
/// - Resumable downloads with Range header support
/// - SHA256 verification after download
/// - Atomic installation (temp -> verify -> move)
/// - Disk space checks before download
/// - Wi-Fi only option
public protocol DownloadService: AnyObject, Sendable {
    /// Current state of all packs
    var packStates: [String: PackState] { get async }

    /// User download preferences
    var preferences: DownloadPreferences { get async }

    /// Available packs from manifest
    var availablePacks: [ContentPack] { get async }

    /// Start downloading a pack
    @MainActor
    func startDownload(packId: String) async -> DownloadResult

    /// Pause an active download
    @MainActor
    func pauseDownload(packId: String) async

    /// Resume a paused download
    @MainActor
    func resumeDownload(packId: String) async -> DownloadResult

    /// Cancel a download and remove partial data
    @MainActor
    func cancelDownload(packId: String) async

    /// Remove an installed pack
    @MainActor
    func removePack(packId: String) async

    /// Check for pack updates
    @MainActor
    func checkForUpdates() async

    /// Update download preferences
    @MainActor
    func updatePreferences(_ preferences: DownloadPreferences)

    /// Get available disk space in bytes
    func getAvailableSpace() -> Int64

    /// Check if network is available (respecting Wi-Fi only preference)
    func isNetworkAvailable() async -> Bool
}

/// Implementation of DownloadService.
public final class DownloadServiceImpl: DownloadService, @unchecked Sendable {

    // MARK: - Singleton

    public static let shared = DownloadServiceImpl()

    // MARK: - Private Properties

    private let logger = Logger(subsystem: "com.marrakechguide", category: "DownloadService")
    private let lock = NSLock()

    private struct TrackedDownloadTask {
        let sessionId: UUID
        let task: URLSessionDownloadTask
    }

    private var _packStates: [String: PackState] = [:]
    private var _preferences = DownloadPreferences()
    private var _availablePacks: [ContentPack] = []
    private var activeDownloads: Set<String> = []
    private var activeDownloadSessions: [String: UUID] = [:]
    private var removePackIntents: Set<String> = []
    private var downloadTasks: [String: TrackedDownloadTask] = [:]
#if DEBUG
    private var testNetworkAvailabilityOverride: Bool?
    private var testAvailableSpaceOverride: Int64?
    private var testPackStateObserver: ((String, PackState) -> Void)?
    private var testTemporaryCleanupDelayNanoseconds: UInt64?
#endif

    private lazy var packsDirectory: URL = {
        let paths = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)
        let packsDir = paths[0].appendingPathComponent("packs", isDirectory: true)
        try? FileManager.default.createDirectory(at: packsDir, withIntermediateDirectories: true)
        return packsDir
    }()

    private lazy var tempDirectory: URL = {
        let tempDir = FileManager.default.temporaryDirectory.appendingPathComponent("pack_downloads", isDirectory: true)
        try? FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
        return tempDir
    }()

    // MARK: - Public Properties

    public var packStates: [String: PackState] {
        get async {
            lock.lock()
            defer { lock.unlock() }
            return _packStates
        }
    }

    public var preferences: DownloadPreferences {
        get async {
            lock.lock()
            defer { lock.unlock() }
            return _preferences
        }
    }

    public var availablePacks: [ContentPack] {
        get async {
            lock.lock()
            defer { lock.unlock() }
            return _availablePacks
        }
    }

    // MARK: - Initialization

    private init() {
        _availablePacks = Self.defaultPacks()
        loadInstalledPacks()
    }

    // MARK: - Public Methods

    @MainActor
    public func startDownload(packId: String) async -> DownloadResult {
        removePackIntents.remove(packId)
        guard let pack = _availablePacks.first(where: { $0.id == packId }) else {
            return .error(packId: packId, error: .unknown(message: "Pack not found"))
        }
        guard pack.sizeBytes > 0 else {
            return .error(packId: packId, error: .unknown(message: "Invalid pack size"))
        }

        // Check if already downloading
        if activeDownloads.contains(packId) {
            logger.debug("Download already in progress for \(packId)")
            return .error(packId: packId, error: .unknown(message: "Download already in progress"))
        }

        let hasCompleteTempFile = pack.sizeBytes > 0 && temporaryPackFileSize(packId: packId) >= pack.sizeBytes
        if hasCompleteTempFile {
            // Finalizing a complete temp file still needs space for staged install copy.
            guard getAvailableSpace() >= pack.sizeBytes else {
                return .error(packId: packId, error: .insufficientStorage)
            }
        } else {
            // Check network
            guard await isNetworkAvailable() else {
                return .error(packId: packId, error: .networkUnavailable)
            }

            // Check disk space (need ~2x for temp + final) with overflow-safe math.
            guard let requiredBytes = requiredDownloadSpaceBytes(forPackSizeBytes: pack.sizeBytes),
                  getAvailableSpace() >= requiredBytes else {
                return .error(packId: packId, error: .insufficientStorage)
            }
        }

        return await performDownload(pack: pack, resumeFrom: 0)
    }

    @MainActor
    public func pauseDownload(packId: String) async {
        let hadActiveDownload = activeDownloads.remove(packId) != nil
        activeDownloadSessions.removeValue(forKey: packId)
        let task = downloadTasks.removeValue(forKey: packId)
        task?.task.cancel(byProducingResumeData: { _ in })
        let hasActivePackState: Bool = {
            lock.lock()
            defer { lock.unlock() }
            return _packStates[packId]?.isActive ?? false
        }()
        guard hadActiveDownload || hasActivePackState else {
            logger.debug("Pause requested for non-active download: \(packId)")
            return
        }
        updatePackState(packId: packId) { state in
            var newState = state
            newState.status = .paused
            return newState
        }
        logger.info("Paused download: \(packId)")
    }

    @MainActor
    public func resumeDownload(packId: String) async -> DownloadResult {
        removePackIntents.remove(packId)
        guard let state = _packStates[packId] else {
            return .error(packId: packId, error: .unknown(message: "Pack state not found"))
        }
        guard let pack = _availablePacks.first(where: { $0.id == packId }) else {
            return .error(packId: packId, error: .unknown(message: "Pack not found"))
        }
        guard pack.sizeBytes > 0 else {
            return .error(packId: packId, error: .unknown(message: "Invalid pack size"))
        }
        let existingTempBytes = temporaryPackFileSize(packId: packId)
        let canResume = state.canResume || (state.status == .paused && existingTempBytes > 0)
        guard canResume else {
            return await startDownload(packId: packId)
        }

        let hasCompleteTempFile = pack.sizeBytes > 0 && existingTempBytes >= pack.sizeBytes
        if hasCompleteTempFile {
            // Finalizing a complete temp file still needs space for staged install copy.
            guard getAvailableSpace() >= pack.sizeBytes else {
                return .error(packId: packId, error: .insufficientStorage)
            }
        } else {
            guard await isNetworkAvailable() else {
                return .error(packId: packId, error: .networkUnavailable)
            }
            guard let requiredBytes = requiredResumeDownloadSpaceBytes(
                forPackSizeBytes: pack.sizeBytes,
                existingTempBytes: existingTempBytes
            ),
                  getAvailableSpace() >= requiredBytes else {
                return .error(packId: packId, error: .insufficientStorage)
            }
        }
        let resumeFrom = max(state.downloadedBytes, existingTempBytes)
        return await performDownload(pack: pack, resumeFrom: resumeFrom)
    }

    @MainActor
    public func cancelDownload(packId: String) async {
        removePackIntents.remove(packId)
        activeDownloads.remove(packId)
        activeDownloadSessions.removeValue(forKey: packId)
        downloadTasks[packId]?.task.cancel()
        downloadTasks.removeValue(forKey: packId)

        // Clean up temp files
        let tempFile = tempDirectory.appendingPathComponent("\(packId).tmp")
        try? FileManager.default.removeItem(at: tempFile)

        updatePackState(packId: packId) { _ in
            PackState(packId: packId, status: .notDownloaded)
        }
        await restoreInstalledStateFromDiskIfPresent(packId: packId)
        logger.info("Cancelled download: \(packId)")
    }

    @MainActor
    public func removePack(packId: String) async {
        removePackIntents.insert(packId)
        activeDownloads.remove(packId)
        activeDownloadSessions.removeValue(forKey: packId)
        downloadTasks[packId]?.task.cancel()
        downloadTasks.removeValue(forKey: packId)

        let tempFile = tempDirectory.appendingPathComponent("\(packId).tmp")
        try? FileManager.default.removeItem(at: tempFile)

        let packDir = packsDirectory.appendingPathComponent(packId)
        try? FileManager.default.removeItem(at: packDir)

        updatePackState(packId: packId) { _ in
            PackState(packId: packId, status: .notDownloaded)
        }
        logger.info("Removed pack: \(packId)")
    }

    @MainActor
    public func checkForUpdates() async {
        // In production, this would fetch from a remote manifest
        // For now, check installed versions against available packs
        for pack in _availablePacks {
            guard let state = _packStates[pack.id],
                  !state.isActive,
                  let installedVersion = state.installedVersion,
                  installedVersion != pack.version else {
                continue
            }

            // If a paused partial download targeted an older update payload, discard stale bytes.
            if state.status == .paused, state.downloadedBytes > 0 {
                await removeTemporaryPackFileIfPresent(
                    packId: pack.id,
                    expectedPausedInstalledVersionForCleanup: installedVersion,
                    latestAvailableVersionForCleanup: pack.version
                )
            }

            updatePackState(packId: pack.id) { state in
                guard
                    !state.isActive,
                    let currentInstalledVersion = state.installedVersion,
                    currentInstalledVersion != pack.version
                else {
                    return state
                }
                var newState = state
                newState.status = .updateAvailable
                // Update-available means "installed but no active update payload".
                // Keep totalBytes for UI sizing, reset downloaded progress to zero.
                newState.downloadedBytes = 0
                newState.totalBytes = pack.sizeBytes
                newState.errorMessage = nil
                return newState
            }
        }
        logger.info("Checked for updates")
    }

    @MainActor
    public func updatePreferences(_ preferences: DownloadPreferences) {
        lock.lock()
        _preferences = preferences
        lock.unlock()
    }

    public func getAvailableSpace() -> Int64 {
#if DEBUG
        lock.lock()
        let override = testAvailableSpaceOverride
        lock.unlock()
        if let override {
            return override
        }
#endif
        do {
            let paths = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)
            let attributes = try FileManager.default.attributesOfFileSystem(forPath: paths[0].path)
            if let freeSpace = attributes[.systemFreeSize] as? Int64 {
                return freeSpace
            }
        } catch {
            logger.error("Failed to get available space: \(error.localizedDescription)")
        }
        return 0
    }

    public func isNetworkAvailable() async -> Bool {
#if DEBUG
        lock.lock()
        let override = testNetworkAvailabilityOverride
        lock.unlock()
        if let override {
            return override
        }
#endif
        // In production, use NWPathMonitor for proper network checking
        // For now, return true as a placeholder
        return true
    }

    // MARK: - Private Methods

    @MainActor
    private func performDownload(pack: ContentPack, resumeFrom: Int64) async -> DownloadResult {
        let insertionResult = activeDownloads.insert(pack.id)
        guard insertionResult.inserted else {
            logger.debug("Download already in progress for \(pack.id)")
            return .error(packId: pack.id, error: .unknown(message: "Download already in progress"))
        }
        let sessionId = UUID()
        activeDownloadSessions[pack.id] = sessionId
        let tempFile = tempDirectory.appendingPathComponent("\(pack.id).tmp")
        let packsDirectory = self.packsDirectory
        let hadExistingInstall = Self.hasInstalledPackOnDisk(packId: pack.id, packsDirectory: packsDirectory)
        let existingTempFileBytes = temporaryPackFileSize(packId: pack.id)
        let canAttemptResume = resumeFrom > 0 && existingTempFileBytes > 0
        // Trust on-disk temp size as the canonical resume point; stale state values can be lower.
        let effectiveResumeFrom = canAttemptResume ? existingTempFileBytes : 0
        let initialDownloadedBytes = clampDownloadedBytes(effectiveResumeFrom, totalBytes: pack.sizeBytes)

        updatePackState(packId: pack.id) { _ in
            PackState(
                packId: pack.id,
                status: .downloading,
                downloadedBytes: initialDownloadedBytes,
                totalBytes: pack.sizeBytes
            )
        }
        if existingTempFileBytes >= pack.sizeBytes && existingTempFileBytes > 0 {
            logger.info("Resuming from complete temp file for \(pack.id); skipping network request")
            return await finalizeDownloadedPack(
                pack: pack,
                tempFile: tempFile,
                hadExistingInstall: hadExistingInstall,
                sessionId: sessionId
            )
        }

        do {
            guard let url = URL(string: pack.downloadUrl) else {
                throw DownloadError.unknown(message: "Invalid download URL")
            }

            var request = URLRequest(url: url)
            request.timeoutInterval = 30

            // Set up resumable download
            if canAttemptResume {
                request.setValue("bytes=\(effectiveResumeFrom)-", forHTTPHeaderField: "Range")
            }

            // Perform download using a tracked task so pause/cancel can interrupt in-flight work.
            let (tempURL, response) = try await performTrackedDownload(
                request: request,
                packId: pack.id,
                sessionId: sessionId
            )

            guard let httpResponse = response as? HTTPURLResponse else {
                throw DownloadError.unknown(message: "Invalid response")
            }

            guard [200, 206].contains(httpResponse.statusCode) else {
                throw DownloadError.httpError(code: httpResponse.statusCode)
            }

            guard isSessionActive(packId: pack.id, sessionId: sessionId) else {
                if hasDifferentActiveSession(packId: pack.id, sessionId: sessionId) {
                    return passiveProgress(packId: pack.id, resumeFrom: effectiveResumeFrom, totalBytes: pack.sizeBytes)
                }
                return await interruptionProgress(packId: pack.id, resumeFrom: effectiveResumeFrom, totalBytes: pack.sizeBytes)
            }

            // Persist download result. Partial responses append to existing temp file.
            try await performBlockingFileIO {
                try Self.persistDownloadedChunk(
                    from: tempURL,
                    to: tempFile,
                    responseCode: httpResponse.statusCode,
                    resumeFrom: effectiveResumeFrom
                )
            }

            // Check if download was cancelled
            guard isSessionActive(packId: pack.id, sessionId: sessionId) else {
                if hasDifferentActiveSession(packId: pack.id, sessionId: sessionId) {
                    return passiveProgress(packId: pack.id, resumeFrom: effectiveResumeFrom, totalBytes: pack.sizeBytes)
                }
                return await interruptionProgress(packId: pack.id, resumeFrom: effectiveResumeFrom, totalBytes: pack.sizeBytes)
            }

            return await finalizeDownloadedPack(
                pack: pack,
                tempFile: tempFile,
                hadExistingInstall: hadExistingInstall,
                sessionId: sessionId
            )

        } catch {
            if !isSessionActive(packId: pack.id, sessionId: sessionId) {
                if hasDifferentActiveSession(packId: pack.id, sessionId: sessionId) {
                    return passiveProgress(packId: pack.id, resumeFrom: effectiveResumeFrom, totalBytes: pack.sizeBytes)
                }
                return await interruptionProgress(packId: pack.id, resumeFrom: effectiveResumeFrom, totalBytes: pack.sizeBytes)
            }
            if isDownloadCancelledError(error) {
                clearSessionStateIfMatching(packId: pack.id, sessionId: sessionId)
                return await interruptionProgress(packId: pack.id, resumeFrom: effectiveResumeFrom, totalBytes: pack.sizeBytes)
            }

            logger.error("Download failed for \(pack.id): \(error.localizedDescription)")
            clearSessionStateIfMatching(packId: pack.id, sessionId: sessionId)
            if hadExistingInstall {
                let restored = await applyExistingInstallStateAfterFailure(pack: pack, errorMessage: error.localizedDescription)
                if !restored {
                    setFailedState(packId: pack.id, errorMessage: error.localizedDescription)
                }
            } else {
                setFailedState(packId: pack.id, errorMessage: error.localizedDescription)
            }
            if let downloadError = error as? DownloadError {
                return .error(packId: pack.id, error: downloadError)
            }
            return .error(packId: pack.id, error: .unknown(message: error.localizedDescription))
        }
    }

    @MainActor
    private func finalizeDownloadedPack(
        pack: ContentPack,
        tempFile: URL,
        hadExistingInstall: Bool,
        sessionId: UUID
    ) async -> DownloadResult {
        let packsDirectory = self.packsDirectory
        guard isSessionActive(packId: pack.id, sessionId: sessionId) else {
            if hasDifferentActiveSession(packId: pack.id, sessionId: sessionId) {
                return passiveProgress(packId: pack.id, resumeFrom: temporaryPackFileSize(packId: pack.id), totalBytes: pack.sizeBytes)
            }
            return await interruptionProgress(packId: pack.id, resumeFrom: temporaryPackFileSize(packId: pack.id), totalBytes: pack.sizeBytes)
        }

        // Verify checksum
        updatePackState(packId: pack.id) { state in
            var newState = state
            newState.status = .verifying
            return newState
        }

        let checksumValid = (try? await performBlockingFileIO {
            Self.verifyChecksum(file: tempFile, expectedSha256: pack.sha256)
        }) ?? false
        guard checksumValid else {
            if !isSessionActive(packId: pack.id, sessionId: sessionId) {
                if hasDifferentActiveSession(packId: pack.id, sessionId: sessionId) {
                    return passiveProgress(packId: pack.id, resumeFrom: temporaryPackFileSize(packId: pack.id), totalBytes: pack.sizeBytes)
                }
                return await interruptionProgress(packId: pack.id, resumeFrom: temporaryPackFileSize(packId: pack.id), totalBytes: pack.sizeBytes)
            }
            try? FileManager.default.removeItem(at: tempFile)
            clearSessionStateIfMatching(packId: pack.id, sessionId: sessionId)
            if hadExistingInstall {
                let restored = await applyExistingInstallStateAfterFailure(pack: pack, errorMessage: "Verification failed")
                if !restored {
                    setFailedState(packId: pack.id, errorMessage: "Verification failed")
                }
            } else {
                setFailedState(packId: pack.id, errorMessage: "Verification failed")
            }
            return .error(packId: pack.id, error: .verificationFailed)
        }

        if !isSessionActive(packId: pack.id, sessionId: sessionId) {
            if hasDifferentActiveSession(packId: pack.id, sessionId: sessionId) {
                return passiveProgress(packId: pack.id, resumeFrom: temporaryPackFileSize(packId: pack.id), totalBytes: pack.sizeBytes)
            }
            return await interruptionProgress(packId: pack.id, resumeFrom: temporaryPackFileSize(packId: pack.id), totalBytes: pack.sizeBytes)
        }

        // Install pack
        updatePackState(packId: pack.id) { state in
            var newState = state
            newState.status = .installing
            return newState
        }

        let success = (try? await performBlockingFileIO {
            Self.installPack(pack: pack, tempFile: tempFile, packsDirectory: packsDirectory)
        }) ?? false

        if success {
            if !isSessionActive(packId: pack.id, sessionId: sessionId) {
                if hasDifferentActiveSession(packId: pack.id, sessionId: sessionId) {
                    return passiveProgress(packId: pack.id, resumeFrom: temporaryPackFileSize(packId: pack.id), totalBytes: pack.sizeBytes)
                }
                let shouldCleanupInstalledArtifacts = !hadExistingInstall || removePackIntents.contains(pack.id)
                if shouldCleanupInstalledArtifacts {
                    Self.cleanupInstalledPackArtifacts(packId: pack.id, packsDirectory: packsDirectory)
                    return await interruptionProgress(packId: pack.id, resumeFrom: temporaryPackFileSize(packId: pack.id), totalBytes: pack.sizeBytes)
                }
                // Installation already completed for a previously installed pack.
                // Reflect authoritative on-disk state instead of leaving a paused state.
                try? FileManager.default.removeItem(at: tempFile)
                await restoreInstalledStateFromDiskIfPresent(packId: pack.id)
                return passiveProgress(packId: pack.id, resumeFrom: 0, totalBytes: pack.sizeBytes)
            }
            try? FileManager.default.removeItem(at: tempFile)
            clearSessionStateIfMatching(packId: pack.id, sessionId: sessionId)
            updatePackState(packId: pack.id) { _ in
                PackState(
                    packId: pack.id,
                    status: .installed,
                    downloadedBytes: pack.sizeBytes,
                    totalBytes: pack.sizeBytes,
                    installedVersion: pack.version,
                    installedAt: Date()
                )
            }
            logger.info("Successfully installed pack: \(pack.id)")
            return .success(packId: pack.id)
        }

        if !isSessionActive(packId: pack.id, sessionId: sessionId) {
            if hasDifferentActiveSession(packId: pack.id, sessionId: sessionId) {
                return passiveProgress(packId: pack.id, resumeFrom: temporaryPackFileSize(packId: pack.id), totalBytes: pack.sizeBytes)
            }
            let shouldCleanupInstalledArtifacts = !hadExistingInstall || removePackIntents.contains(pack.id)
            if shouldCleanupInstalledArtifacts {
                Self.cleanupInstalledPackArtifacts(packId: pack.id, packsDirectory: packsDirectory)
                return await interruptionProgress(packId: pack.id, resumeFrom: temporaryPackFileSize(packId: pack.id), totalBytes: pack.sizeBytes)
            }
            // Keep installed/update state authoritative for interrupted update attempts.
            await restoreInstalledStateFromDiskIfPresent(packId: pack.id)
            return passiveProgress(packId: pack.id, resumeFrom: 0, totalBytes: pack.sizeBytes)
        }
        try? FileManager.default.removeItem(at: tempFile)
        clearSessionStateIfMatching(packId: pack.id, sessionId: sessionId)
        if hadExistingInstall {
            let restored = await applyExistingInstallStateAfterFailure(pack: pack, errorMessage: "Installation failed")
            if !restored {
                setFailedState(packId: pack.id, errorMessage: "Installation failed")
            }
        } else {
            setFailedState(packId: pack.id, errorMessage: "Installation failed")
        }
        return .error(packId: pack.id, error: .installationFailed)
    }

    @MainActor
    private func performTrackedDownload(request: URLRequest, packId: String, sessionId: UUID) async throws -> (URL, URLResponse) {
        try await withCheckedThrowingContinuation { continuation in
            let task = URLSession.shared.downloadTask(with: request) { [weak self] tempURL, response, error in
                Task { @MainActor [weak self] in
                    if let trackedTask = self?.downloadTasks[packId], trackedTask.sessionId == sessionId {
                        self?.downloadTasks.removeValue(forKey: packId)
                    }
                }

                if let error {
                    continuation.resume(throwing: error)
                    return
                }

                guard let tempURL, let response else {
                    continuation.resume(throwing: DownloadError.unknown(message: "Missing download response"))
                    return
                }

                continuation.resume(returning: (tempURL, response))
            }

            downloadTasks[packId] = TrackedDownloadTask(sessionId: sessionId, task: task)
            task.resume()
        }
    }

    @MainActor
    private func isSessionActive(packId: String, sessionId: UUID) -> Bool {
        activeDownloads.contains(packId) && activeDownloadSessions[packId] == sessionId
    }

    @MainActor
    private func hasDifferentActiveSession(packId: String, sessionId: UUID) -> Bool {
        guard activeDownloads.contains(packId), let activeSessionId = activeDownloadSessions[packId] else {
            return false
        }
        return activeSessionId != sessionId
    }

    @MainActor
    private func clearSessionStateIfMatching(packId: String, sessionId: UUID) {
        guard activeDownloadSessions[packId] == sessionId else {
            return
        }
        activeDownloadSessions.removeValue(forKey: packId)
        activeDownloads.remove(packId)
        if let trackedTask = downloadTasks[packId], trackedTask.sessionId == sessionId {
            downloadTasks.removeValue(forKey: packId)
        }
    }

    @MainActor
    private func passiveProgress(packId: String, resumeFrom: Int64, totalBytes: Int64) -> DownloadResult {
        let rawDownloadedBytes = max(resumeFrom, temporaryPackFileSize(packId: packId))
        let downloadedBytes = clampDownloadedBytes(rawDownloadedBytes, totalBytes: totalBytes)
        return .progress(packId: packId, downloadedBytes: downloadedBytes, totalBytes: totalBytes)
    }

    private func isDownloadCancelledError(_ error: Error) -> Bool {
        if let urlError = error as? URLError {
            return urlError.code == .cancelled
        }
        let nsError = error as NSError
        return nsError.domain == NSURLErrorDomain && nsError.code == NSURLErrorCancelled
    }

    private func temporaryPackFileSize(packId: String) -> Int64 {
        let tempFile = tempDirectory.appendingPathComponent("\(packId).tmp")
        guard
            let attrs = try? FileManager.default.attributesOfItem(atPath: tempFile.path),
            let sizeNumber = attrs[.size] as? NSNumber
        else {
            return 0
        }
        return sizeNumber.int64Value
    }

    @MainActor
    private func interruptionProgress(packId: String, resumeFrom: Int64, totalBytes: Int64) async -> DownloadResult {
        let currentState = _packStates[packId]
        if currentState?.status == .notDownloaded {
            await removeTemporaryPackFileIfPresent(packId: packId)
            if !removePackIntents.contains(packId) {
                await restoreInstalledStateFromDiskIfPresent(packId: packId)
            }
            return .progress(packId: packId, downloadedBytes: 0, totalBytes: totalBytes)
        }

        let rawDownloadedBytes = max(resumeFrom, temporaryPackFileSize(packId: packId))
        let downloadedBytes = clampDownloadedBytes(rawDownloadedBytes, totalBytes: totalBytes)
        updateInterruptedStateIfNeeded(packId: packId, downloadedBytes: downloadedBytes, totalBytes: totalBytes)
        return .progress(packId: packId, downloadedBytes: downloadedBytes, totalBytes: totalBytes)
    }

    @MainActor
    private func removeTemporaryPackFileIfPresent(
        packId: String,
        expectedPausedInstalledVersionForCleanup: String? = nil,
        latestAvailableVersionForCleanup: String? = nil
    ) async {
        let tempFile = tempDirectory.appendingPathComponent("\(packId).tmp")
#if DEBUG
        lock.lock()
        let delayNanoseconds = testTemporaryCleanupDelayNanoseconds
        lock.unlock()
        if let delayNanoseconds, delayNanoseconds > 0 {
            try? await Task.sleep(nanoseconds: delayNanoseconds)
        }
#endif
        _ = try? await performBlockingFileIO { [self] in
            if let expectedPausedInstalledVersionForCleanup,
               let latestAvailableVersionForCleanup,
               !shouldDeleteStalePausedTempFile(
                packId: packId,
                expectedInstalledVersion: expectedPausedInstalledVersionForCleanup,
                latestAvailableVersion: latestAvailableVersionForCleanup
               ) {
                return
            }
            guard FileManager.default.fileExists(atPath: tempFile.path) else {
                return
            }
            try FileManager.default.removeItem(at: tempFile)
        }
    }

    private func shouldDeleteStalePausedTempFile(
        packId: String,
        expectedInstalledVersion: String,
        latestAvailableVersion: String
    ) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        guard let state = _packStates[packId] else {
            return false
        }
        guard
            state.status == .paused,
            state.downloadedBytes > 0,
            state.installedVersion == expectedInstalledVersion,
            expectedInstalledVersion != latestAvailableVersion
        else {
            return false
        }
        return true
    }

    @MainActor
    private func restoreInstalledStateFromDiskIfPresent(packId: String) async {
        guard let pack = _availablePacks.first(where: { $0.id == packId }) else {
            return
        }

        guard Self.hasInstalledPackOnDisk(packId: packId, packsDirectory: packsDirectory) else {
            return
        }
        let manifestURL = Self.manifestURL(for: packId, packsDirectory: packsDirectory)

        guard let metadata = try? await performBlockingFileIO({
            try Self.readInstalledManifestMetadata(manifestURL: manifestURL)
        }) else {
            logger.warning("Failed to parse installed pack manifest for \(packId)")
            return
        }
        let installedVersion = metadata.version ?? pack.version
        let resolvedStatus: PackStatus = installedVersion == pack.version ? .installed : .updateAvailable
        let resolvedDownloadedBytes: Int64 = resolvedStatus == .installed ? pack.sizeBytes : 0

        updatePackState(packId: packId) { _ in
            PackState(
                packId: packId,
                status: resolvedStatus,
                downloadedBytes: resolvedDownloadedBytes,
                totalBytes: pack.sizeBytes,
                installedVersion: installedVersion,
                installedAt: metadata.installedAt ?? Date()
            )
        }
    }

    @MainActor
    private func applyExistingInstallStateAfterFailure(pack: ContentPack, errorMessage: String) async -> Bool {
        guard Self.hasInstalledPackOnDisk(packId: pack.id, packsDirectory: packsDirectory) else {
            return false
        }

        let manifestURL = Self.manifestURL(for: pack.id, packsDirectory: packsDirectory)
        guard let metadata = try? await performBlockingFileIO({
            try Self.readInstalledManifestMetadata(manifestURL: manifestURL)
        }) else {
            logger.warning("Failed to parse installed pack manifest for \(pack.id) after download failure")
            return false
        }

        let installedVersion = metadata.version ?? pack.version
        let resolvedStatus: PackStatus = installedVersion == pack.version ? .installed : .updateAvailable
        let resolvedDownloadedBytes: Int64 = resolvedStatus == .installed ? pack.sizeBytes : 0

        updatePackState(packId: pack.id) { _ in
            PackState(
                packId: pack.id,
                status: resolvedStatus,
                downloadedBytes: resolvedDownloadedBytes,
                totalBytes: pack.sizeBytes,
                errorMessage: errorMessage,
                installedVersion: installedVersion,
                installedAt: metadata.installedAt ?? Date()
            )
        }
        return true
    }

    private func setFailedState(packId: String, errorMessage: String) {
        updatePackState(packId: packId) { state in
            var newState = state
            newState.status = .failed
            newState.errorMessage = errorMessage
            return newState
        }
    }

    private func requiredDownloadSpaceBytes(forPackSizeBytes packSizeBytes: Int64) -> Int64? {
        guard packSizeBytes > 0 else { return 0 }
        let (doubled, overflow) = packSizeBytes.multipliedReportingOverflow(by: 2)
        if overflow {
            return nil
        }
        return doubled
    }

    private func requiredResumeDownloadSpaceBytes(forPackSizeBytes packSizeBytes: Int64, existingTempBytes: Int64) -> Int64? {
        guard packSizeBytes > 0 else { return 0 }
        let clampedExistingBytes = min(max(existingTempBytes, 0), packSizeBytes)
        let remainingBytes = packSizeBytes - clampedExistingBytes
        let (requiredBytes, overflow) = packSizeBytes.addingReportingOverflow(remainingBytes)
        if overflow {
            return nil
        }
        return requiredBytes
    }

    private func updateInterruptedStateIfNeeded(packId: String, downloadedBytes: Int64, totalBytes: Int64) {
        let clampedDownloadedBytes = clampDownloadedBytes(downloadedBytes, totalBytes: totalBytes)
        updatePackState(packId: packId) { state in
            guard state.status == .paused || state.isActive else { return state }
            var newState = state
            newState.status = .paused
            newState.downloadedBytes = clampedDownloadedBytes
            newState.totalBytes = totalBytes
            return newState
        }
    }

    private func clampDownloadedBytes(_ downloadedBytes: Int64, totalBytes: Int64) -> Int64 {
        let nonNegativeDownloadedBytes = max(downloadedBytes, 0)
        guard totalBytes > 0 else {
            return nonNegativeDownloadedBytes
        }
        return min(nonNegativeDownloadedBytes, totalBytes)
    }

    private static func verifyChecksum(file: URL, expectedSha256: String) -> Bool {
        guard let fileHandle = try? FileHandle(forReadingFrom: file) else {
            return false
        }
        defer {
            try? fileHandle.close()
        }

        var hasher = SHA256Hasher()
        while true {
            guard let chunk = try? fileHandle.read(upToCount: 64 * 1024) else {
                return false
            }
            guard let chunk, !chunk.isEmpty else {
                break
            }
            hasher.update(data: chunk)
        }
        let actualHash = hasher.finalize()

        return actualHash.lowercased() == expectedSha256.lowercased()
    }

    private static func persistDownloadedChunk(
        from sourceURL: URL,
        to destinationURL: URL,
        responseCode: Int,
        resumeFrom: Int64
    ) throws {
        let isPartialResponse = resumeFrom > 0 && responseCode == 206

        if isPartialResponse, FileManager.default.fileExists(atPath: destinationURL.path) {
            try appendFile(from: sourceURL, to: destinationURL)
            try? FileManager.default.removeItem(at: sourceURL)
            return
        }

        try? FileManager.default.removeItem(at: destinationURL)
        try FileManager.default.moveItem(at: sourceURL, to: destinationURL)
    }

    private static func appendFile(from sourceURL: URL, to destinationURL: URL) throws {
        let reader = try FileHandle(forReadingFrom: sourceURL)
        let writer = try FileHandle(forWritingTo: destinationURL)
        defer {
            try? reader.close()
            try? writer.close()
        }

        _ = try writer.seekToEnd()

        while true {
            let chunk = try reader.read(upToCount: 64 * 1024) ?? Data()
            if chunk.isEmpty {
                break
            }
            try writer.write(contentsOf: chunk)
        }
    }

    private static func installPack(pack: ContentPack, tempFile: URL, packsDirectory: URL) -> Bool {
        do {
            let fileManager = FileManager.default
            let packDirectory = packsDirectory.appendingPathComponent(pack.id, isDirectory: true)
            let stagedDirectory = packsDirectory.appendingPathComponent("\(pack.id).staged.\(UUID().uuidString)", isDirectory: true)
            defer {
                try? fileManager.removeItem(at: stagedDirectory)
            }

            try fileManager.createDirectory(at: stagedDirectory, withIntermediateDirectories: true)

            let stagedDataFile = stagedDirectory.appendingPathComponent("data.pack")
            try fileManager.copyItem(at: tempFile, to: stagedDataFile)

            let manifest: [String: Any] = [
                "id": pack.id,
                "version": pack.version,
                "installedAt": Date().timeIntervalSince1970
            ]
            let manifestData = try JSONSerialization.data(withJSONObject: manifest)
            let stagedManifestFile = stagedDirectory.appendingPathComponent("manifest.json")
            try manifestData.write(to: stagedManifestFile, options: .atomic)

            if fileManager.fileExists(atPath: packDirectory.path) {
                _ = try fileManager.replaceItemAt(
                    packDirectory,
                    withItemAt: stagedDirectory,
                    backupItemName: nil,
                    options: [],
                    resultingItemURL: nil
                )
            } else {
                try fileManager.moveItem(at: stagedDirectory, to: packDirectory)
            }

            return true
        } catch {
            return false
        }
    }

    private static func manifestURL(for packId: String, packsDirectory: URL) -> URL {
        packsDirectory
            .appendingPathComponent(packId, isDirectory: true)
            .appendingPathComponent("manifest.json")
    }

    private static func dataPackURL(for packId: String, packsDirectory: URL) -> URL {
        packsDirectory
            .appendingPathComponent(packId, isDirectory: true)
            .appendingPathComponent("data.pack")
    }

    private static func hasInstalledPackOnDisk(packId: String, packsDirectory: URL) -> Bool {
        let manifestURL = manifestURL(for: packId, packsDirectory: packsDirectory)
        let dataURL = dataPackURL(for: packId, packsDirectory: packsDirectory)
        return FileManager.default.fileExists(atPath: manifestURL.path)
            && FileManager.default.fileExists(atPath: dataURL.path)
    }

    private static func cleanupInstalledPackArtifacts(packId: String, packsDirectory: URL) {
        let packDirectory = packsDirectory.appendingPathComponent(packId, isDirectory: true)
        try? FileManager.default.removeItem(at: packDirectory)
    }

    private struct InstalledManifestMetadata {
        let version: String?
        let installedAt: Date?
    }

    private enum InstalledManifestError: Error {
        case invalidFormat
        case invalidVersionType
        case invalidInstalledAtType
    }

    private static func readInstalledManifestMetadata(manifestURL: URL) throws -> InstalledManifestMetadata {
        let reader = try FileHandle(forReadingFrom: manifestURL)
        defer {
            try? reader.close()
        }

        var data = Data()
        while true {
            let chunk = try reader.read(upToCount: 4 * 1024) ?? Data()
            if chunk.isEmpty {
                break
            }
            data.append(chunk)
        }

        guard let manifest = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw InstalledManifestError.invalidFormat
        }

        let version: String?
        if let rawVersion = manifest["version"] {
            guard let typedVersion = rawVersion as? String else {
                throw InstalledManifestError.invalidVersionType
            }
            version = typedVersion
        } else {
            version = nil
        }

        let installedAtSeconds: TimeInterval?
        if let rawInstalledAt = manifest["installedAt"] {
            if let typedInstalledAt = rawInstalledAt as? TimeInterval {
                installedAtSeconds = typedInstalledAt
            } else if let typedInstalledAt = rawInstalledAt as? NSNumber {
                installedAtSeconds = typedInstalledAt.doubleValue
            } else {
                throw InstalledManifestError.invalidInstalledAtType
            }
        } else {
            installedAtSeconds = nil
        }
        let installedAt = installedAtSeconds.map { Date(timeIntervalSince1970: $0) }
        return InstalledManifestMetadata(version: version, installedAt: installedAt)
    }

    private func performBlockingFileIO<T>(_ operation: @escaping () throws -> T) async throws -> T {
        try await withCheckedThrowingContinuation { continuation in
            DispatchQueue.global(qos: .utility).async {
                do {
                    continuation.resume(returning: try operation())
                } catch {
                    continuation.resume(throwing: error)
                }
            }
        }
    }

    private func updatePackState(packId: String, update: (PackState) -> PackState) {
        lock.lock()
        let currentState = _packStates[packId] ?? PackState(packId: packId, status: .notDownloaded)
        let updatedState = update(currentState)
        _packStates[packId] = updatedState
#if DEBUG
        let stateObserver = testPackStateObserver
#endif
        lock.unlock()
#if DEBUG
        stateObserver?(packId, updatedState)
#endif
    }

    private func loadInstalledPacks() {
        for pack in _availablePacks {
            let packDir = packsDirectory.appendingPathComponent(pack.id)
            let manifestFile = packDir.appendingPathComponent("manifest.json")

            if Self.hasInstalledPackOnDisk(packId: pack.id, packsDirectory: packsDirectory) {
                // Pack is installed
                if let metadata = try? Self.readInstalledManifestMetadata(manifestURL: manifestFile) {
                    let installedVersion = metadata.version ?? pack.version
                    let resolvedStatus: PackStatus = installedVersion == pack.version ? .installed : .updateAvailable
                    let resolvedDownloadedBytes: Int64 = resolvedStatus == .installed ? pack.sizeBytes : 0
                    _packStates[pack.id] = PackState(
                        packId: pack.id,
                        status: resolvedStatus,
                        downloadedBytes: resolvedDownloadedBytes,
                        totalBytes: pack.sizeBytes,
                        installedVersion: installedVersion,
                        installedAt: metadata.installedAt
                    )
                } else {
                    logger.warning("Ignoring invalid manifest for installed pack: \(pack.id)")
                    _packStates[pack.id] = PackState(packId: pack.id, status: .notDownloaded)
                }
            } else {
                // Check for partial download
                let tempFile = tempDirectory.appendingPathComponent("\(pack.id).tmp")
                if FileManager.default.fileExists(atPath: tempFile.path),
                   let attrs = try? FileManager.default.attributesOfItem(atPath: tempFile.path),
                   let sizeNumber = attrs[.size] as? NSNumber {
                    let partialDownloadedBytes = clampDownloadedBytes(sizeNumber.int64Value, totalBytes: pack.sizeBytes)
                    _packStates[pack.id] = PackState(
                        packId: pack.id,
                        status: .paused,
                        downloadedBytes: partialDownloadedBytes,
                        totalBytes: pack.sizeBytes
                    )
                } else {
                    _packStates[pack.id] = PackState(packId: pack.id, status: .notDownloaded)
                }
            }
        }
    }

    private static func defaultPacks() -> [ContentPack] {
        [
            ContentPack(
                id: "medina-map",
                type: .medinaMap,
                displayName: "Medina Offline Map",
                description: "Offline map tiles and navigation for the old city. Essential for exploring the souks and riads.",
                version: "2026.02.01",
                sizeBytes: 52_428_800,
                sha256: "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                downloadUrl: "https://cdn.marrakechguide.app/packs/medina-map-2026.02.01.pack",
                minAppVersion: nil,
                dependencies: nil
            ),
            ContentPack(
                id: "gueliz-map",
                type: .guelizMap,
                displayName: "Gueliz & New City Map",
                description: "Offline map for the modern city district including restaurants, shops, and transit.",
                version: "2026.02.01",
                sizeBytes: 31_457_280,
                sha256: "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b856",
                downloadUrl: "https://cdn.marrakechguide.app/packs/gueliz-map-2026.02.01.pack",
                minAppVersion: nil,
                dependencies: nil
            ),
            ContentPack(
                id: "audio-phrases",
                type: .audioPhrases,
                displayName: "Audio Phrasebook",
                description: "Native speaker recordings for all Darija phrases. Hear correct pronunciation.",
                version: "2026.02.01",
                sizeBytes: 41_943_040,
                sha256: "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b857",
                downloadUrl: "https://cdn.marrakechguide.app/packs/audio-phrases-2026.02.01.pack",
                minAppVersion: nil,
                dependencies: nil
            ),
            ContentPack(
                id: "hires-images",
                type: .highResImages,
                displayName: "High-Resolution Photos",
                description: "Premium quality photographs of all places. Best visual experience.",
                version: "2026.02.01",
                sizeBytes: 104_857_600,
                sha256: "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b858",
                downloadUrl: "https://cdn.marrakechguide.app/packs/hires-images-2026.02.01.pack",
                minAppVersion: nil,
                dependencies: nil
            )
        ]
    }
}

#if DEBUG
extension DownloadServiceImpl {
    @MainActor
    func _testConfigureForDownloadTests(availablePacks: [ContentPack]) {
        downloadTasks.values.forEach { $0.task.cancel() }
        downloadTasks.removeAll()
        activeDownloads.removeAll()
        activeDownloadSessions.removeAll()
        removePackIntents.removeAll()
        let initialStates = Dictionary(
            uniqueKeysWithValues: availablePacks.map { pack in
                (pack.id, PackState(packId: pack.id, status: .notDownloaded))
            }
        )
        lock.lock()
        _preferences = DownloadPreferences()
        testNetworkAvailabilityOverride = nil
        testAvailableSpaceOverride = nil
        testPackStateObserver = nil
        testTemporaryCleanupDelayNanoseconds = nil
        _availablePacks = availablePacks
        _packStates = initialStates
        lock.unlock()
    }

    @MainActor
    func _testResetDownloadStateForTests() {
        downloadTasks.values.forEach { $0.task.cancel() }
        downloadTasks.removeAll()
        activeDownloads.removeAll()
        activeDownloadSessions.removeAll()
        removePackIntents.removeAll()
        let defaultPacks = Self.defaultPacks()
        let defaultStates = Dictionary(
            uniqueKeysWithValues: defaultPacks.map { pack in
                (pack.id, PackState(packId: pack.id, status: .notDownloaded))
            }
        )
        lock.lock()
        _preferences = DownloadPreferences()
        testNetworkAvailabilityOverride = nil
        testAvailableSpaceOverride = nil
        testPackStateObserver = nil
        testTemporaryCleanupDelayNanoseconds = nil
        _availablePacks = defaultPacks
        _packStates = defaultStates
        lock.unlock()
    }

    @MainActor
    func _testSetPackStateForTests(_ state: PackState) {
        lock.lock()
        _packStates[state.packId] = state
        lock.unlock()
    }

    @MainActor
    func _testSetNetworkAvailabilityForTests(_ isAvailable: Bool?) {
        lock.lock()
        testNetworkAvailabilityOverride = isAvailable
        lock.unlock()
    }

    @MainActor
    func _testSetAvailableSpaceForTests(_ availableBytes: Int64?) {
        lock.lock()
        testAvailableSpaceOverride = availableBytes
        lock.unlock()
    }

    @MainActor
    func _testSetPackStateObserverForTests(_ observer: ((String, PackState) -> Void)?) {
        lock.lock()
        testPackStateObserver = observer
        lock.unlock()
    }

    @MainActor
    func _testSetTemporaryCleanupDelayForTests(_ nanoseconds: UInt64?) {
        lock.lock()
        testTemporaryCleanupDelayNanoseconds = nanoseconds
        lock.unlock()
    }

    @MainActor
    func _testSetDownloadTaskForTests(packId: String, task: URLSessionDownloadTask?) {
        if let task {
            downloadTasks[packId] = TrackedDownloadTask(sessionId: UUID(), task: task)
        } else {
            downloadTasks.removeValue(forKey: packId)
        }
    }

    func _testPersistDownloadedChunk(
        from sourceURL: URL,
        to destinationURL: URL,
        responseCode: Int,
        resumeFrom: Int64
    ) throws {
        try Self.persistDownloadedChunk(
            from: sourceURL,
            to: destinationURL,
            responseCode: responseCode,
            resumeFrom: resumeFrom
        )
    }
}
#endif

// MARK: - SHA256 Hasher

/// Simple SHA256 hasher using CommonCrypto
private struct SHA256Hasher {
    private var context: CC_SHA256_CTX = {
        var context = CC_SHA256_CTX()
        CC_SHA256_Init(&context)
        return context
    }()

    mutating func update(data: Data) {
        data.withUnsafeBytes { bytes in
            guard let baseAddress = bytes.baseAddress else { return }
            CC_SHA256_Update(&context, baseAddress, CC_LONG(data.count))
        }
    }

    mutating func finalize() -> String {
        var workingContext = context
        var hash = [UInt8](repeating: 0, count: Int(CC_SHA256_DIGEST_LENGTH))
        CC_SHA256_Final(&hash, &workingContext)
        return hash.map { String(format: "%02x", $0) }.joined()
    }
}

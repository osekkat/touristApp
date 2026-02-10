import CommonCrypto
import XCTest
@testable import MarrakechGuide

final class DownloadServiceTests: XCTestCase {
    override class func setUp() {
        super.setUp()
        URLProtocol.registerClass(ControlledDownloadURLProtocol.self)
    }

    override class func tearDown() {
        URLProtocol.unregisterClass(ControlledDownloadURLProtocol.self)
        super.tearDown()
    }

    override func setUp() {
        super.setUp()
        ControlledDownloadURLProtocol.reset()
    }

    override func tearDown() {
        let resetExpectation = expectation(description: "reset download service state")
        Task { @MainActor in
            DownloadServiceImpl.shared._testResetDownloadStateForTests()
            resetExpectation.fulfill()
        }
        wait(for: [resetExpectation], timeout: 2.0)
        super.tearDown()
    }

    func testPersistDownloadedChunk_PartialResponse_AppendsToExistingFile() throws {
        let fileManager = FileManager.default
        let baseDirectory = fileManager.temporaryDirectory
            .appendingPathComponent("DownloadServiceTests-\(UUID().uuidString)", isDirectory: true)
        try fileManager.createDirectory(at: baseDirectory, withIntermediateDirectories: true)

        let destinationURL = baseDirectory.appendingPathComponent("destination.tmp")
        let sourceURL = baseDirectory.appendingPathComponent("source.tmp")

        try Data("abc".utf8).write(to: destinationURL)
        try Data("def".utf8).write(to: sourceURL)

        try DownloadServiceImpl.shared._testPersistDownloadedChunk(
            from: sourceURL,
            to: destinationURL,
            responseCode: 206,
            resumeFrom: 3
        )

        let contents = try readUTF8Contents(from: destinationURL)
        XCTAssertEqual(contents, "abcdef")
        XCTAssertFalse(fileManager.fileExists(atPath: sourceURL.path))
    }

    func testPersistDownloadedChunk_FullResponse_ReplacesDestinationFile() throws {
        let fileManager = FileManager.default
        let baseDirectory = fileManager.temporaryDirectory
            .appendingPathComponent("DownloadServiceTests-\(UUID().uuidString)", isDirectory: true)
        try fileManager.createDirectory(at: baseDirectory, withIntermediateDirectories: true)

        let destinationURL = baseDirectory.appendingPathComponent("destination.tmp")
        let sourceURL = baseDirectory.appendingPathComponent("source.tmp")

        try Data("old".utf8).write(to: destinationURL)
        try Data("new".utf8).write(to: sourceURL)

        try DownloadServiceImpl.shared._testPersistDownloadedChunk(
            from: sourceURL,
            to: destinationURL,
            responseCode: 200,
            resumeFrom: 0
        )

        let contents = try readUTF8Contents(from: destinationURL)
        XCTAssertEqual(contents, "new")
    }

    func testStartDownload_ConcurrentAttempt_ReturnsAlreadyInProgressError() async {
        let packId = "ios-concurrent-\(UUID().uuidString)"
        let pack = makeTestPack(packId: packId)
        let service = DownloadServiceImpl.shared

        await MainActor.run {
            service._testConfigureForDownloadTests(availablePacks: [pack])
        }

        ControlledDownloadURLProtocol.configure(
            chunk: Data(repeating: 0xAB, count: 2048),
            chunkCount: 2_000,
            chunkDelayMs: 3
        )

        let firstTask = Task {
            await service.startDownload(packId: pack.id)
        }

        await waitForCondition("first download request") {
            ControlledDownloadURLProtocol.requestCount >= 1
        }

        let secondResult = await service.startDownload(packId: pack.id)

        switch secondResult {
        case .error(let failedPackId, let error):
            XCTAssertEqual(failedPackId, pack.id)
            guard case .unknown(let message) = error else {
                XCTFail("Expected unknown error, got \(error)")
                break
            }
            XCTAssertEqual(message, "Download already in progress")
        default:
            XCTFail("Expected second concurrent attempt to fail with already-in-progress error")
        }

        await service.pauseDownload(packId: pack.id)
        _ = await firstTask.value
        await MainActor.run {
            service._testResetDownloadStateForTests()
        }
    }

    func testStartDownload_WhenTransportCancels_ClearsActiveStateAndAllowsRetry() async {
        let packId = "ios-cancelled-retry-\(UUID().uuidString)"
        let pack = makeTestPack(packId: packId)
        let service = DownloadServiceImpl.shared

        await MainActor.run {
            service._testConfigureForDownloadTests(availablePacks: [pack])
        }

        ControlledDownloadURLProtocol.configureError(code: .cancelled)

        let firstResult = await service.startDownload(packId: pack.id)
        guard case .progress(let firstPackId, _, let firstTotalBytes) = firstResult else {
            XCTFail("Expected cancelled transport to return progress on first attempt, got \(firstResult)")
            return
        }
        XCTAssertEqual(firstPackId, pack.id)
        XCTAssertEqual(firstTotalBytes, pack.sizeBytes)
        XCTAssertEqual(ControlledDownloadURLProtocol.requestCount, 1)

        let firstState = (await service.packStates)[pack.id]
        XCTAssertEqual(firstState?.status, .paused)

        let secondResult = await service.startDownload(packId: pack.id)
        guard case .progress(let secondPackId, _, let secondTotalBytes) = secondResult else {
            XCTFail("Expected retry after cancelled transport to proceed, got \(secondResult)")
            return
        }
        XCTAssertEqual(secondPackId, pack.id)
        XCTAssertEqual(secondTotalBytes, pack.sizeBytes)
        XCTAssertEqual(ControlledDownloadURLProtocol.requestCount, 2)

        let secondState = (await service.packStates)[pack.id]
        XCTAssertEqual(secondState?.status, .paused)
    }

    func testRemovePack_WhenDownloadInFlight_ClearsActiveStateAndAllowsImmediateRestart() async {
        let packId = "ios-remove-inflight-\(UUID().uuidString)"
        let pack = makeTestPack(packId: packId)
        let service = DownloadServiceImpl.shared

        await MainActor.run {
            service._testConfigureForDownloadTests(availablePacks: [pack])
        }

        ControlledDownloadURLProtocol.configure(
            payload: Data(repeating: 0xA5, count: 64 * 1024),
            chunkDelayMs: 400
        )

        let firstTask = Task {
            await service.startDownload(packId: pack.id)
        }

        await waitForCondition("first download request before remove") {
            ControlledDownloadURLProtocol.requestCount >= 1
        }

        await service.removePack(packId: pack.id)

        let stateAfterRemove = (await service.packStates)[pack.id]
        XCTAssertEqual(stateAfterRemove?.status, .notDownloaded)

        ControlledDownloadURLProtocol.configureError(code: .cancelled)
        let retryResult = await service.startDownload(packId: pack.id)
        guard case .progress(let retryPackId, _, let retryTotalBytes) = retryResult else {
            XCTFail("Expected retry after remove to start a new request, got \(retryResult)")
            _ = await firstTask.value
            return
        }
        XCTAssertEqual(retryPackId, pack.id)
        XCTAssertEqual(retryTotalBytes, pack.sizeBytes)
        XCTAssertEqual(ControlledDownloadURLProtocol.requestCount, 1)

        _ = await firstTask.value
    }

    func testRemovePack_WhenUpdateInstallInFlight_KeepsNotDownloadedAndDoesNotRestoreInstalledState() async {
        let packId = "ios-remove-installing-existing-\(UUID().uuidString)"
        let payload = Data(repeating: 0x6B, count: 8 * 1024 * 1024)
        let pack = makeTestPack(
            packId: packId,
            sizeBytes: Int64(payload.count),
            sha256: sha256Hex(payload)
        )
        let service = DownloadServiceImpl.shared

        await MainActor.run {
            service._testConfigureForDownloadTests(availablePacks: [pack])
            service._testSetNetworkAvailabilityForTests(true)
            service._testSetAvailableSpaceForTests(Int64.max)
        }

        do {
            let documentsDirectory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            let packDirectory = documentsDirectory
                .appendingPathComponent("packs", isDirectory: true)
                .appendingPathComponent(pack.id, isDirectory: true)
            try FileManager.default.createDirectory(at: packDirectory, withIntermediateDirectories: true)
            try Data("installed-content".utf8).write(to: packDirectory.appendingPathComponent("data.pack"))
            let manifestData = try JSONSerialization.data(withJSONObject: [
                "id": pack.id,
                "version": "0.9.0",
                "installedAt": 1_739_600_000.0
            ])
            try manifestData.write(to: packDirectory.appendingPathComponent("manifest.json"))
        } catch {
            XCTFail("Failed to seed existing installed pack fixtures: \(error.localizedDescription)")
            return
        }

        let removeTriggeredExpectation = expectation(description: "remove triggered during installing")
        let observerLock = NSLock()
        var hasTriggeredRemove = false
        await MainActor.run {
            service._testSetPackStateObserverForTests { observedPackId, state in
                guard observedPackId == pack.id, state.status == .installing else {
                    return
                }
                observerLock.lock()
                defer { observerLock.unlock() }
                guard !hasTriggeredRemove else {
                    return
                }
                hasTriggeredRemove = true
                Task { @MainActor in
                    await service.removePack(packId: pack.id)
                    removeTriggeredExpectation.fulfill()
                }
            }
        }

        ControlledDownloadURLProtocol.configure(payload: payload)

        let result = await service.startDownload(packId: pack.id)
        await fulfillment(of: [removeTriggeredExpectation], timeout: 3.0)

        switch result {
        case .progress(let resultPackId, _, _):
            XCTAssertEqual(resultPackId, pack.id)
        case .success(let resultPackId):
            XCTAssertEqual(resultPackId, pack.id)
        case .error(let failedPackId, let error):
            XCTFail("Expected remove-during-install flow to avoid errors, got error \(error) for \(failedPackId)")
        }

        await MainActor.run {
            service._testSetPackStateObserverForTests(nil)
        }

        let state = (await service.packStates)[pack.id]
        XCTAssertEqual(state?.status, .notDownloaded)
        XCTAssertEqual(state?.downloadedBytes, 0)

        let documentsDirectory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let packDirectory = documentsDirectory
            .appendingPathComponent("packs", isDirectory: true)
            .appendingPathComponent(pack.id, isDirectory: true)
        XCTAssertFalse(FileManager.default.fileExists(atPath: packDirectory.path))

        let tempFile = FileManager.default.temporaryDirectory
            .appendingPathComponent("pack_downloads", isDirectory: true)
            .appendingPathComponent("\(pack.id).tmp")
        XCTAssertFalse(FileManager.default.fileExists(atPath: tempFile.path))
    }

    func testStartDownload_WhenStaleSessionCompletesAfterCancel_DoesNotOverrideNewActiveSession() async {
        let packId = "ios-stale-session-overlap-\(UUID().uuidString)"
        let firstPayload = Data(repeating: 0x3C, count: 128 * 1024)
        let pack = makeTestPack(
            packId: packId,
            sizeBytes: Int64(firstPayload.count),
            sha256: sha256Hex(firstPayload)
        )
        let service = DownloadServiceImpl.shared

        await MainActor.run {
            service._testConfigureForDownloadTests(availablePacks: [pack])
            service._testSetNetworkAvailabilityForTests(true)
            service._testSetAvailableSpaceForTests(Int64.max)
        }

        ControlledDownloadURLProtocol.configure(
            payload: firstPayload,
            chunkDelayMs: 450,
            ignoreStopLoading: true
        )

        let firstTask = Task {
            await service.startDownload(packId: pack.id)
        }

        await waitForCondition("first request before cancel for stale-session overlap") {
            ControlledDownloadURLProtocol.requestCount >= 1
        }
        await service.cancelDownload(packId: pack.id)

        ControlledDownloadURLProtocol.configure(
            chunk: Data([0xAB]),
            chunkCount: 8_000,
            chunkDelayMs: 200
        )

        let secondTask = Task {
            await service.startDownload(packId: pack.id)
        }

        await waitForCondition("second request for stale-session overlap") {
            ControlledDownloadURLProtocol.requestCount >= 1
        }

        let firstResult = await firstTask.value
        guard case .progress(let firstPackId, _, let firstTotalBytes) = firstResult else {
            XCTFail("Expected stale first session to return progress without overriding active session, got \(firstResult)")
            await service.pauseDownload(packId: pack.id)
            _ = await secondTask.value
            return
        }
        XCTAssertEqual(firstPackId, pack.id)
        XCTAssertEqual(firstTotalBytes, pack.sizeBytes)

        let tempFile = FileManager.default.temporaryDirectory
            .appendingPathComponent("pack_downloads", isDirectory: true)
            .appendingPathComponent("\(pack.id).tmp")
        let tempBytesAfterFirstResult: Int64 = {
            guard
                let attributes = try? FileManager.default.attributesOfItem(atPath: tempFile.path),
                let sizeNumber = attributes[.size] as? NSNumber
            else {
                return 0
            }
            return sizeNumber.int64Value
        }()
        XCTAssertLessThan(tempBytesAfterFirstResult, pack.sizeBytes)

        let stateAfterFirstResult = (await service.packStates)[pack.id]
        XCTAssertNotEqual(stateAfterFirstResult?.status, .installed)

        await service.pauseDownload(packId: pack.id)
        let secondResult = await secondTask.value
        guard case .progress(let secondPackId, _, let secondTotalBytes) = secondResult else {
            XCTFail("Expected second in-flight session to pause with progress, got \(secondResult)")
            return
        }
        XCTAssertEqual(secondPackId, pack.id)
        XCTAssertEqual(secondTotalBytes, pack.sizeBytes)

        let finalState = (await service.packStates)[pack.id]
        XCTAssertNotEqual(finalState?.status, .installed)
    }

    func testPauseDownload_WhenUpdateInstallInFlight_RestoresInstalledStateFromDisk() async {
        let packId = "ios-pause-installing-existing-\(UUID().uuidString)"
        let payload = Data(repeating: 0x44, count: 8 * 1024 * 1024)
        let pack = makeTestPack(
            packId: packId,
            sizeBytes: Int64(payload.count),
            sha256: sha256Hex(payload)
        )
        let service = DownloadServiceImpl.shared

        await MainActor.run {
            service._testConfigureForDownloadTests(availablePacks: [pack])
            service._testSetNetworkAvailabilityForTests(true)
            service._testSetAvailableSpaceForTests(Int64.max)
        }

        do {
            let documentsDirectory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            let packDirectory = documentsDirectory
                .appendingPathComponent("packs", isDirectory: true)
                .appendingPathComponent(pack.id, isDirectory: true)
            try FileManager.default.createDirectory(at: packDirectory, withIntermediateDirectories: true)
            try Data("installed-content".utf8).write(to: packDirectory.appendingPathComponent("data.pack"))
            let manifestData = try JSONSerialization.data(withJSONObject: [
                "id": pack.id,
                "version": "0.9.0",
                "installedAt": 1_739_700_000.0
            ])
            try manifestData.write(to: packDirectory.appendingPathComponent("manifest.json"))
        } catch {
            XCTFail("Failed to seed existing installed pack fixtures for pause-during-install test: \(error.localizedDescription)")
            return
        }

        let pauseTriggeredExpectation = expectation(description: "pause triggered during installing")
        let observerLock = NSLock()
        var hasTriggeredPause = false
        await MainActor.run {
            service._testSetPackStateObserverForTests { observedPackId, state in
                guard observedPackId == pack.id, state.status == .installing else {
                    return
                }
                observerLock.lock()
                defer { observerLock.unlock() }
                guard !hasTriggeredPause else {
                    return
                }
                hasTriggeredPause = true
                Task { @MainActor in
                    await service.pauseDownload(packId: pack.id)
                    pauseTriggeredExpectation.fulfill()
                }
            }
        }

        ControlledDownloadURLProtocol.configure(payload: payload)
        let result = await service.startDownload(packId: pack.id)
        await fulfillment(of: [pauseTriggeredExpectation], timeout: 3.0)

        switch result {
        case .progress(let resultPackId, _, _):
            XCTAssertEqual(resultPackId, pack.id)
        case .success(let resultPackId):
            XCTAssertEqual(resultPackId, pack.id)
        case .error(let failedPackId, let error):
            XCTFail("Expected pause-during-install flow to avoid errors, got error \(error) for \(failedPackId)")
        }

        await MainActor.run {
            service._testSetPackStateObserverForTests(nil)
        }

        let state = (await service.packStates)[pack.id]
        XCTAssertEqual(state?.status, .installed)
        XCTAssertEqual(state?.installedVersion, pack.version)
        XCTAssertEqual(state?.downloadedBytes, pack.sizeBytes)
        XCTAssertEqual(state?.totalBytes, pack.sizeBytes)
    }

    func testPauseDownload_CancelledTaskReturnsProgressAndPausedState() async {
        let packId = "ios-pause-\(UUID().uuidString)"
        let pack = makeTestPack(packId: packId)
        let service = DownloadServiceImpl.shared

        await MainActor.run {
            service._testConfigureForDownloadTests(availablePacks: [pack])
        }

        ControlledDownloadURLProtocol.configure(
            chunk: Data(repeating: 0xCD, count: 2048),
            chunkCount: 2_000,
            chunkDelayMs: 3
        )

        let startTask = Task {
            await service.startDownload(packId: pack.id)
        }

        await waitForCondition("download request before pause") {
            ControlledDownloadURLProtocol.requestCount >= 1
        }

        await service.pauseDownload(packId: pack.id)
        let result = await startTask.value

        switch result {
        case .progress(let resultPackId, let downloadedBytes, let totalBytes):
            XCTAssertEqual(resultPackId, pack.id)
            XCTAssertEqual(totalBytes, pack.sizeBytes)
            XCTAssertGreaterThanOrEqual(downloadedBytes, 0)
        default:
            XCTFail("Expected paused download to return progress, got \(result)")
        }

        let state = (await service.packStates)[pack.id]
        XCTAssertEqual(state?.status, .paused)
        XCTAssertEqual(state?.totalBytes, pack.sizeBytes)
        XCTAssertGreaterThanOrEqual(state?.downloadedBytes ?? -1, 0)
        await MainActor.run {
            service._testResetDownloadStateForTests()
        }
    }

    func testPauseDownload_WhenNotDownloaded_NoOpAndKeepsState() async {
        let packId = "ios-pause-noop-not-downloaded-\(UUID().uuidString)"
        let pack = makeTestPack(packId: packId)
        let service = DownloadServiceImpl.shared

        await MainActor.run {
            service._testConfigureForDownloadTests(availablePacks: [pack])
        }

        await service.pauseDownload(packId: pack.id)

        let state = (await service.packStates)[pack.id]
        XCTAssertEqual(state?.status, .notDownloaded)
        XCTAssertEqual(state?.downloadedBytes, 0)
        XCTAssertEqual(state?.totalBytes, 0)
    }

    func testPauseDownload_WhenInstalled_NoOpAndKeepsInstalledState() async {
        let packId = "ios-pause-noop-installed-\(UUID().uuidString)"
        let pack = makeTestPack(packId: packId)
        let service = DownloadServiceImpl.shared

        await MainActor.run {
            service._testConfigureForDownloadTests(availablePacks: [pack])
            service._testSetPackStateForTests(
                PackState(
                    packId: pack.id,
                    status: .installed,
                    downloadedBytes: pack.sizeBytes,
                    totalBytes: pack.sizeBytes,
                    installedVersion: pack.version,
                    installedAt: Date()
                )
            )
        }

        await service.pauseDownload(packId: pack.id)

        let state = (await service.packStates)[pack.id]
        XCTAssertEqual(state?.status, .installed)
        XCTAssertEqual(state?.downloadedBytes, pack.sizeBytes)
        XCTAssertEqual(state?.totalBytes, pack.sizeBytes)
        XCTAssertEqual(state?.installedVersion, pack.version)
    }

    func testPauseDownload_WhenInstalledAndTrackedTaskExists_NoOpAndKeepsInstalledState() async {
        let packId = "ios-pause-installed-stale-task-\(UUID().uuidString)"
        let pack = makeTestPack(packId: packId)
        let service = DownloadServiceImpl.shared
        let trackedTask = makeDormantTrackedDownloadTask(packId: pack.id)

        await MainActor.run {
            service._testConfigureForDownloadTests(availablePacks: [pack])
            service._testSetPackStateForTests(
                PackState(
                    packId: pack.id,
                    status: .installed,
                    downloadedBytes: pack.sizeBytes,
                    totalBytes: pack.sizeBytes,
                    installedVersion: pack.version,
                    installedAt: Date()
                )
            )
            service._testSetDownloadTaskForTests(packId: pack.id, task: trackedTask)
        }

        await service.pauseDownload(packId: pack.id)

        let state = (await service.packStates)[pack.id]
        XCTAssertEqual(state?.status, .installed)
        XCTAssertEqual(state?.downloadedBytes, pack.sizeBytes)
        XCTAssertEqual(state?.totalBytes, pack.sizeBytes)
        XCTAssertEqual(state?.installedVersion, pack.version)
    }

    func testPauseDownload_WhenStateIsDownloadingWithoutActiveSet_PausesState() async {
        let packId = "ios-pause-downloading-state-only-\(UUID().uuidString)"
        let pack = makeTestPack(packId: packId)
        let service = DownloadServiceImpl.shared
        let trackedTask = makeDormantTrackedDownloadTask(packId: pack.id)

        await MainActor.run {
            service._testConfigureForDownloadTests(availablePacks: [pack])
            service._testSetPackStateForTests(
                PackState(
                    packId: pack.id,
                    status: .downloading,
                    downloadedBytes: 512,
                    totalBytes: 2_048
                )
            )
            service._testSetDownloadTaskForTests(packId: pack.id, task: trackedTask)
        }

        await service.pauseDownload(packId: pack.id)

        let state = (await service.packStates)[pack.id]
        XCTAssertEqual(state?.status, .paused)
        XCTAssertEqual(state?.downloadedBytes, 512)
        XCTAssertEqual(state?.totalBytes, 2_048)
    }

    func testResumeDownload_CancelledTaskReturnsZeroProgressAndNotDownloadedState() async {
        let packId = "ios-cancel-resume-\(UUID().uuidString)"
        let pack = makeTestPack(packId: packId)
        let service = DownloadServiceImpl.shared

        await MainActor.run {
            service._testConfigureForDownloadTests(availablePacks: [pack])
        }

        ControlledDownloadURLProtocol.configure(
            chunk: Data(repeating: 0xEF, count: 2048),
            chunkCount: 2_000,
            chunkDelayMs: 3
        )

        let startTask = Task {
            await service.startDownload(packId: pack.id)
        }

        await waitForCondition("initial download request before pause") {
            ControlledDownloadURLProtocol.requestCount >= 1
        }

        await service.pauseDownload(packId: pack.id)
        let firstResult = await startTask.value
        guard case .progress(_, let pausedBytes, _) = firstResult else {
            XCTFail("Expected initial start to pause with progress, got \(firstResult)")
            await MainActor.run {
                service._testResetDownloadStateForTests()
            }
            return
        }
        XCTAssertGreaterThan(pausedBytes, 0)

        let resumeTask = Task {
            await service.resumeDownload(packId: pack.id)
        }

        await waitForCondition("resume request before cancel") {
            ControlledDownloadURLProtocol.requestCount >= 2
        }
        await service.cancelDownload(packId: pack.id)

        let resumedResult = await resumeTask.value
        guard case .progress(let resultPackId, let downloadedBytes, let totalBytes) = resumedResult else {
            XCTFail("Expected cancelled resume to return progress, got \(resumedResult)")
            await MainActor.run {
                service._testResetDownloadStateForTests()
            }
            return
        }

        XCTAssertEqual(resultPackId, pack.id)
        XCTAssertEqual(downloadedBytes, 0)
        XCTAssertEqual(totalBytes, pack.sizeBytes)

        let state = (await service.packStates)[pack.id]
        XCTAssertEqual(state?.status, .notDownloaded)
        XCTAssertEqual(state?.downloadedBytes, 0)
        let tempFile = FileManager.default.temporaryDirectory
            .appendingPathComponent("pack_downloads", isDirectory: true)
            .appendingPathComponent("\(pack.id).tmp")
        XCTAssertFalse(FileManager.default.fileExists(atPath: tempFile.path))

        await MainActor.run {
            service._testResetDownloadStateForTests()
        }
    }

    func testResumeDownload_CancelledTaskWithExistingInstall_RestoresUpdateAvailableState() async {
        let packId = "ios-cancel-existing-\(UUID().uuidString)"
        let pack = makeTestPack(packId: packId)
        let service = DownloadServiceImpl.shared

        await MainActor.run {
            service._testConfigureForDownloadTests(availablePacks: [pack])
        }

        do {
            let documentsDirectory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            let packDirectory = documentsDirectory
                .appendingPathComponent("packs", isDirectory: true)
                .appendingPathComponent(pack.id, isDirectory: true)
            try FileManager.default.createDirectory(at: packDirectory, withIntermediateDirectories: true)
            try Data("installed-content".utf8).write(to: packDirectory.appendingPathComponent("data.pack"))
            let manifestData = try JSONSerialization.data(withJSONObject: [
                "id": pack.id,
                "version": "0.9.0",
                "installedAt": 1_739_100_000.0
            ])
            try manifestData.write(to: packDirectory.appendingPathComponent("manifest.json"))
        } catch {
            XCTFail("Failed to seed installed pack fixtures: \(error.localizedDescription)")
            return
        }

        ControlledDownloadURLProtocol.configure(
            chunk: Data(repeating: 0xAA, count: 2048),
            chunkCount: 2_000,
            chunkDelayMs: 3
        )

        let startTask = Task {
            await service.startDownload(packId: pack.id)
        }
        await waitForCondition("initial request before pause for existing install") {
            ControlledDownloadURLProtocol.requestCount >= 1
        }
        await service.pauseDownload(packId: pack.id)
        _ = await startTask.value

        let resumeTask = Task {
            await service.resumeDownload(packId: pack.id)
        }
        await waitForCondition("resume request before cancel for existing install") {
            ControlledDownloadURLProtocol.requestCount >= 2
        }
        await service.cancelDownload(packId: pack.id)
        _ = await resumeTask.value

        let state = (await service.packStates)[pack.id]
        XCTAssertEqual(state?.status, .updateAvailable)
        XCTAssertEqual(state?.installedVersion, "0.9.0")
        XCTAssertEqual(state?.downloadedBytes, 0)
        XCTAssertEqual(state?.totalBytes, pack.sizeBytes)
    }

    func testCancelDownload_WithExistingInstalledPack_RestoresUpdateAvailableState() async {
        let packId = "ios-cancel-existing-direct-\(UUID().uuidString)"
        let pack = makeTestPack(packId: packId)
        let service = DownloadServiceImpl.shared

        await MainActor.run {
            service._testConfigureForDownloadTests(availablePacks: [pack])
        }

        do {
            let documentsDirectory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            let packDirectory = documentsDirectory
                .appendingPathComponent("packs", isDirectory: true)
                .appendingPathComponent(pack.id, isDirectory: true)
            try FileManager.default.createDirectory(at: packDirectory, withIntermediateDirectories: true)
            try Data("installed-content".utf8).write(to: packDirectory.appendingPathComponent("data.pack"))
            let manifestData = try JSONSerialization.data(withJSONObject: [
                "id": pack.id,
                "version": "0.8.1",
                "installedAt": 1_739_200_000.0
            ])
            try manifestData.write(to: packDirectory.appendingPathComponent("manifest.json"))
        } catch {
            XCTFail("Failed to seed installed pack fixtures: \(error.localizedDescription)")
            return
        }

        await service.cancelDownload(packId: pack.id)

        let state = (await service.packStates)[pack.id]
        XCTAssertEqual(state?.status, .updateAvailable)
        XCTAssertEqual(state?.installedVersion, "0.8.1")
        XCTAssertEqual(state?.downloadedBytes, 0)
        XCTAssertEqual(state?.totalBytes, pack.sizeBytes)
    }

    func testCheckForUpdates_WhenInstalledVersionDiff_ResetsProgressAndMarksUpdateAvailable() async {
        let packId = "ios-check-updates-progress-reset-\(UUID().uuidString)"
        let pack = makeTestPack(packId: packId)
        let service = DownloadServiceImpl.shared

        await MainActor.run {
            service._testConfigureForDownloadTests(availablePacks: [pack])
            service._testSetPackStateForTests(
                PackState(
                    packId: pack.id,
                    status: .installed,
                    downloadedBytes: pack.sizeBytes,
                    totalBytes: pack.sizeBytes,
                    errorMessage: "Previous update attempt failed",
                    installedVersion: "0.5.0",
                    installedAt: Date()
                )
            )
        }

        await service.checkForUpdates()

        let state = (await service.packStates)[pack.id]
        XCTAssertEqual(state?.status, .updateAvailable)
        XCTAssertEqual(state?.downloadedBytes, 0)
        XCTAssertEqual(state?.totalBytes, pack.sizeBytes)
        XCTAssertNil(state?.errorMessage)
        XCTAssertEqual(state?.installedVersion, "0.5.0")
    }

    func testCheckForUpdates_WhenPausedOutdatedStateHasPartialBytes_ResetsAndRemovesStaleTempFile() async {
        let packId = "ios-check-updates-paused-stale-\(UUID().uuidString)"
        let pack = makeTestPack(packId: packId, sizeBytes: 4_096)
        let service = DownloadServiceImpl.shared

        let tempDirectory = FileManager.default.temporaryDirectory
            .appendingPathComponent("pack_downloads", isDirectory: true)
        let tempFile = tempDirectory.appendingPathComponent("\(pack.id).tmp")
        let stalePartialBytes = 1_024
        do {
            try FileManager.default.createDirectory(at: tempDirectory, withIntermediateDirectories: true)
            try Data(repeating: 0x42, count: stalePartialBytes).write(to: tempFile)
        } catch {
            XCTFail("Failed to seed stale paused temp file for check-for-updates test: \(error.localizedDescription)")
            return
        }

        await MainActor.run {
            service._testConfigureForDownloadTests(availablePacks: [pack])
            service._testSetPackStateForTests(
                PackState(
                    packId: pack.id,
                    status: .paused,
                    downloadedBytes: Int64(stalePartialBytes),
                    totalBytes: Int64(stalePartialBytes + 512),
                    errorMessage: "Previous paused update",
                    installedVersion: "0.8.0",
                    installedAt: Date()
                )
            )
        }

        XCTAssertTrue(FileManager.default.fileExists(atPath: tempFile.path))
        await service.checkForUpdates()

        let state = (await service.packStates)[pack.id]
        XCTAssertEqual(state?.status, .updateAvailable)
        XCTAssertEqual(state?.downloadedBytes, 0)
        XCTAssertEqual(state?.totalBytes, pack.sizeBytes)
        XCTAssertNil(state?.errorMessage)
        XCTAssertEqual(state?.installedVersion, "0.8.0")
        XCTAssertFalse(FileManager.default.fileExists(atPath: tempFile.path))
    }

    func testCheckForUpdates_WhenDownloadStartsDuringCleanup_DoesNotOverrideActiveState() async {
        let packId = "ios-check-updates-active-race-\(UUID().uuidString)"
        let pack = makeTestPack(packId: packId)
        let service = DownloadServiceImpl.shared

        let tempDirectory = FileManager.default.temporaryDirectory
            .appendingPathComponent("pack_downloads", isDirectory: true)
        let tempFile = tempDirectory.appendingPathComponent("\(pack.id).tmp")
        let stalePartialBytes = 2_048
        do {
            try FileManager.default.createDirectory(at: tempDirectory, withIntermediateDirectories: true)
            try Data(repeating: 0x37, count: stalePartialBytes).write(to: tempFile)
        } catch {
            XCTFail("Failed to seed temp file for check-for-updates active-race test: \(error.localizedDescription)")
            return
        }

        await MainActor.run {
            service._testConfigureForDownloadTests(availablePacks: [pack])
            service._testSetPackStateForTests(
                PackState(
                    packId: pack.id,
                    status: .paused,
                    downloadedBytes: Int64(stalePartialBytes),
                    totalBytes: Int64(stalePartialBytes + 512),
                    installedVersion: "0.9.0",
                    installedAt: Date()
                )
            )
            service._testSetTemporaryCleanupDelayForTests(250_000_000)
        }

        ControlledDownloadURLProtocol.configure(
            chunk: Data(repeating: 0xAC, count: 2_048),
            chunkCount: 2_000,
            chunkDelayMs: 3
        )

        let updatesTask = Task {
            await service.checkForUpdates()
        }

        try? await Task.sleep(nanoseconds: 40_000_000)

        let startTask = Task {
            await service.startDownload(packId: pack.id)
        }

        await waitForCondition("download request while checkForUpdates cleanup is delayed") {
            ControlledDownloadURLProtocol.requestCount >= 1
        }

        _ = await updatesTask.value

        let stateAfterUpdates = (await service.packStates)[pack.id]
        XCTAssertNotEqual(stateAfterUpdates?.status, .updateAvailable)
        XCTAssertTrue(
            FileManager.default.fileExists(atPath: tempFile.path),
            "Stale cleanup should not delete resume temp once download becomes active"
        )

        await service.pauseDownload(packId: pack.id)
        _ = await startTask.value

        let finalState = (await service.packStates)[pack.id]
        XCTAssertNotEqual(finalState?.status, .updateAvailable)

        await MainActor.run {
            service._testSetTemporaryCleanupDelayForTests(nil)
        }
    }

    func testStartDownload_VerificationFailureWithExistingOutdatedInstall_KeepsUpdateAvailableState() async {
        let packId = "ios-verify-existing-outdated-\(UUID().uuidString)"
        let pack = makeTestPack(packId: packId)
        let service = DownloadServiceImpl.shared

        await MainActor.run {
            service._testConfigureForDownloadTests(availablePacks: [pack])
        }

        do {
            let documentsDirectory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            let packDirectory = documentsDirectory
                .appendingPathComponent("packs", isDirectory: true)
                .appendingPathComponent(pack.id, isDirectory: true)
            try FileManager.default.createDirectory(at: packDirectory, withIntermediateDirectories: true)
            try Data("installed-content".utf8).write(to: packDirectory.appendingPathComponent("data.pack"))
            let manifestData = try JSONSerialization.data(withJSONObject: [
                "id": pack.id,
                "version": "0.7.0",
                "installedAt": 1_739_300_000.0
            ])
            try manifestData.write(to: packDirectory.appendingPathComponent("manifest.json"))
        } catch {
            XCTFail("Failed to seed installed pack fixtures: \(error.localizedDescription)")
            return
        }

        ControlledDownloadURLProtocol.configure(
            chunk: Data(repeating: 0xBC, count: 1024),
            chunkCount: 4,
            chunkDelayMs: 0
        )

        let result = await service.startDownload(packId: pack.id)
        guard case .error(let failedPackId, let error) = result else {
            XCTFail("Expected verification failure result, got \(result)")
            return
        }
        XCTAssertEqual(failedPackId, pack.id)
        guard case .verificationFailed = error else {
            XCTFail("Expected verificationFailed error, got \(error)")
            return
        }

        let state = (await service.packStates)[pack.id]
        XCTAssertEqual(state?.status, .updateAvailable)
        XCTAssertEqual(state?.installedVersion, "0.7.0")
        XCTAssertEqual(state?.downloadedBytes, 0)
        XCTAssertEqual(state?.totalBytes, pack.sizeBytes)
        XCTAssertEqual(state?.errorMessage, "Verification failed")
    }

    func testStartDownload_VerificationFailureWithExistingCurrentInstall_KeepsInstalledState() async {
        let packId = "ios-verify-existing-current-\(UUID().uuidString)"
        let pack = makeTestPack(packId: packId)
        let service = DownloadServiceImpl.shared

        await MainActor.run {
            service._testConfigureForDownloadTests(availablePacks: [pack])
        }

        do {
            let documentsDirectory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            let packDirectory = documentsDirectory
                .appendingPathComponent("packs", isDirectory: true)
                .appendingPathComponent(pack.id, isDirectory: true)
            try FileManager.default.createDirectory(at: packDirectory, withIntermediateDirectories: true)
            try Data("installed-content".utf8).write(to: packDirectory.appendingPathComponent("data.pack"))
            let manifestData = try JSONSerialization.data(withJSONObject: [
                "id": pack.id,
                "version": pack.version,
                "installedAt": 1_739_400_000.0
            ])
            try manifestData.write(to: packDirectory.appendingPathComponent("manifest.json"))
        } catch {
            XCTFail("Failed to seed installed pack fixtures: \(error.localizedDescription)")
            return
        }

        ControlledDownloadURLProtocol.configure(
            chunk: Data(repeating: 0xCC, count: 1024),
            chunkCount: 4,
            chunkDelayMs: 0
        )

        let result = await service.startDownload(packId: pack.id)
        guard case .error(let failedPackId, let error) = result else {
            XCTFail("Expected verification failure result, got \(result)")
            return
        }
        XCTAssertEqual(failedPackId, pack.id)
        guard case .verificationFailed = error else {
            XCTFail("Expected verificationFailed error, got \(error)")
            return
        }

        let state = (await service.packStates)[pack.id]
        XCTAssertEqual(state?.status, .installed)
        XCTAssertEqual(state?.installedVersion, pack.version)
        XCTAssertEqual(state?.downloadedBytes, pack.sizeBytes)
        XCTAssertEqual(state?.totalBytes, pack.sizeBytes)
        XCTAssertEqual(state?.errorMessage, "Verification failed")
    }

    func testStartDownload_InvalidURLWithExistingOutdatedInstall_KeepsUpdateAvailableState() async {
        let packId = "ios-invalid-url-existing-\(UUID().uuidString)"
        let pack = makeTestPack(packId: packId, downloadUrl: "not a valid url")
        let service = DownloadServiceImpl.shared

        await MainActor.run {
            service._testConfigureForDownloadTests(availablePacks: [pack])
        }

        do {
            let documentsDirectory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            let packDirectory = documentsDirectory
                .appendingPathComponent("packs", isDirectory: true)
                .appendingPathComponent(pack.id, isDirectory: true)
            try FileManager.default.createDirectory(at: packDirectory, withIntermediateDirectories: true)
            try Data("installed-content".utf8).write(to: packDirectory.appendingPathComponent("data.pack"))
            let manifestData = try JSONSerialization.data(withJSONObject: [
                "id": pack.id,
                "version": "0.6.0",
                "installedAt": 1_739_500_000.0
            ])
            try manifestData.write(to: packDirectory.appendingPathComponent("manifest.json"))
        } catch {
            XCTFail("Failed to seed installed pack fixtures: \(error.localizedDescription)")
            return
        }

        let result = await service.startDownload(packId: pack.id)
        guard case .error(let failedPackId, let error) = result else {
            XCTFail("Expected invalid URL error result, got \(result)")
            return
        }
        XCTAssertEqual(failedPackId, pack.id)
        guard case .unknown(let message) = error else {
            XCTFail("Expected unknown error for invalid URL, got \(error)")
            return
        }
        XCTAssertEqual(message, "Invalid download URL")

        let state = (await service.packStates)[pack.id]
        XCTAssertEqual(state?.status, .updateAvailable)
        XCTAssertEqual(state?.installedVersion, "0.6.0")
        XCTAssertEqual(state?.downloadedBytes, 0)
        XCTAssertEqual(state?.totalBytes, pack.sizeBytes)
        XCTAssertEqual(state?.errorMessage, "Invalid download URL")
    }

    func testStartDownload_InvalidURLWithCorruptedExistingManifest_FallsBackToFailedState() async {
        let packId = "ios-invalid-url-corrupt-manifest-\(UUID().uuidString)"
        let pack = makeTestPack(packId: packId, downloadUrl: "not a valid url")
        let service = DownloadServiceImpl.shared

        await MainActor.run {
            service._testConfigureForDownloadTests(availablePacks: [pack])
        }

        do {
            let documentsDirectory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            let packDirectory = documentsDirectory
                .appendingPathComponent("packs", isDirectory: true)
                .appendingPathComponent(pack.id, isDirectory: true)
            try FileManager.default.createDirectory(at: packDirectory, withIntermediateDirectories: true)
            try Data("installed-content".utf8).write(to: packDirectory.appendingPathComponent("data.pack"))
            try "{not-json".write(to: packDirectory.appendingPathComponent("manifest.json"), atomically: true, encoding: .utf8)
        } catch {
            XCTFail("Failed to seed corrupted installed pack fixtures: \(error.localizedDescription)")
            return
        }

        let result = await service.startDownload(packId: pack.id)
        guard case .error(let failedPackId, let error) = result else {
            XCTFail("Expected invalid URL error result, got \(result)")
            return
        }
        XCTAssertEqual(failedPackId, pack.id)
        guard case .unknown(let message) = error else {
            XCTFail("Expected unknown error for invalid URL, got \(error)")
            return
        }
        XCTAssertEqual(message, "Invalid download URL")

        let state = (await service.packStates)[pack.id]
        XCTAssertEqual(state?.status, .failed)
        XCTAssertEqual(state?.errorMessage, "Invalid download URL")
    }

    func testStartDownload_InvalidURLWithInvalidManifestShape_FallsBackToFailedState() async {
        let packId = "ios-invalid-url-invalid-shape-manifest-\(UUID().uuidString)"
        let pack = makeTestPack(packId: packId, downloadUrl: "not a valid url")
        let service = DownloadServiceImpl.shared

        await MainActor.run {
            service._testConfigureForDownloadTests(availablePacks: [pack])
        }

        do {
            let documentsDirectory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
            let packDirectory = documentsDirectory
                .appendingPathComponent("packs", isDirectory: true)
                .appendingPathComponent(pack.id, isDirectory: true)
            try FileManager.default.createDirectory(at: packDirectory, withIntermediateDirectories: true)
            try Data("installed-content".utf8).write(to: packDirectory.appendingPathComponent("data.pack"))
            let manifestData = try JSONSerialization.data(withJSONObject: ["unexpected-array-entry"])
            try manifestData.write(to: packDirectory.appendingPathComponent("manifest.json"))
        } catch {
            XCTFail("Failed to seed invalid-shape manifest fixtures: \(error.localizedDescription)")
            return
        }

        let result = await service.startDownload(packId: pack.id)
        guard case .error(let failedPackId, let error) = result else {
            XCTFail("Expected invalid URL error result, got \(result)")
            return
        }
        XCTAssertEqual(failedPackId, pack.id)
        guard case .unknown(let message) = error else {
            XCTFail("Expected unknown error for invalid URL, got \(error)")
            return
        }
        XCTAssertEqual(message, "Invalid download URL")

        let state = (await service.packStates)[pack.id]
        XCTAssertEqual(state?.status, .failed)
        XCTAssertEqual(state?.errorMessage, "Invalid download URL")
    }

    func testResumeDownload_WithCompleteTempFile_SkipsNetworkAndInstallsSuccessfully() async {
        let packId = "ios-complete-temp-\(UUID().uuidString)"
        let payload = Data(repeating: 0x5A, count: 2048)
        let pack = ContentPack(
            id: packId,
            type: .medinaMap,
            displayName: "Complete Temp Test Pack",
            description: "Test complete temp resume path",
            version: "1.0.0",
            sizeBytes: Int64(payload.count),
            sha256: sha256Hex(payload),
            downloadUrl: "not a valid url",
            minAppVersion: nil,
            dependencies: nil
        )
        let service = DownloadServiceImpl.shared

        let tempDirectory = FileManager.default.temporaryDirectory
            .appendingPathComponent("pack_downloads", isDirectory: true)
        let tempFile = tempDirectory.appendingPathComponent("\(pack.id).tmp")
        do {
            try FileManager.default.createDirectory(at: tempDirectory, withIntermediateDirectories: true)
            try payload.write(to: tempFile)
        } catch {
            XCTFail("Failed to seed complete temp file: \(error.localizedDescription)")
            return
        }

        await MainActor.run {
            service._testConfigureForDownloadTests(availablePacks: [pack])
            service._testSetPackStateForTests(
                PackState(
                    packId: pack.id,
                    status: .paused,
                    downloadedBytes: Int64(payload.count),
                    totalBytes: Int64(payload.count)
                )
            )
        }

        ControlledDownloadURLProtocol.reset()

        let result = await service.resumeDownload(packId: pack.id)
        guard case .success(let resultPackId) = result else {
            XCTFail("Expected complete temp resume to succeed, got \(result)")
            return
        }
        XCTAssertEqual(resultPackId, pack.id)
        XCTAssertEqual(ControlledDownloadURLProtocol.requestCount, 0)

        let state = (await service.packStates)[pack.id]
        XCTAssertEqual(state?.status, .installed)
        XCTAssertEqual(state?.downloadedBytes, Int64(payload.count))
        XCTAssertEqual(state?.totalBytes, Int64(payload.count))
    }

    func testResumeDownload_WithCompleteTempFileAndOfflineNetwork_InstallsSuccessfully() async {
        let packId = "ios-complete-temp-offline-\(UUID().uuidString)"
        let payload = Data(repeating: 0x7B, count: 2048)
        let pack = ContentPack(
            id: packId,
            type: .medinaMap,
            displayName: "Complete Temp Offline Test Pack",
            description: "Test complete temp offline resume path",
            version: "1.0.0",
            sizeBytes: Int64(payload.count),
            sha256: sha256Hex(payload),
            downloadUrl: "not a valid url",
            minAppVersion: nil,
            dependencies: nil
        )
        let service = DownloadServiceImpl.shared

        let tempDirectory = FileManager.default.temporaryDirectory
            .appendingPathComponent("pack_downloads", isDirectory: true)
        let tempFile = tempDirectory.appendingPathComponent("\(pack.id).tmp")
        do {
            try FileManager.default.createDirectory(at: tempDirectory, withIntermediateDirectories: true)
            try payload.write(to: tempFile)
        } catch {
            XCTFail("Failed to seed complete temp file for offline test: \(error.localizedDescription)")
            return
        }

        await MainActor.run {
            service._testConfigureForDownloadTests(availablePacks: [pack])
            service._testSetPackStateForTests(
                PackState(
                    packId: pack.id,
                    status: .paused,
                    downloadedBytes: Int64(payload.count),
                    totalBytes: Int64(payload.count)
                )
            )
            service._testSetNetworkAvailabilityForTests(false)
        }

        ControlledDownloadURLProtocol.reset()

        let result = await service.resumeDownload(packId: pack.id)
        guard case .success(let resultPackId) = result else {
            XCTFail("Expected offline complete temp resume to succeed, got \(result)")
            return
        }
        XCTAssertEqual(resultPackId, pack.id)
        XCTAssertEqual(ControlledDownloadURLProtocol.requestCount, 0)

        let state = (await service.packStates)[pack.id]
        XCTAssertEqual(state?.status, .installed)
        XCTAssertEqual(state?.downloadedBytes, Int64(payload.count))
        XCTAssertEqual(state?.totalBytes, Int64(payload.count))
    }

    func testStartDownload_WithCompleteTempFileAndOfflineNetwork_InstallsSuccessfully() async {
        let packId = "ios-start-complete-temp-offline-\(UUID().uuidString)"
        let payload = Data(repeating: 0x6D, count: 2048)
        let pack = ContentPack(
            id: packId,
            type: .medinaMap,
            displayName: "Start Complete Temp Offline Test Pack",
            description: "Test start download complete temp offline path",
            version: "1.0.0",
            sizeBytes: Int64(payload.count),
            sha256: sha256Hex(payload),
            downloadUrl: "not a valid url",
            minAppVersion: nil,
            dependencies: nil
        )
        let service = DownloadServiceImpl.shared

        let tempDirectory = FileManager.default.temporaryDirectory
            .appendingPathComponent("pack_downloads", isDirectory: true)
        let tempFile = tempDirectory.appendingPathComponent("\(pack.id).tmp")
        do {
            try FileManager.default.createDirectory(at: tempDirectory, withIntermediateDirectories: true)
            try payload.write(to: tempFile)
        } catch {
            XCTFail("Failed to seed complete temp file for offline start test: \(error.localizedDescription)")
            return
        }

        await MainActor.run {
            service._testConfigureForDownloadTests(availablePacks: [pack])
            service._testSetNetworkAvailabilityForTests(false)
        }

        ControlledDownloadURLProtocol.reset()

        let result = await service.startDownload(packId: pack.id)
        guard case .success(let resultPackId) = result else {
            XCTFail("Expected offline start with complete temp to succeed, got \(result)")
            return
        }
        XCTAssertEqual(resultPackId, pack.id)
        XCTAssertEqual(ControlledDownloadURLProtocol.requestCount, 0)

        let state = (await service.packStates)[pack.id]
        XCTAssertEqual(state?.status, .installed)
        XCTAssertEqual(state?.downloadedBytes, Int64(payload.count))
        XCTAssertEqual(state?.totalBytes, Int64(payload.count))
    }

    func testStartDownload_WithCompleteTempFileAndInsufficientStorage_ReturnsInsufficientStorage() async {
        let packId = "ios-start-complete-temp-low-storage-\(UUID().uuidString)"
        let payload = Data(repeating: 0x6E, count: 2048)
        let pack = ContentPack(
            id: packId,
            type: .medinaMap,
            displayName: "Start Complete Temp Low Storage Test Pack",
            description: "Test start download complete temp low storage path",
            version: "1.0.0",
            sizeBytes: Int64(payload.count),
            sha256: sha256Hex(payload),
            downloadUrl: "not a valid url",
            minAppVersion: nil,
            dependencies: nil
        )
        let service = DownloadServiceImpl.shared

        let tempDirectory = FileManager.default.temporaryDirectory
            .appendingPathComponent("pack_downloads", isDirectory: true)
        let tempFile = tempDirectory.appendingPathComponent("\(pack.id).tmp")
        do {
            try FileManager.default.createDirectory(at: tempDirectory, withIntermediateDirectories: true)
            try payload.write(to: tempFile)
        } catch {
            XCTFail("Failed to seed complete temp file for low storage start test: \(error.localizedDescription)")
            return
        }

        await MainActor.run {
            service._testConfigureForDownloadTests(availablePacks: [pack])
            service._testSetAvailableSpaceForTests(Int64(payload.count - 1))
            service._testSetNetworkAvailabilityForTests(false)
        }

        ControlledDownloadURLProtocol.reset()

        let result = await service.startDownload(packId: pack.id)
        guard case .error(let resultPackId, let error) = result else {
            XCTFail("Expected insufficient storage error, got \(result)")
            return
        }
        XCTAssertEqual(resultPackId, pack.id)
        guard case .insufficientStorage = error else {
            XCTFail("Expected insufficientStorage error, got \(error)")
            return
        }
        XCTAssertEqual(ControlledDownloadURLProtocol.requestCount, 0)
        XCTAssertTrue(FileManager.default.fileExists(atPath: tempFile.path))

        let state = (await service.packStates)[pack.id]
        XCTAssertEqual(state?.status, .notDownloaded)
        XCTAssertEqual(state?.downloadedBytes, 0)
        XCTAssertEqual(state?.totalBytes, 0)
    }

    func testResumeDownload_WithCompleteTempFileAndInsufficientStorage_ReturnsInsufficientStorage() async {
        let packId = "ios-resume-complete-temp-low-storage-\(UUID().uuidString)"
        let payload = Data(repeating: 0x6F, count: 2048)
        let pack = ContentPack(
            id: packId,
            type: .medinaMap,
            displayName: "Resume Complete Temp Low Storage Test Pack",
            description: "Test resume download complete temp low storage path",
            version: "1.0.0",
            sizeBytes: Int64(payload.count),
            sha256: sha256Hex(payload),
            downloadUrl: "not a valid url",
            minAppVersion: nil,
            dependencies: nil
        )
        let service = DownloadServiceImpl.shared

        let tempDirectory = FileManager.default.temporaryDirectory
            .appendingPathComponent("pack_downloads", isDirectory: true)
        let tempFile = tempDirectory.appendingPathComponent("\(pack.id).tmp")
        do {
            try FileManager.default.createDirectory(at: tempDirectory, withIntermediateDirectories: true)
            try payload.write(to: tempFile)
        } catch {
            XCTFail("Failed to seed complete temp file for low storage resume test: \(error.localizedDescription)")
            return
        }

        await MainActor.run {
            service._testConfigureForDownloadTests(availablePacks: [pack])
            service._testSetPackStateForTests(
                PackState(
                    packId: pack.id,
                    status: .paused,
                    downloadedBytes: Int64(payload.count),
                    totalBytes: Int64(payload.count)
                )
            )
            service._testSetAvailableSpaceForTests(Int64(payload.count - 1))
            service._testSetNetworkAvailabilityForTests(false)
        }

        ControlledDownloadURLProtocol.reset()

        let result = await service.resumeDownload(packId: pack.id)
        guard case .error(let resultPackId, let error) = result else {
            XCTFail("Expected insufficient storage error, got \(result)")
            return
        }
        XCTAssertEqual(resultPackId, pack.id)
        guard case .insufficientStorage = error else {
            XCTFail("Expected insufficientStorage error, got \(error)")
            return
        }
        XCTAssertEqual(ControlledDownloadURLProtocol.requestCount, 0)
        XCTAssertTrue(FileManager.default.fileExists(atPath: tempFile.path))

        let state = (await service.packStates)[pack.id]
        XCTAssertEqual(state?.status, .paused)
        XCTAssertEqual(state?.downloadedBytes, Int64(payload.count))
        XCTAssertEqual(state?.totalBytes, Int64(payload.count))
    }

    func testResumeDownload_WithPartialTempFileAndInsufficientStorage_ReturnsInsufficientStorage() async {
        let packId = "ios-resume-partial-temp-low-storage-\(UUID().uuidString)"
        let partialPayload = Data(repeating: 0x70, count: 1024)
        let totalSize = Int64(partialPayload.count * 4)
        let pack = ContentPack(
            id: packId,
            type: .medinaMap,
            displayName: "Resume Partial Temp Low Storage Test Pack",
            description: "Test resume download partial temp low storage path",
            version: "1.0.0",
            sizeBytes: totalSize,
            sha256: String(repeating: "0", count: 64),
            downloadUrl: "https://download.test/\(packId).pack",
            minAppVersion: nil,
            dependencies: nil
        )
        let service = DownloadServiceImpl.shared

        let tempDirectory = FileManager.default.temporaryDirectory
            .appendingPathComponent("pack_downloads", isDirectory: true)
        let tempFile = tempDirectory.appendingPathComponent("\(pack.id).tmp")
        do {
            try FileManager.default.createDirectory(at: tempDirectory, withIntermediateDirectories: true)
            try partialPayload.write(to: tempFile)
        } catch {
            XCTFail("Failed to seed partial temp file for low storage resume test: \(error.localizedDescription)")
            return
        }

        let partialBytes = Int64(partialPayload.count)
        let requiredBytes = totalSize + (totalSize - partialBytes)
        await MainActor.run {
            service._testConfigureForDownloadTests(availablePacks: [pack])
            service._testSetPackStateForTests(
                PackState(
                    packId: pack.id,
                    status: .paused,
                    downloadedBytes: partialBytes,
                    totalBytes: totalSize
                )
            )
            service._testSetAvailableSpaceForTests(requiredBytes - 1)
            service._testSetNetworkAvailabilityForTests(true)
        }

        ControlledDownloadURLProtocol.reset()

        let result = await service.resumeDownload(packId: pack.id)
        guard case .error(let resultPackId, let error) = result else {
            XCTFail("Expected insufficient storage error, got \(result)")
            return
        }
        XCTAssertEqual(resultPackId, pack.id)
        guard case .insufficientStorage = error else {
            XCTFail("Expected insufficientStorage error, got \(error)")
            return
        }
        XCTAssertEqual(ControlledDownloadURLProtocol.requestCount, 0)
        XCTAssertTrue(FileManager.default.fileExists(atPath: tempFile.path))

        let state = (await service.packStates)[pack.id]
        XCTAssertEqual(state?.status, .paused)
        XCTAssertEqual(state?.downloadedBytes, partialBytes)
        XCTAssertEqual(state?.totalBytes, totalSize)
    }

    func testResumeDownload_WhenStateIsStaleLowerThanTemp_UsesTempSizeRangeAndInstallsSuccessfully() async {
        let packId = "ios-resume-stale-state-\(UUID().uuidString)"
        let finalPayload = Data("abcdefgh".utf8)
        let staleStateBytes: Int64 = 3
        let partialPayload = Data(finalPayload.prefix(6))
        let pack = ContentPack(
            id: packId,
            type: .medinaMap,
            displayName: "Resume Stale State Test Pack",
            description: "Ensures resume range uses temp file size when state bytes are stale",
            version: "1.0.0",
            sizeBytes: Int64(finalPayload.count),
            sha256: sha256Hex(finalPayload),
            downloadUrl: "https://download.test/\(packId).pack",
            minAppVersion: nil,
            dependencies: nil
        )
        let service = DownloadServiceImpl.shared

        let tempDirectory = FileManager.default.temporaryDirectory
            .appendingPathComponent("pack_downloads", isDirectory: true)
        let tempFile = tempDirectory.appendingPathComponent("\(pack.id).tmp")
        do {
            try FileManager.default.createDirectory(at: tempDirectory, withIntermediateDirectories: true)
            try partialPayload.write(to: tempFile)
        } catch {
            XCTFail("Failed to seed partial temp file for stale-state resume test: \(error.localizedDescription)")
            return
        }

        await MainActor.run {
            service._testConfigureForDownloadTests(availablePacks: [pack])
            service._testSetPackStateForTests(
                PackState(
                    packId: pack.id,
                    status: .paused,
                    downloadedBytes: staleStateBytes,
                    totalBytes: Int64(finalPayload.count)
                )
            )
            service._testSetNetworkAvailabilityForTests(true)
            service._testSetAvailableSpaceForTests(Int64.max)
        }

        ControlledDownloadURLProtocol.configure(payload: finalPayload, honorRange: true)

        let result = await service.resumeDownload(packId: pack.id)
        guard case .success(let resultPackId) = result else {
            XCTFail("Expected stale-state resume to succeed, got \(result)")
            return
        }
        XCTAssertEqual(resultPackId, pack.id)
        XCTAssertEqual(ControlledDownloadURLProtocol.requestCount, 1)
        XCTAssertEqual(ControlledDownloadURLProtocol.lastRangeHeader, "bytes=\(partialPayload.count)-")

        let state = (await service.packStates)[pack.id]
        XCTAssertEqual(state?.status, .installed)
        XCTAssertEqual(state?.downloadedBytes, Int64(finalPayload.count))
        XCTAssertEqual(state?.totalBytes, Int64(finalPayload.count))
        XCTAssertEqual(state?.installedVersion, pack.version)
        XCTAssertFalse(FileManager.default.fileExists(atPath: tempFile.path))
    }

    func testResumeDownload_WhenStateIsStaleHigherThanTemp_UsesTempSizeRangeAndInstallsSuccessfully() async {
        let packId = "ios-resume-stale-high-state-\(UUID().uuidString)"
        let finalPayload = Data("abcdefgh".utf8)
        let staleStateBytes: Int64 = 7
        let partialPayload = Data(finalPayload.prefix(6))
        let pack = ContentPack(
            id: packId,
            type: .medinaMap,
            displayName: "Resume Stale High State Test Pack",
            description: "Ensures resume range uses temp file size when state bytes are stale high",
            version: "1.0.0",
            sizeBytes: Int64(finalPayload.count),
            sha256: sha256Hex(finalPayload),
            downloadUrl: "https://download.test/\(packId).pack",
            minAppVersion: nil,
            dependencies: nil
        )
        let service = DownloadServiceImpl.shared

        let tempDirectory = FileManager.default.temporaryDirectory
            .appendingPathComponent("pack_downloads", isDirectory: true)
        let tempFile = tempDirectory.appendingPathComponent("\(pack.id).tmp")
        do {
            try FileManager.default.createDirectory(at: tempDirectory, withIntermediateDirectories: true)
            try partialPayload.write(to: tempFile)
        } catch {
            XCTFail("Failed to seed partial temp file for stale-high resume test: \(error.localizedDescription)")
            return
        }

        await MainActor.run {
            service._testConfigureForDownloadTests(availablePacks: [pack])
            service._testSetPackStateForTests(
                PackState(
                    packId: pack.id,
                    status: .paused,
                    downloadedBytes: staleStateBytes,
                    totalBytes: Int64(finalPayload.count)
                )
            )
            service._testSetNetworkAvailabilityForTests(true)
            service._testSetAvailableSpaceForTests(Int64.max)
        }

        ControlledDownloadURLProtocol.configure(payload: finalPayload, honorRange: true)

        let result = await service.resumeDownload(packId: pack.id)
        guard case .success(let resultPackId) = result else {
            XCTFail("Expected stale-high resume to succeed, got \(result)")
            return
        }
        XCTAssertEqual(resultPackId, pack.id)
        XCTAssertEqual(ControlledDownloadURLProtocol.requestCount, 1)
        XCTAssertEqual(ControlledDownloadURLProtocol.lastRangeHeader, "bytes=\(partialPayload.count)-")

        let state = (await service.packStates)[pack.id]
        XCTAssertEqual(state?.status, .installed)
        XCTAssertEqual(state?.downloadedBytes, Int64(finalPayload.count))
        XCTAssertEqual(state?.totalBytes, Int64(finalPayload.count))
        XCTAssertEqual(state?.installedVersion, pack.version)
        XCTAssertFalse(FileManager.default.fileExists(atPath: tempFile.path))
    }

    func testResumeDownload_WhenPausedStateBytesAreZeroButTempExists_ResumesUsingTempRange() async {
        let packId = "ios-resume-zero-state-temp-present-\(UUID().uuidString)"
        let finalPayload = Data("abcdefgh".utf8)
        let partialPayload = Data(finalPayload.prefix(6))
        let pack = ContentPack(
            id: packId,
            type: .medinaMap,
            displayName: "Resume Zero State Temp Test Pack",
            description: "Ensures resume uses temp bytes even when paused state has 0 downloaded bytes",
            version: "1.0.0",
            sizeBytes: Int64(finalPayload.count),
            sha256: sha256Hex(finalPayload),
            downloadUrl: "https://download.test/\(packId).pack",
            minAppVersion: nil,
            dependencies: nil
        )
        let service = DownloadServiceImpl.shared

        let tempDirectory = FileManager.default.temporaryDirectory
            .appendingPathComponent("pack_downloads", isDirectory: true)
        let tempFile = tempDirectory.appendingPathComponent("\(pack.id).tmp")
        do {
            try FileManager.default.createDirectory(at: tempDirectory, withIntermediateDirectories: true)
            try partialPayload.write(to: tempFile)
        } catch {
            XCTFail("Failed to seed partial temp file for zero-state resume test: \(error.localizedDescription)")
            return
        }

        await MainActor.run {
            service._testConfigureForDownloadTests(availablePacks: [pack])
            service._testSetPackStateForTests(
                PackState(
                    packId: pack.id,
                    status: .paused,
                    downloadedBytes: 0,
                    totalBytes: Int64(finalPayload.count)
                )
            )
            service._testSetNetworkAvailabilityForTests(true)
            service._testSetAvailableSpaceForTests(Int64.max)
        }

        ControlledDownloadURLProtocol.configure(payload: finalPayload, honorRange: true)

        let result = await service.resumeDownload(packId: pack.id)
        guard case .success(let resultPackId) = result else {
            XCTFail("Expected zero-state temp resume to succeed, got \(result)")
            return
        }
        XCTAssertEqual(resultPackId, pack.id)
        XCTAssertEqual(ControlledDownloadURLProtocol.requestCount, 1)
        XCTAssertEqual(ControlledDownloadURLProtocol.lastRangeHeader, "bytes=\(partialPayload.count)-")

        let state = (await service.packStates)[pack.id]
        XCTAssertEqual(state?.status, .installed)
        XCTAssertEqual(state?.downloadedBytes, Int64(finalPayload.count))
        XCTAssertEqual(state?.totalBytes, Int64(finalPayload.count))
        XCTAssertEqual(state?.installedVersion, pack.version)
        XCTAssertFalse(FileManager.default.fileExists(atPath: tempFile.path))
    }

    func testResumeDownload_WhenStateIsStaleLowerThanTemp_FirstDownloadingStateUsesTempBytes() async {
        let packId = "ios-resume-stale-first-state-\(UUID().uuidString)"
        let staleStateBytes: Int64 = 3
        let canonicalTempBytes = 6
        let pack = makeTestPack(packId: packId)
        let service = DownloadServiceImpl.shared

        let tempDirectory = FileManager.default.temporaryDirectory
            .appendingPathComponent("pack_downloads", isDirectory: true)
        let tempFile = tempDirectory.appendingPathComponent("\(pack.id).tmp")
        do {
            try FileManager.default.createDirectory(at: tempDirectory, withIntermediateDirectories: true)
            try Data(repeating: 0x88, count: canonicalTempBytes).write(to: tempFile)
        } catch {
            XCTFail("Failed to seed temp file for stale first-state resume test: \(error.localizedDescription)")
            return
        }

        let firstDownloadingStateExpectation = expectation(description: "first downloading state captured")
        let observerLock = NSLock()
        var firstDownloadingBytes: Int64?
        await MainActor.run {
            service._testConfigureForDownloadTests(availablePacks: [pack])
            service._testSetPackStateForTests(
                PackState(
                    packId: pack.id,
                    status: .paused,
                    downloadedBytes: staleStateBytes,
                    totalBytes: pack.sizeBytes
                )
            )
            service._testSetPackStateObserverForTests { observedPackId, state in
                guard observedPackId == pack.id, state.status == .downloading else {
                    return
                }
                observerLock.lock()
                defer { observerLock.unlock() }
                guard firstDownloadingBytes == nil else {
                    return
                }
                firstDownloadingBytes = state.downloadedBytes
                firstDownloadingStateExpectation.fulfill()
            }
            service._testSetNetworkAvailabilityForTests(true)
            service._testSetAvailableSpaceForTests(Int64.max)
        }

        ControlledDownloadURLProtocol.configure(
            chunk: Data(repeating: 0xA1, count: 2048),
            chunkCount: 2_000,
            chunkDelayMs: 3
        )

        let resumeTask = Task {
            await service.resumeDownload(packId: pack.id)
        }

        await fulfillment(of: [firstDownloadingStateExpectation], timeout: 2.0)
        await service.pauseDownload(packId: pack.id)
        _ = await resumeTask.value

        observerLock.lock()
        let capturedFirstDownloadingBytes = firstDownloadingBytes
        observerLock.unlock()
        XCTAssertEqual(capturedFirstDownloadingBytes, Int64(canonicalTempBytes))

        await MainActor.run {
            service._testSetPackStateObserverForTests(nil)
        }
    }

    func testResumeDownload_WhenPausedDuringVerifyingWithOversizedTemp_ClampsInterruptedProgressToTotalBytes() async {
        let packId = "ios-resume-oversized-verifying-pause-\(UUID().uuidString)"
        let declaredSizeBytes = 1_024 * 1_024
        let oversizedTempBytes = declaredSizeBytes + (256 * 1_024)
        let oversizedPayload = Data(repeating: 0x95, count: oversizedTempBytes)
        let pack = ContentPack(
            id: packId,
            type: .medinaMap,
            displayName: "Resume Oversized Temp Clamp Test Pack",
            description: "Clamps interrupted progress when temp bytes exceed declared pack size",
            version: "1.0.0",
            sizeBytes: Int64(declaredSizeBytes),
            sha256: String(repeating: "0", count: 64),
            downloadUrl: "https://download.test/\(packId).pack",
            minAppVersion: nil,
            dependencies: nil
        )
        let service = DownloadServiceImpl.shared

        let tempDirectory = FileManager.default.temporaryDirectory
            .appendingPathComponent("pack_downloads", isDirectory: true)
        let tempFile = tempDirectory.appendingPathComponent("\(pack.id).tmp")
        do {
            try FileManager.default.createDirectory(at: tempDirectory, withIntermediateDirectories: true)
            try oversizedPayload.write(to: tempFile)
        } catch {
            XCTFail("Failed to seed oversized temp file for clamp test: \(error.localizedDescription)")
            return
        }

        await MainActor.run {
            service._testConfigureForDownloadTests(availablePacks: [pack])
            service._testSetPackStateForTests(
                PackState(
                    packId: pack.id,
                    status: .paused,
                    downloadedBytes: Int64(oversizedTempBytes),
                    totalBytes: pack.sizeBytes
                )
            )
            service._testSetAvailableSpaceForTests(Int64.max)
        }

        let pauseTriggeredExpectation = expectation(description: "pause triggered during verifying for oversized temp")
        let observerLock = NSLock()
        var hasTriggeredPause = false
        await MainActor.run {
            service._testSetPackStateObserverForTests { observedPackId, state in
                guard observedPackId == pack.id, state.status == .verifying else {
                    return
                }
                observerLock.lock()
                defer { observerLock.unlock() }
                guard !hasTriggeredPause else {
                    return
                }
                hasTriggeredPause = true
                Task { @MainActor in
                    await service.pauseDownload(packId: pack.id)
                    pauseTriggeredExpectation.fulfill()
                }
            }
        }

        let resultTask = Task {
            await service.resumeDownload(packId: pack.id)
        }
        await fulfillment(of: [pauseTriggeredExpectation], timeout: 3.0)
        let result = await resultTask.value

        await MainActor.run {
            service._testSetPackStateObserverForTests(nil)
        }

        guard case .progress(let resultPackId, let downloadedBytes, let totalBytes) = result else {
            XCTFail("Expected paused oversized-temp resume to return progress, got \(result)")
            return
        }
        XCTAssertEqual(resultPackId, pack.id)
        XCTAssertEqual(totalBytes, pack.sizeBytes)
        XCTAssertEqual(downloadedBytes, pack.sizeBytes)

        let state = (await service.packStates)[pack.id]
        XCTAssertEqual(state?.status, .paused)
        XCTAssertEqual(state?.totalBytes, pack.sizeBytes)
        XCTAssertEqual(state?.downloadedBytes, pack.sizeBytes)
    }

    func testStartDownload_WithHugePackSize_DoesNotOverflowAndReturnsInsufficientStorage() async {
        let packId = "ios-huge-pack-\(UUID().uuidString)"
        let pack = ContentPack(
            id: packId,
            type: .medinaMap,
            displayName: "Huge Pack Size Test",
            description: "Test overflow-safe storage calculation",
            version: "1.0.0",
            sizeBytes: Int64.max,
            sha256: String(repeating: "0", count: 64),
            downloadUrl: "https://download.test/\(packId).pack",
            minAppVersion: nil,
            dependencies: nil
        )
        let service = DownloadServiceImpl.shared

        await MainActor.run {
            service._testConfigureForDownloadTests(availablePacks: [pack])
            service._testSetNetworkAvailabilityForTests(true)
            service._testSetAvailableSpaceForTests(Int64.max)
        }

        ControlledDownloadURLProtocol.reset()

        let result = await service.startDownload(packId: pack.id)
        guard case .error(let resultPackId, let error) = result else {
            XCTFail("Expected insufficient storage error for huge pack, got \(result)")
            return
        }
        XCTAssertEqual(resultPackId, pack.id)
        guard case .insufficientStorage = error else {
            XCTFail("Expected insufficientStorage error, got \(error)")
            return
        }
        XCTAssertEqual(ControlledDownloadURLProtocol.requestCount, 0)
    }

    func testStartDownload_WithZeroPackSize_ReturnsInvalidPackSizeError() async {
        let packId = "ios-zero-size-pack-\(UUID().uuidString)"
        let pack = ContentPack(
            id: packId,
            type: .medinaMap,
            displayName: "Zero Size Pack Test",
            description: "Test invalid zero size metadata",
            version: "1.0.0",
            sizeBytes: 0,
            sha256: String(repeating: "0", count: 64),
            downloadUrl: "https://download.test/\(packId).pack",
            minAppVersion: nil,
            dependencies: nil
        )
        let service = DownloadServiceImpl.shared

        await MainActor.run {
            service._testConfigureForDownloadTests(availablePacks: [pack])
            service._testSetNetworkAvailabilityForTests(true)
        }

        ControlledDownloadURLProtocol.reset()

        let result = await service.startDownload(packId: pack.id)
        guard case .error(let resultPackId, let error) = result else {
            XCTFail("Expected invalid pack size error, got \(result)")
            return
        }
        XCTAssertEqual(resultPackId, pack.id)
        guard case .unknown(let message) = error else {
            XCTFail("Expected unknown invalid-size error, got \(error)")
            return
        }
        XCTAssertEqual(message, "Invalid pack size")
        XCTAssertEqual(ControlledDownloadURLProtocol.requestCount, 0)

        let state = (await service.packStates)[pack.id]
        XCTAssertEqual(state?.status, .notDownloaded)
        XCTAssertEqual(state?.downloadedBytes, 0)
        XCTAssertEqual(state?.totalBytes, 0)
    }

    func testResumeDownload_WithZeroPackSize_ReturnsInvalidPackSizeErrorAndKeepsPausedState() async {
        let packId = "ios-resume-zero-size-pack-\(UUID().uuidString)"
        let pack = ContentPack(
            id: packId,
            type: .medinaMap,
            displayName: "Resume Zero Size Pack Test",
            description: "Test invalid zero size metadata during resume",
            version: "1.0.0",
            sizeBytes: 0,
            sha256: String(repeating: "0", count: 64),
            downloadUrl: "https://download.test/\(packId).pack",
            minAppVersion: nil,
            dependencies: nil
        )
        let service = DownloadServiceImpl.shared

        await MainActor.run {
            service._testConfigureForDownloadTests(availablePacks: [pack])
            service._testSetPackStateForTests(
                PackState(
                    packId: pack.id,
                    status: .paused,
                    downloadedBytes: 128,
                    totalBytes: 256
                )
            )
            service._testSetNetworkAvailabilityForTests(true)
        }

        ControlledDownloadURLProtocol.reset()

        let result = await service.resumeDownload(packId: pack.id)
        guard case .error(let resultPackId, let error) = result else {
            XCTFail("Expected invalid pack size error on resume, got \(result)")
            return
        }
        XCTAssertEqual(resultPackId, pack.id)
        guard case .unknown(let message) = error else {
            XCTFail("Expected unknown invalid-size error, got \(error)")
            return
        }
        XCTAssertEqual(message, "Invalid pack size")
        XCTAssertEqual(ControlledDownloadURLProtocol.requestCount, 0)

        let state = (await service.packStates)[pack.id]
        XCTAssertEqual(state?.status, .paused)
        XCTAssertEqual(state?.downloadedBytes, 128)
        XCTAssertEqual(state?.totalBytes, 256)
    }

    func testResetDownloadStateForTests_RestoresDefaultPacksAndStates() async {
        let service = DownloadServiceImpl.shared
        let customPack = makeTestPack(packId: "ios-reset-\(UUID().uuidString)")

        await MainActor.run {
            service._testConfigureForDownloadTests(availablePacks: [customPack])
        }

        let configuredAvailable = await service.availablePacks
        XCTAssertEqual(configuredAvailable.map(\.id), [customPack.id])
        let configuredStates = await service.packStates
        XCTAssertEqual(configuredStates.count, 1)
        XCTAssertEqual(configuredStates[customPack.id]?.status, .notDownloaded)

        await MainActor.run {
            service._testResetDownloadStateForTests()
        }

        let resetAvailable = await service.availablePacks
        XCTAssertFalse(resetAvailable.isEmpty)
        XCTAssertTrue(resetAvailable.contains { $0.id == "medina-map" })
        XCTAssertFalse(resetAvailable.contains { $0.id == customPack.id })

        let resetStates = await service.packStates
        XCTAssertEqual(resetStates.count, resetAvailable.count)
        XCTAssertNil(resetStates[customPack.id])
        for pack in resetAvailable {
            XCTAssertEqual(resetStates[pack.id]?.status, .notDownloaded)
            XCTAssertEqual(resetStates[pack.id]?.downloadedBytes, 0)
            XCTAssertEqual(resetStates[pack.id]?.totalBytes, 0)
        }
    }

    func testResetDownloadStateForTests_IsIdempotent() async {
        let service = DownloadServiceImpl.shared

        await MainActor.run {
            service._testResetDownloadStateForTests()
            service._testResetDownloadStateForTests()
        }

        let availablePacks = await service.availablePacks
        let states = await service.packStates
        XCTAssertEqual(states.count, availablePacks.count)

        for pack in availablePacks {
            XCTAssertEqual(states[pack.id]?.status, .notDownloaded)
        }
    }

    private func makeTestPack(
        packId: String,
        sizeBytes: Int64 = 4_096_000,
        sha256: String = String(repeating: "0", count: 64),
        downloadUrl: String? = nil
    ) -> ContentPack {
        ContentPack(
            id: packId,
            type: .medinaMap,
            displayName: "Test Pack",
            description: "Test download pack",
            version: "1.0.0",
            sizeBytes: sizeBytes,
            sha256: sha256,
            downloadUrl: downloadUrl ?? "https://download.test/\(packId).pack",
            minAppVersion: nil,
            dependencies: nil
        )
    }

    private func makeDormantTrackedDownloadTask(packId: String) -> URLSessionDownloadTask {
        let url = URL(string: "https://download.test/\(packId).pack") ?? URL(fileURLWithPath: "/")
        return URLSession.shared.downloadTask(with: url)
    }

    private func waitForCondition(
        _ description: String,
        timeoutMs: Int = 3_000,
        pollIntervalMs: UInt64 = 20,
        condition: @escaping () -> Bool
    ) async {
        let start = Date()
        while Date().timeIntervalSince(start) * 1000 < Double(timeoutMs) {
            if condition() {
                return
            }
            try? await Task.sleep(nanoseconds: pollIntervalMs * 1_000_000)
        }

        XCTAssertTrue(condition(), "Timed out waiting for \(description)")
    }

    private func readUTF8Contents(from url: URL) throws -> String {
        let handle = try FileHandle(forReadingFrom: url)
        defer {
            try? handle.close()
        }

        var data = Data()
        while true {
            let chunk = try handle.read(upToCount: 4 * 1024) ?? Data()
            if chunk.isEmpty {
                break
            }
            data.append(chunk)
        }

        guard let contents = String(data: data, encoding: .utf8) else {
            throw NSError(domain: "DownloadServiceTests", code: 1, userInfo: [
                NSLocalizedDescriptionKey: "Failed to decode UTF-8 contents from \(url.lastPathComponent)"
            ])
        }
        return contents
    }

    private func sha256Hex(_ data: Data) -> String {
        var digest = [UInt8](repeating: 0, count: Int(CC_SHA256_DIGEST_LENGTH))
        data.withUnsafeBytes { buffer in
            _ = CC_SHA256(buffer.baseAddress, CC_LONG(data.count), &digest)
        }
        return digest.map { String(format: "%02x", $0) }.joined()
    }
}

private final class ControlledDownloadURLProtocol: URLProtocol {
    private struct Configuration {
        let chunk: Data
        let chunkCount: Int
        let chunkDelayMs: UInt64
        let payload: Data?
        let honorRange: Bool
        let forcedErrorCode: URLError.Code?
        let ignoreStopLoading: Bool
    }

    private static let lock = NSLock()
    private static var configuration = Configuration(
        chunk: Data([0x00]),
        chunkCount: 1,
        chunkDelayMs: 0,
        payload: nil,
        honorRange: false,
        forcedErrorCode: nil,
        ignoreStopLoading: false
    )
    private static var _requestCount = 0
    private static var _lastRangeHeader: String?

    private let stateLock = NSLock()
    private var stopped = false

    static var requestCount: Int {
        lock.lock()
        defer { lock.unlock() }
        return _requestCount
    }

    static var lastRangeHeader: String? {
        lock.lock()
        defer { lock.unlock() }
        return _lastRangeHeader
    }

    static func configure(chunk: Data, chunkCount: Int, chunkDelayMs: UInt64) {
        lock.lock()
        configuration = Configuration(
            chunk: chunk,
            chunkCount: chunkCount,
            chunkDelayMs: chunkDelayMs,
            payload: nil,
            honorRange: false,
            forcedErrorCode: nil,
            ignoreStopLoading: false
        )
        _requestCount = 0
        _lastRangeHeader = nil
        lock.unlock()
    }

    static func configure(
        payload: Data,
        chunkDelayMs: UInt64 = 0,
        honorRange: Bool = false,
        ignoreStopLoading: Bool = false
    ) {
        lock.lock()
        configuration = Configuration(
            chunk: payload,
            chunkCount: 1,
            chunkDelayMs: chunkDelayMs,
            payload: payload,
            honorRange: honorRange,
            forcedErrorCode: nil,
            ignoreStopLoading: ignoreStopLoading
        )
        _requestCount = 0
        _lastRangeHeader = nil
        lock.unlock()
    }

    static func configureError(code: URLError.Code) {
        lock.lock()
        configuration = Configuration(
            chunk: Data([0x00]),
            chunkCount: 1,
            chunkDelayMs: 0,
            payload: nil,
            honorRange: false,
            forcedErrorCode: code,
            ignoreStopLoading: false
        )
        _requestCount = 0
        _lastRangeHeader = nil
        lock.unlock()
    }

    static func reset() {
        configure(chunk: Data([0x00]), chunkCount: 1, chunkDelayMs: 0)
    }

    override class func canInit(with request: URLRequest) -> Bool {
        request.url?.host == "download.test"
    }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest {
        request
    }

    override func startLoading() {
        let rangeHeader = request.value(forHTTPHeaderField: "Range")
        Self.lock.lock()
        Self._requestCount += 1
        Self._lastRangeHeader = rangeHeader
        let config = Self.configuration
        Self.lock.unlock()

        guard let url = request.url else {
            client?.urlProtocol(self, didFailWithError: URLError(.badURL))
            return
        }

        Thread.detachNewThread { [weak self] in
            guard let self else { return }

            if let forcedErrorCode = config.forcedErrorCode {
                self.client?.urlProtocol(self, didFailWithError: URLError(forcedErrorCode))
                return
            }

            if let payload = config.payload {
                var responsePayload = payload
                var statusCode = 200
                var headerFields: [String: String] = [
                    "Cache-Control": "no-store"
                ]

                if config.honorRange,
                   let rangeHeader,
                   let rangeStart = Self.parseRangeStart(rangeHeader),
                   rangeStart < payload.count {
                    statusCode = 206
                    responsePayload = payload.subdata(in: rangeStart..<payload.count)
                    headerFields["Content-Range"] = "bytes \(rangeStart)-\(payload.count - 1)/\(payload.count)"
                }
                headerFields["Content-Length"] = "\(responsePayload.count)"

                let response = HTTPURLResponse(
                    url: url,
                    statusCode: statusCode,
                    httpVersion: "HTTP/1.1",
                    headerFields: headerFields
                )!
                self.client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)

                if !config.ignoreStopLoading && self.isStopped {
                    self.client?.urlProtocol(self, didFailWithError: URLError(.cancelled))
                    return
                }

                if !responsePayload.isEmpty {
                    self.client?.urlProtocol(self, didLoad: responsePayload)
                }
                if config.chunkDelayMs > 0 {
                    Thread.sleep(forTimeInterval: Double(config.chunkDelayMs) / 1000.0)
                }

                if !config.ignoreStopLoading && self.isStopped {
                    self.client?.urlProtocol(self, didFailWithError: URLError(.cancelled))
                    return
                }

                self.client?.urlProtocolDidFinishLoading(self)
                return
            }

            let totalBytes = config.chunk.count * config.chunkCount
            let response = HTTPURLResponse(
                url: url,
                statusCode: 200,
                httpVersion: "HTTP/1.1",
                headerFields: [
                    "Content-Length": "\(totalBytes)",
                    "Cache-Control": "no-store"
                ]
            )!

            self.client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)

            for _ in 0..<config.chunkCount {
                if !config.ignoreStopLoading && self.isStopped {
                    self.client?.urlProtocol(self, didFailWithError: URLError(.cancelled))
                    return
                }

                self.client?.urlProtocol(self, didLoad: config.chunk)
                if config.chunkDelayMs > 0 {
                    Thread.sleep(forTimeInterval: Double(config.chunkDelayMs) / 1000.0)
                }
            }

            if !config.ignoreStopLoading && self.isStopped {
                self.client?.urlProtocol(self, didFailWithError: URLError(.cancelled))
                return
            }

            self.client?.urlProtocolDidFinishLoading(self)
        }
    }

    private static func parseRangeStart(_ header: String) -> Int? {
        guard header.hasPrefix("bytes=") else {
            return nil
        }
        let rangeValue = header.dropFirst("bytes=".count)
        guard let startToken = rangeValue.split(separator: "-", maxSplits: 1, omittingEmptySubsequences: false).first,
              let start = Int(startToken),
              start >= 0 else {
            return nil
        }
        return start
    }

    override func stopLoading() {
        stateLock.lock()
        stopped = true
        stateLock.unlock()
    }

    private var isStopped: Bool {
        stateLock.lock()
        defer { stateLock.unlock() }
        return stopped
    }
}

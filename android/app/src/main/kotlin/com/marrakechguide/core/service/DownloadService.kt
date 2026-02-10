package com.marrakechguide.core.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.marrakechguide.core.model.ContentPack
import com.marrakechguide.core.model.DownloadError
import com.marrakechguide.core.model.DownloadPreferences
import com.marrakechguide.core.model.DownloadResult
import com.marrakechguide.core.model.PackManifest
import com.marrakechguide.core.model.PackState
import com.marrakechguide.core.model.PackStatus
import com.marrakechguide.core.model.PackType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for managing content pack downloads.
 *
 * Features:
 * - Resumable downloads with Range header support
 * - SHA256 verification after download
 * - Atomic installation (temp -> verify -> move)
 * - Disk space checks before download
 * - Wi-Fi only option
 */
interface DownloadService {
    /** Current state of all packs */
    val packStates: StateFlow<Map<String, PackState>>

    /** User download preferences */
    val preferences: StateFlow<DownloadPreferences>

    /** Available packs from manifest */
    val availablePacks: StateFlow<List<ContentPack>>

    /** Start downloading a pack */
    suspend fun startDownload(packId: String): DownloadResult

    /** Pause an active download */
    suspend fun pauseDownload(packId: String)

    /** Resume a paused download */
    suspend fun resumeDownload(packId: String): DownloadResult

    /** Cancel a download and remove partial data */
    suspend fun cancelDownload(packId: String)

    /** Remove an installed pack */
    suspend fun removePack(packId: String)

    /** Check for pack updates */
    suspend fun checkForUpdates()

    /** Update download preferences */
    fun updatePreferences(preferences: DownloadPreferences)

    /** Get available disk space in bytes */
    fun getAvailableSpace(): Long

    /** Check if network is available (respecting Wi-Fi only preference) */
    fun isNetworkAvailable(): Boolean
}

/**
 * Implementation of DownloadService.
 */
@Singleton
class DownloadServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : DownloadService {

    companion object {
        private const val TAG = "DownloadService"
        private const val PACKS_DIR = "packs"
        private const val TEMP_DIR = "packs_temp"
        private const val RESUME_SUFFIX = ".resume"
        private const val BUFFER_SIZE = 8192
        private const val PROGRESS_UPDATE_INTERVAL_BYTES = 65536 // Update every 64KB
    }

    private val packsDir: File = File(context.filesDir, PACKS_DIR).apply { mkdirs() }
    private val tempDir: File = File(context.cacheDir, TEMP_DIR).apply { mkdirs() }

    private val _packStates = MutableStateFlow<Map<String, PackState>>(emptyMap())
    override val packStates: StateFlow<Map<String, PackState>> = _packStates.asStateFlow()

    private val _preferences = MutableStateFlow(DownloadPreferences())
    override val preferences: StateFlow<DownloadPreferences> = _preferences.asStateFlow()

    private val _availablePacks = MutableStateFlow(getDefaultPacks())
    override val availablePacks: StateFlow<List<ContentPack>> = _availablePacks.asStateFlow()

    private val activeDownloads = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val activeDownloadSessions = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val downloadSessionCounter = java.util.concurrent.atomic.AtomicLong(0L)
    private val explicitRemoveRequests = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val pausedTargetVersions = java.util.concurrent.ConcurrentHashMap<String, String>()
    @VisibleForTesting
    internal var availableSpaceOverrideBytesForTest: Long? = null
    @VisibleForTesting
    internal var onAfterInstallAttemptForTest: ((String, Boolean) -> Unit)? = null
    @VisibleForTesting
    internal var forceInstallFailureForTest: Boolean = false
    @VisibleForTesting
    internal var forceInstallFailureAfterStagingForTest: Boolean = false
    @VisibleForTesting
    internal var forceManifestSwapFailureForTest: Boolean = false

    init {
        // Initialize pack states from installed packs
        loadInstalledPacks()
    }

    override suspend fun startDownload(packId: String): DownloadResult {
        val pack = _availablePacks.value.find { it.id == packId }
            ?: return DownloadResult.Error(packId, DownloadError.Unknown("Pack not found"))

        // Check network
        if (!isNetworkAvailable()) {
            return DownloadResult.Error(packId, DownloadError.NetworkUnavailable)
        }

        // Check disk space (need 2x for temp + final)
        val requiredFreeSpace = requiredFreshInstallSpaceBytes(pack.sizeBytes)
        if (getAvailableSpace() < requiredFreeSpace) {
            return DownloadResult.Error(packId, DownloadError.InsufficientStorage)
        }

        // Check if already downloading
        if (packId in activeDownloads) {
            Log.d(TAG, "Download already in progress for $packId")
            return DownloadResult.Error(packId, DownloadError.Unknown("Download already in progress"))
        }

        // A new explicit download attempt supersedes any stale remove intent marker.
        explicitRemoveRequests.remove(packId)
        return performDownload(pack, resumeFrom = 0)
    }

    override suspend fun pauseDownload(packId: String) {
        val removed = activeDownloads.remove(packId)
        activeDownloadSessions.remove(packId)
        if (!removed) {
            Log.w(TAG, "Pause requested with no active download: $packId")
            return
        }
        _availablePacks.value.find { it.id == packId }?.let { pack ->
            pausedTargetVersions[packId] = pack.version
        }
        updatePackState(packId) { it.copy(status = PackStatus.PAUSED) }
        Log.i(TAG, "Paused download: $packId")
    }

    override suspend fun resumeDownload(packId: String): DownloadResult {
        val state = _packStates.value[packId]
            ?: return DownloadResult.Error(packId, DownloadError.Unknown("Pack state not found"))

        if (!state.canResume) {
            return startDownload(packId)
        }

        val pack = _availablePacks.value.find { it.id == packId }
            ?: return DownloadResult.Error(packId, DownloadError.Unknown("Pack not found"))

        if (!isNetworkAvailable()) {
            return DownloadResult.Error(packId, DownloadError.NetworkUnavailable)
        }

        // Check disk space for remaining download bytes + staged install copy.
        val tempLength = getTempFile(packId).takeIf { it.exists() }?.length() ?: 0L
        val requiredFreeSpace = requiredResumeSpaceBytes(pack.sizeBytes, tempLength)
        if (getAvailableSpace() < requiredFreeSpace) {
            return DownloadResult.Error(packId, DownloadError.InsufficientStorage)
        }

        explicitRemoveRequests.remove(packId)
        return performDownload(pack, resumeFrom = state.downloadedBytes)
    }

    override suspend fun cancelDownload(packId: String) {
        activeDownloads.remove(packId)
        activeDownloadSessions.remove(packId)
        explicitRemoveRequests.remove(packId)
        pausedTargetVersions.remove(packId)

        // Clean up temp files
        getTempFile(packId).delete()
        getResumeFile(packId).delete()

        val pack = _availablePacks.value.find { it.id == packId }
        val restoredInstalledState = pack?.let { restoreInstalledStateFromDisk(it) } == true
        if (!restoredInstalledState) {
            updatePackState(packId) {
                it.copy(
                    status = PackStatus.NOT_DOWNLOADED,
                    downloadedBytes = 0,
                    totalBytes = pack?.sizeBytes ?: 0,
                    errorMessage = null,
                    installedVersion = null,
                    installedAt = null
                )
            }
        }
        Log.i(TAG, "Cancelled download: $packId")
    }

    override suspend fun removePack(packId: String) {
        explicitRemoveRequests.add(packId)
        activeDownloads.remove(packId)
        activeDownloadSessions.remove(packId)
        pausedTargetVersions.remove(packId)
        withContext(Dispatchers.IO) {
            val packDir = File(packsDir, packId)
            packDir.deleteRecursively()
            getTempFile(packId).delete()
            getResumeFile(packId).delete()
        }

        updatePackState(packId) {
            PackState(
                packId = packId,
                status = PackStatus.NOT_DOWNLOADED,
                installedVersion = null,
                installedAt = null
            )
        }
        Log.i(TAG, "Removed pack: $packId")
    }

    override suspend fun checkForUpdates() {
        // In production, this would fetch from a remote manifest
        // For now, check installed versions against available packs
        val currentStates = _packStates.value.toMutableMap()
        val packsNeedingPartialCleanup = mutableListOf<String>()

        for (pack in _availablePacks.value) {
            val state = currentStates[pack.id]
            if (state == null || state.installedVersion == null) {
                continue
            }
            if (state.status in listOf(PackStatus.DOWNLOADING, PackStatus.VERIFYING, PackStatus.INSTALLING)) {
                continue
            }
            if (state.installedVersion != pack.version) {
                val pausedTargetVersion = pausedTargetVersions[pack.id]
                val hasResumablePausedUpdateForCurrentManifest =
                    state.status == PackStatus.PAUSED &&
                    pausedTargetVersion == pack.version &&
                    state.totalBytes == pack.sizeBytes &&
                    state.downloadedBytes in 1..pack.sizeBytes &&
                    getTempFile(pack.id).exists()
                if (hasResumablePausedUpdateForCurrentManifest) {
                    // Preserve active paused progress when it still targets the current manifest pack.
                    continue
                }
                currentStates[pack.id] = state.copy(
                    status = PackStatus.UPDATE_AVAILABLE,
                    downloadedBytes = 0L,
                    totalBytes = pack.sizeBytes,
                    errorMessage = null
                )
                pausedTargetVersions.remove(pack.id)
                // If update state no longer matches current manifest, drop stale partial bytes.
                if (state.status == PackStatus.PAUSED || getTempFile(pack.id).exists()) {
                    packsNeedingPartialCleanup += pack.id
                }
            } else if (state.status != PackStatus.PAUSED) {
                pausedTargetVersions.remove(pack.id)
            }
        }

        if (packsNeedingPartialCleanup.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                packsNeedingPartialCleanup.forEach { packId ->
                    getTempFile(packId).delete()
                    getResumeFile(packId).delete()
                }
            }
        }

        _packStates.value = currentStates
        Log.i(TAG, "Checked for updates")
    }

    override fun updatePreferences(preferences: DownloadPreferences) {
        _preferences.value = preferences
    }

    override fun getAvailableSpace(): Long {
        availableSpaceOverrideBytesForTest?.let { return it }
        return try {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBytes
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get available space", e)
            0L
        }
    }

    override fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (!hasInternet) return false

        // Check Wi-Fi only preference
        if (_preferences.value.wifiOnly) {
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        }

        return true
    }

    @VisibleForTesting
    internal suspend fun performDownloadForTest(pack: ContentPack, resumeFrom: Long): DownloadResult {
        return performDownload(pack, resumeFrom)
    }

    @VisibleForTesting
    internal fun deactivateDownloadForTest(packId: String) {
        activeDownloads.remove(packId)
        activeDownloadSessions.remove(packId)
    }

    @VisibleForTesting
    internal fun replaceAvailablePacksForTest(packs: List<ContentPack>) {
        _availablePacks.value = packs
    }

    @VisibleForTesting
    internal fun replaceAvailablePacksAndReloadForTest(packs: List<ContentPack>) {
        _availablePacks.value = packs
        loadInstalledPacks()
    }

    private suspend fun performDownload(pack: ContentPack, resumeFrom: Long): DownloadResult {
        if (!activeDownloads.add(pack.id)) {
            Log.d(TAG, "Download already in progress for ${pack.id}")
            return DownloadResult.Error(pack.id, DownloadError.Unknown("Download already in progress"))
        }
        val sessionId = beginDownloadSession(pack.id)
        pausedTargetVersions[pack.id] = pack.version
        updatePackState(pack.id) {
            it.copy(
                status = PackStatus.DOWNLOADING,
                downloadedBytes = resumeFrom,
                totalBytes = pack.sizeBytes,
                errorMessage = null
            )
        }

        return withContext(Dispatchers.IO) {
            persistPausedTargetVersion(pack.id, pack.version)
            val hadExistingInstall = hasInstalledPackOnDisk(pack.id)
            try {
                val tempFile = getTempFile(pack.id)
                val hasCompleteTempFile = resumeFrom >= pack.sizeBytes && tempFile.exists() && tempFile.length() >= pack.sizeBytes
                if (hasCompleteTempFile) {
                    if (tempFile.length() > pack.sizeBytes) {
                        runCatching {
                            RandomAccessFile(tempFile, "rw").use { file ->
                                // Old resume logic could leave stale tail bytes; trim before checksum.
                                file.setLength(pack.sizeBytes)
                            }
                        }.onFailure { error ->
                            Log.w(TAG, "Failed to trim oversized temp file for ${pack.id}", error)
                        }
                    }
                    Log.i(TAG, "Resuming from complete temp file for ${pack.id}; skipping network")
                    return@withContext finalizeDownloadedPack(
                        pack = pack,
                        tempFile = tempFile,
                        hadExistingInstall = hadExistingInstall,
                        sessionId = sessionId
                    )
                }
                val hasResumeTempFile = resumeFrom > 0 && tempFile.exists()
                val canAttemptResume = hasResumeTempFile && tempFile.length() >= resumeFrom
                if (hasResumeTempFile && !canAttemptResume) {
                    Log.w(
                        TAG,
                        "Resume offset $resumeFrom exceeds temp file length ${tempFile.length()} for ${pack.id}; restarting download from zero"
                    )
                }
                val connection = URL(pack.downloadUrl).openConnection() as HttpURLConnection

                // Set up resumable download
                if (canAttemptResume) {
                    connection.setRequestProperty("Range", "bytes=$resumeFrom-")
                }

                connection.connectTimeout = 30_000
                connection.readTimeout = 30_000

                val responseCode = connection.responseCode
                if (!isDownloadSessionActive(pack.id, sessionId)) {
                    connection.disconnect()
                    return@withContext interruptionProgress(
                        pack = pack,
                        totalRead = getTempFile(pack.id).takeIf { it.exists() }?.length() ?: 0L,
                        sessionId = sessionId
                    )
                }
                if (responseCode !in listOf(200, 206)) {
                    connection.disconnect()
                    endDownloadSession(pack.id, sessionId)
                    pausedTargetVersions.remove(pack.id)
                    getResumeFile(pack.id).delete()
                    if (hadExistingInstall) {
                        markUpdateAvailableWithError(pack, "HTTP $responseCode")
                    } else {
                        updatePackState(pack.id) {
                            it.copy(status = PackStatus.FAILED, errorMessage = "HTTP $responseCode")
                        }
                    }
                    return@withContext DownloadResult.Error(pack.id, DownloadError.HttpError(responseCode))
                }

                // Download to temp file - use RandomAccessFile for resume, FileOutputStream for new
                val randomAccessFile: RandomAccessFile?
                val fileOutput: FileOutputStream?

                val isResumingDownload = canAttemptResume && responseCode == 206
                if (resumeFrom > 0 && !isResumingDownload) {
                    // Server ignored Range request (or partial file was missing), restart progress from zero.
                    updatePackState(pack.id) {
                        it.copy(downloadedBytes = 0, totalBytes = pack.sizeBytes)
                    }
                }

                if (isResumingDownload) {
                    randomAccessFile = RandomAccessFile(tempFile, "rw").apply {
                        // Drop stale trailing bytes before appending resumed content.
                        setLength(resumeFrom)
                        seek(resumeFrom)
                    }
                    fileOutput = null
                } else {
                    randomAccessFile = null
                    fileOutput = FileOutputStream(tempFile)
                }

                try {
                    var totalRead = if (isResumingDownload) resumeFrom else 0L
                    connection.inputStream.use { inputStream ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytesRead: Int
                        var lastProgressUpdate = totalRead

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            if (!isDownloadSessionActive(pack.id, sessionId)) {
                                return@withContext interruptionProgress(pack, totalRead, sessionId)
                            }
                            if (randomAccessFile != null) {
                                randomAccessFile.write(buffer, 0, bytesRead)
                            } else {
                                fileOutput?.write(buffer, 0, bytesRead)
                            }
                            totalRead += bytesRead

                            // Update progress periodically
                            if (totalRead - lastProgressUpdate >= PROGRESS_UPDATE_INTERVAL_BYTES) {
                                updatePackState(pack.id) {
                                    it.copy(downloadedBytes = clampDownloadedBytes(totalRead, pack.sizeBytes))
                                }
                                lastProgressUpdate = totalRead
                            }

                            // Honor pause/cancel after persisting in-flight bytes.
                            if (!isDownloadSessionActive(pack.id, sessionId)) {
                                return@withContext interruptionProgress(pack, totalRead, sessionId)
                            }
                        }
                    }

                    if (!isDownloadSessionActive(pack.id, sessionId)) {
                        return@withContext interruptionProgress(pack, totalRead, sessionId)
                    }
                } finally {
                    runCatching { randomAccessFile?.close() }
                    runCatching { fileOutput?.close() }
                    connection.disconnect()
                }

                return@withContext finalizeDownloadedPack(
                    pack = pack,
                    tempFile = tempFile,
                    hadExistingInstall = hadExistingInstall,
                    sessionId = sessionId
                )

            } catch (e: Exception) {
                if (!isDownloadSessionActive(pack.id, sessionId)) {
                    return@withContext interruptionProgress(pack, getTempFile(pack.id).length(), sessionId)
                }
                Log.e(TAG, "Download failed for ${pack.id}", e)
                endDownloadSession(pack.id, sessionId)
                pausedTargetVersions.remove(pack.id)
                getResumeFile(pack.id).delete()
                val currentStatus = _packStates.value[pack.id]?.status
                if (hadExistingInstall && currentStatus != PackStatus.INSTALLING) {
                    markUpdateAvailableWithError(pack, e.message)
                } else {
                    updatePackState(pack.id) {
                        it.copy(status = PackStatus.FAILED, errorMessage = e.message)
                    }
                }
                DownloadResult.Error(pack.id, DownloadError.Unknown(e.message))
            }
        }
    }

    private fun interruptionProgress(pack: ContentPack, totalRead: Long, sessionId: Long): DownloadResult.Progress {
        val activeSessionId = activeDownloadSessions[pack.id]
        if (activeSessionId != null && activeSessionId != sessionId) {
            Log.d(TAG, "Ignoring stale interruption state update for ${pack.id} (session=$sessionId, active=$activeSessionId)")
            return DownloadResult.Progress(pack.id, 0, pack.sizeBytes)
        }
        val currentState = _packStates.value[pack.id]
        val tempFileExists = getTempFile(pack.id).exists()
        return if (currentState?.status == PackStatus.NOT_DOWNLOADED) {
            val wasExplicitlyRemoved = explicitRemoveRequests.remove(pack.id)
            pausedTargetVersions.remove(pack.id)
            // Remove/cancel can race with in-flight writes from an already-open stream.
            // Re-delete temp artifacts to keep removed packs from resurfacing as paused.
            runCatching { getTempFile(pack.id).delete() }
            runCatching { getResumeFile(pack.id).delete() }
            if (wasExplicitlyRemoved) {
                // User-requested removal should win over any late install completion race.
                cleanupInstalledPackArtifacts(pack.id)
            } else {
                restoreInstalledStateFromDisk(pack)
            }
            DownloadResult.Progress(pack.id, 0, pack.sizeBytes)
        } else if (!tempFileExists) {
            pausedTargetVersions.remove(pack.id)
            runCatching { getResumeFile(pack.id).delete() }
            DownloadResult.Progress(pack.id, 0, pack.sizeBytes)
        } else {
            persistPausedTargetVersion(pack.id, pack.version)
            val clampedBytes = clampDownloadedBytes(totalRead, pack.sizeBytes)
            updatePackState(pack.id) {
                it.copy(downloadedBytes = clampedBytes)
            }
            DownloadResult.Progress(pack.id, clampedBytes, pack.sizeBytes)
        }
    }

    private fun finalizeDownloadedPack(
        pack: ContentPack,
        tempFile: File,
        hadExistingInstall: Boolean,
        sessionId: Long
    ): DownloadResult {
        if (!isDownloadSessionActive(pack.id, sessionId)) {
            return interruptionProgress(pack, tempFile.length(), sessionId)
        }

        updatePackState(pack.id) { it.copy(status = PackStatus.VERIFYING) }
        if (!verifyChecksum(tempFile, pack.sha256)) {
            if (!isDownloadSessionActive(pack.id, sessionId)) {
                return interruptionProgress(pack, tempFile.length(), sessionId)
            }
            tempFile.delete()
            endDownloadSession(pack.id, sessionId)
            pausedTargetVersions.remove(pack.id)
            getResumeFile(pack.id).delete()
            if (hadExistingInstall) {
                markUpdateAvailableWithError(pack, "Verification failed")
            } else {
                updatePackState(pack.id) {
                    it.copy(status = PackStatus.FAILED, errorMessage = "Verification failed")
                }
            }
            return DownloadResult.Error(pack.id, DownloadError.VerificationFailed)
        }

        if (!isDownloadSessionActive(pack.id, sessionId)) {
            return interruptionProgress(pack, tempFile.length(), sessionId)
        }

        updatePackState(pack.id) { it.copy(status = PackStatus.INSTALLING) }
        val installResult = installPack(pack, tempFile)
        onAfterInstallAttemptForTest?.invoke(pack.id, installResult)

        if (!isDownloadSessionActive(pack.id, sessionId)) {
            if (!hadExistingInstall) {
                cleanupInstalledPackArtifacts(pack.id)
            }
            return interruptionProgress(pack, tempFile.length(), sessionId)
        }

        return if (installResult) {
            tempFile.delete()
            getResumeFile(pack.id).delete()
            endDownloadSession(pack.id, sessionId)
            pausedTargetVersions.remove(pack.id)
            updatePackState(pack.id) {
                PackState(
                    packId = pack.id,
                    status = PackStatus.INSTALLED,
                    downloadedBytes = pack.sizeBytes,
                    totalBytes = pack.sizeBytes,
                    installedVersion = pack.version,
                    installedAt = System.currentTimeMillis()
                )
            }
            Log.i(TAG, "Successfully installed pack: ${pack.id}")
            DownloadResult.Success(pack.id)
        } else {
            tempFile.delete()
            endDownloadSession(pack.id, sessionId)
            pausedTargetVersions.remove(pack.id)
            getResumeFile(pack.id).delete()
            if (hadExistingInstall) {
                markUpdateAvailableWithError(pack, "Installation failed")
            } else {
                cleanupInstalledPackArtifacts(pack.id)
                updatePackState(pack.id) {
                    it.copy(status = PackStatus.FAILED, errorMessage = "Installation failed")
                }
            }
            DownloadResult.Error(pack.id, DownloadError.InstallationFailed)
        }
    }

    private fun cleanupInstalledPackArtifacts(packId: String) {
        runCatching { File(packsDir, packId).deleteRecursively() }
            .onFailure { error ->
                Log.w(TAG, "Failed to clean up installed artifacts for $packId", error)
            }
    }

    private fun markUpdateAvailableWithError(pack: ContentPack, errorMessage: String?) {
        val restoredInstalledState = restoreInstalledStateFromDisk(pack)
        updatePackState(pack.id) { current ->
            if (restoredInstalledState) {
                val hasUpdateAvailable = current.installedVersion != pack.version
                current.copy(
                    status = if (hasUpdateAvailable) PackStatus.UPDATE_AVAILABLE else PackStatus.INSTALLED,
                    downloadedBytes = if (hasUpdateAvailable) 0 else pack.sizeBytes,
                    totalBytes = pack.sizeBytes,
                    errorMessage = errorMessage
                )
            } else {
                current.copy(
                    status = PackStatus.FAILED,
                    downloadedBytes = 0,
                    totalBytes = pack.sizeBytes,
                    errorMessage = errorMessage,
                    installedVersion = null,
                    installedAt = null
                )
            }
        }
    }

    private fun restoreInstalledStateFromDisk(pack: ContentPack): Boolean {
        if (!hasInstalledPackOnDisk(pack.id)) return false
        val manifestFile = File(File(packsDir, pack.id), "manifest.json")

        return runCatching {
            val metadata = parseInstalledPackMetadata(manifestFile.readText())
            updatePackState(pack.id) { current ->
                val resolvedInstalledVersion = metadata.version ?: current.installedVersion ?: pack.version
                val resolvedStatus = if (resolvedInstalledVersion != pack.version) {
                    PackStatus.UPDATE_AVAILABLE
                } else {
                    PackStatus.INSTALLED
                }
                val resolvedDownloadedBytes = if (resolvedStatus == PackStatus.UPDATE_AVAILABLE) {
                    0L
                } else {
                    pack.sizeBytes
                }
                PackState(
                    packId = pack.id,
                    status = resolvedStatus,
                    downloadedBytes = resolvedDownloadedBytes,
                    totalBytes = pack.sizeBytes,
                    errorMessage = null,
                    installedVersion = resolvedInstalledVersion,
                    installedAt = metadata.installedAt ?: current.installedAt ?: System.currentTimeMillis()
                )
            }
            true
        }.onFailure { error ->
            Log.w(TAG, "Failed to restore installed state for ${pack.id}", error)
        }.getOrDefault(false)
    }

    private fun verifyChecksum(file: File, expectedSha256: String): Boolean {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            actualHash.equals(expectedSha256, ignoreCase = true)
        } catch (e: Exception) {
            Log.e(TAG, "Checksum verification failed", e)
            false
        }
    }

    private fun installPack(pack: ContentPack, tempFile: File): Boolean {
        if (forceInstallFailureForTest) {
            Log.w(TAG, "Forced install failure for ${pack.id} (test hook)")
            return false
        }

        return try {
            val packDir = File(packsDir, pack.id)
            packDir.mkdirs()

            val destFile = File(packDir, "data.pack")
            val stagedDataFile = File(packDir, "data.pack.staged")
            val manifestFile = File(packDir, "manifest.json")
            val stagedManifestFile = File(packDir, "manifest.json.staged")
            val backupDataFile = File(packDir, "data.pack.backup")
            val backupManifestFile = File(packDir, "manifest.json.backup")
            val installedAt = System.currentTimeMillis()

            // Stage content first so interrupted writes cannot corrupt active installed files.
            stagedDataFile.delete()
            stagedManifestFile.delete()
            backupDataFile.delete()
            backupManifestFile.delete()
            tempFile.copyTo(stagedDataFile, overwrite = true)
            stagedManifestFile.writeText(
                """{"id":"${pack.id}","version":"${pack.version}","installedAt":$installedAt}"""
            )

            if (forceInstallFailureAfterStagingForTest) {
                Log.w(TAG, "Forced install failure after staging for ${pack.id} (test hook)")
                return false
            }

            if (destFile.exists()) {
                destFile.copyTo(backupDataFile, overwrite = true)
            }
            if (manifestFile.exists()) {
                manifestFile.copyTo(backupManifestFile, overwrite = true)
            }

            var dataSwapped = false
            try {
                atomicMoveReplace(stagedDataFile, destFile)
                dataSwapped = true

                if (forceManifestSwapFailureForTest) {
                    throw IOException("Forced manifest swap failure for ${pack.id} (test hook)")
                }

                atomicMoveReplace(stagedManifestFile, manifestFile)
            } catch (e: Exception) {
                // Best-effort rollback: if data was already swapped, restore old files from backups.
                if (dataSwapped && backupDataFile.exists()) {
                    runCatching { atomicMoveReplace(backupDataFile, destFile) }
                        .onFailure { rollbackError ->
                            Log.w(TAG, "Failed to restore data backup for ${pack.id}", rollbackError)
                        }
                }
                if (backupManifestFile.exists()) {
                    runCatching { atomicMoveReplace(backupManifestFile, manifestFile) }
                        .onFailure { rollbackError ->
                            Log.w(TAG, "Failed to restore manifest backup for ${pack.id}", rollbackError)
                        }
                }
                throw e
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Installation failed for ${pack.id}", e)
            false
        } finally {
            val packDir = File(packsDir, pack.id)
            runCatching { File(packDir, "data.pack.staged").delete() }
            runCatching { File(packDir, "manifest.json.staged").delete() }
            runCatching { File(packDir, "data.pack.backup").delete() }
            runCatching { File(packDir, "manifest.json.backup").delete() }
        }
    }

    private fun atomicMoveReplace(source: File, destination: File) {
        val sourcePath = source.toPath()
        val destinationPath = destination.toPath()
        try {
            Files.move(
                sourcePath,
                destinationPath,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                sourcePath,
                destinationPath,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: UnsupportedOperationException) {
            Files.move(
                sourcePath,
                destinationPath,
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    private fun requiredFreshInstallSpaceBytes(packSizeBytes: Long): Long {
        val normalizedPackSize = packSizeBytes.coerceAtLeast(0L)
        return saturatingAdd(normalizedPackSize, normalizedPackSize)
    }

    private fun requiredResumeSpaceBytes(packSizeBytes: Long, tempLengthBytes: Long): Long {
        val normalizedPackSize = packSizeBytes.coerceAtLeast(0L)
        val normalizedTempLength = tempLengthBytes.coerceIn(0L, normalizedPackSize)
        val remainingBytes = normalizedPackSize - normalizedTempLength
        return saturatingAdd(normalizedPackSize, remainingBytes)
    }

    private fun saturatingAdd(left: Long, right: Long): Long {
        return if (Long.MAX_VALUE - left < right) {
            Long.MAX_VALUE
        } else {
            left + right
        }
    }

    private fun clampDownloadedBytes(downloadedBytes: Long, packSizeBytes: Long): Long {
        val normalizedPackSize = packSizeBytes.coerceAtLeast(0L)
        return downloadedBytes.coerceIn(0L, normalizedPackSize)
    }

    private fun beginDownloadSession(packId: String): Long {
        val sessionId = downloadSessionCounter.incrementAndGet()
        activeDownloadSessions[packId] = sessionId
        return sessionId
    }

    private fun endDownloadSession(packId: String, sessionId: Long) {
        if (activeDownloadSessions.remove(packId, sessionId)) {
            activeDownloads.remove(packId)
        }
    }

    private fun isDownloadSessionActive(packId: String, sessionId: Long): Boolean {
        return (packId in activeDownloads) && activeDownloadSessions[packId] == sessionId
    }

    private fun getTempFile(packId: String): File = File(tempDir, "$packId.tmp")
    private fun getResumeFile(packId: String): File = File(tempDir, "$packId$RESUME_SUFFIX")

    private fun updatePackState(packId: String, update: (PackState) -> PackState) {
        val currentStates = _packStates.value.toMutableMap()
        val currentState = currentStates[packId] ?: PackState(
            packId = packId,
            status = PackStatus.NOT_DOWNLOADED
        )
        currentStates[packId] = update(currentState)
        _packStates.value = currentStates
    }

    private fun loadInstalledPacks() {
        val states = mutableMapOf<String, PackState>()
        pausedTargetVersions.clear()

        // Check each available pack
        for (pack in _availablePacks.value) {
            val packDir = File(packsDir, pack.id)
            val manifestFile = File(packDir, "manifest.json")

            if (hasInstalledPackOnDisk(pack.id)) {
                // Pack is installed - parse simple manifest
                try {
                    val metadata = parseInstalledPackMetadata(manifestFile.readText())
                    val installedVersion = metadata.version ?: pack.version
                    val resolvedStatus = if (installedVersion != pack.version) {
                        PackStatus.UPDATE_AVAILABLE
                    } else {
                        PackStatus.INSTALLED
                    }
                    val resumeTargetVersion = readPausedTargetVersion(pack.id)
                    val partialTempBytes = getTempFile(pack.id)
                        .takeIf { it.exists() }
                        ?.length()
                        ?.coerceIn(0L, pack.sizeBytes)
                        ?: 0L
                    val hasStalePausedUpdateForDifferentTarget =
                        resolvedStatus == PackStatus.UPDATE_AVAILABLE &&
                            partialTempBytes > 0L &&
                            resumeTargetVersion != null &&
                            resumeTargetVersion != pack.version
                    val shouldResumePausedUpdate =
                        resolvedStatus == PackStatus.UPDATE_AVAILABLE &&
                            partialTempBytes > 0L &&
                            !hasStalePausedUpdateForDifferentTarget
                    if (hasStalePausedUpdateForDifferentTarget) {
                        runCatching { getTempFile(pack.id).delete() }
                        runCatching { getResumeFile(pack.id).delete() }
                    }
                    val stateStatus = if (shouldResumePausedUpdate) {
                        PackStatus.PAUSED
                    } else {
                        resolvedStatus
                    }
                    val resolvedDownloadedBytes = when {
                        shouldResumePausedUpdate -> partialTempBytes
                        resolvedStatus == PackStatus.UPDATE_AVAILABLE -> 0L
                        else -> pack.sizeBytes
                    }

                    states[pack.id] = PackState(
                        packId = pack.id,
                        status = stateStatus,
                        downloadedBytes = resolvedDownloadedBytes,
                        totalBytes = pack.sizeBytes,
                        installedVersion = installedVersion,
                        installedAt = metadata.installedAt
                    )
                    if (shouldResumePausedUpdate) {
                        pausedTargetVersions[pack.id] = resumeTargetVersion ?: pack.version
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read manifest for ${pack.id}", e)
                    states[pack.id] = PackState(packId = pack.id, status = PackStatus.NOT_DOWNLOADED)
                }
            } else {
                // Check for partial download
                val tempFile = getTempFile(pack.id)
                if (tempFile.exists()) {
                    val partialBytes = tempFile.length().coerceIn(0L, pack.sizeBytes)
                    val resumeTargetVersion = readPausedTargetVersion(pack.id)
                    val hasStalePartialForDifferentTarget =
                        partialBytes > 0L &&
                            resumeTargetVersion != null &&
                            resumeTargetVersion != pack.version
                    if (hasStalePartialForDifferentTarget) {
                        runCatching { tempFile.delete() }
                        runCatching { getResumeFile(pack.id).delete() }
                    }
                    states[pack.id] = if (partialBytes > 0L && !hasStalePartialForDifferentTarget) {
                        pausedTargetVersions[pack.id] = resumeTargetVersion ?: pack.version
                        PackState(
                            packId = pack.id,
                            status = PackStatus.PAUSED,
                            downloadedBytes = partialBytes,
                            totalBytes = pack.sizeBytes
                        )
                    } else {
                        PackState(packId = pack.id, status = PackStatus.NOT_DOWNLOADED)
                    }
                } else {
                    states[pack.id] = PackState(packId = pack.id, status = PackStatus.NOT_DOWNLOADED)
                }
            }
        }

        _packStates.value = states
    }

    private fun hasInstalledPackOnDisk(packId: String): Boolean {
        val packDir = File(packsDir, packId)
        return File(packDir, "manifest.json").exists() && File(packDir, "data.pack").exists()
    }

    internal data class InstalledPackMetadata(
        val version: String?,
        val installedAt: Long?
    )

    internal fun parseInstalledPackMetadata(content: String): InstalledPackMetadata {
        val version = Regex("\"version\"\\s*:\\s*\"([^\"]+)\"")
            .find(content)
            ?.groupValues
            ?.getOrNull(1)
        val installedAt = Regex("\"installedAt\"\\s*:\\s*(\\d+)")
            .find(content)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
        return InstalledPackMetadata(version = version, installedAt = installedAt)
    }

    private fun persistPausedTargetVersion(packId: String, targetVersion: String) {
        runCatching {
            getResumeFile(packId).writeText(targetVersion)
        }.onFailure { error ->
            Log.w(TAG, "Failed to persist paused target version for $packId", error)
        }
    }

    private fun readPausedTargetVersion(packId: String): String? {
        val resumeFile = getResumeFile(packId)
        if (!resumeFile.exists()) return null
        return runCatching {
            resumeFile.readText().trim().takeIf { it.isNotEmpty() }
        }.onFailure { error ->
            Log.w(TAG, "Failed to read paused target version for $packId", error)
        }.getOrNull()
    }

    private fun getDefaultPacks(): List<ContentPack> {
        // Default packs - in production these would come from a server manifest
        return listOf(
            ContentPack(
                id = "medina-map",
                type = PackType.MEDINA_MAP,
                displayName = "Medina Offline Map",
                description = "Offline map tiles and navigation for the old city. Essential for exploring the souks and riads.",
                version = "2026.02.01",
                sizeBytes = 52_428_800, // 50 MB
                sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                downloadUrl = "https://cdn.marrakechguide.app/packs/medina-map-2026.02.01.pack"
            ),
            ContentPack(
                id = "gueliz-map",
                type = PackType.GUELIZ_MAP,
                displayName = "Gueliz & New City Map",
                description = "Offline map for the modern city district including restaurants, shops, and transit.",
                version = "2026.02.01",
                sizeBytes = 31_457_280, // 30 MB
                sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b856",
                downloadUrl = "https://cdn.marrakechguide.app/packs/gueliz-map-2026.02.01.pack"
            ),
            ContentPack(
                id = "audio-phrases",
                type = PackType.AUDIO_PHRASES,
                displayName = "Audio Phrasebook",
                description = "Native speaker recordings for all Darija phrases. Hear correct pronunciation.",
                version = "2026.02.01",
                sizeBytes = 41_943_040, // 40 MB
                sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b857",
                downloadUrl = "https://cdn.marrakechguide.app/packs/audio-phrases-2026.02.01.pack"
            ),
            ContentPack(
                id = "hires-images",
                type = PackType.HIGH_RES_IMAGES,
                displayName = "High-Resolution Photos",
                description = "Premium quality photographs of all places. Best visual experience.",
                version = "2026.02.01",
                sizeBytes = 104_857_600, // 100 MB
                sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b858",
                downloadUrl = "https://cdn.marrakechguide.app/packs/hires-images-2026.02.01.pack"
            )
        )
    }
}

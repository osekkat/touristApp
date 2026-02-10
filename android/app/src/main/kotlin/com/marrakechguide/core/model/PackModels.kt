package com.marrakechguide.core.model

/**
 * Types of downloadable content packs.
 */
enum class PackType {
    MEDINA_MAP,
    GUELIZ_MAP,
    AUDIO_PHRASES,
    HIGH_RES_IMAGES
}

/**
 * Current status of a content pack download.
 */
enum class PackStatus {
    /** Not downloaded, available for download */
    NOT_DOWNLOADED,
    /** Queued for download */
    QUEUED,
    /** Currently downloading */
    DOWNLOADING,
    /** Download paused */
    PAUSED,
    /** Verifying downloaded content */
    VERIFYING,
    /** Installing downloaded content */
    INSTALLING,
    /** Fully installed and ready to use */
    INSTALLED,
    /** Update available for installed pack */
    UPDATE_AVAILABLE,
    /** Download or install failed */
    FAILED
}

/**
 * Domain model for a downloadable content pack.
 */
data class ContentPack(
    val id: String,
    val type: PackType,
    val displayName: String,
    val description: String,
    val version: String,
    val sizeBytes: Long,
    val sha256: String,
    val downloadUrl: String,
    val minAppVersion: String? = null,
    val dependencies: List<String> = emptyList()
) {
    /** Get formatted size for display */
    val formattedSize: String
        get() = when {
            sizeBytes >= 1_073_741_824 -> String.format("%.1f GB", sizeBytes / 1_073_741_824.0)
            sizeBytes >= 1_048_576 -> String.format("%.0f MB", sizeBytes / 1_048_576.0)
            sizeBytes >= 1024 -> String.format("%.0f KB", sizeBytes / 1024.0)
            else -> "$sizeBytes B"
        }
}

/**
 * State of a pack download/installation.
 */
data class PackState(
    val packId: String,
    val status: PackStatus,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val errorMessage: String? = null,
    val installedVersion: String? = null,
    val installedAt: Long? = null
) {
    /** Download progress from 0.0 to 1.0 */
    val progress: Float
        get() {
            if (totalBytes <= 0L) return 0f
            val boundedDownloadedBytes = downloadedBytes.coerceIn(0L, totalBytes)
            return (boundedDownloadedBytes.toDouble() / totalBytes.toDouble()).toFloat()
        }

    /** Progress percentage for display */
    val progressPercent: Int
        get() = (progress * 100).toInt().coerceIn(0, 100)

    /** Check if pack is currently active (downloading or installing) */
    val isActive: Boolean
        get() = status in listOf(
            PackStatus.QUEUED,
            PackStatus.DOWNLOADING,
            PackStatus.VERIFYING,
            PackStatus.INSTALLING
        )

    /** Check if pack can be resumed */
    val canResume: Boolean
        get() = status == PackStatus.PAUSED && downloadedBytes > 0
}

/**
 * Manifest containing all available packs.
 */
data class PackManifest(
    val version: String,
    val packs: List<ContentPack>,
    val updatedAt: String
)

/**
 * Result of a download operation.
 */
sealed class DownloadResult {
    data class Success(val packId: String) : DownloadResult()
    data class Progress(val packId: String, val downloadedBytes: Long, val totalBytes: Long) : DownloadResult()
    data class Error(val packId: String, val error: DownloadError) : DownloadResult()
}

/**
 * Errors that can occur during download operations.
 */
sealed class DownloadError : Exception() {
    object NetworkUnavailable : DownloadError() {
        private fun readResolve(): Any = NetworkUnavailable
        override val message = "Network connection is unavailable"
    }
    object InsufficientStorage : DownloadError() {
        private fun readResolve(): Any = InsufficientStorage
        override val message = "Not enough storage space available"
    }
    object VerificationFailed : DownloadError() {
        private fun readResolve(): Any = VerificationFailed
        override val message = "Downloaded file verification failed"
    }
    object InstallationFailed : DownloadError() {
        private fun readResolve(): Any = InstallationFailed
        override val message = "Failed to install the content pack"
    }
    data class HttpError(val code: Int) : DownloadError() {
        override val message = "Download failed with HTTP error $code"
    }
    data class Unknown(override val message: String?) : DownloadError()
}

/**
 * User preferences for downloads.
 */
data class DownloadPreferences(
    val wifiOnly: Boolean = true,
    val autoUpdate: Boolean = false
)

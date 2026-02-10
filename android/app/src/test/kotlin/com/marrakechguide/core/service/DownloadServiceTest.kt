package com.marrakechguide.core.service

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import androidx.test.core.app.ApplicationProvider
import com.marrakechguide.core.model.ContentPack
import com.marrakechguide.core.model.DownloadError
import com.marrakechguide.core.model.DownloadPreferences
import com.marrakechguide.core.model.DownloadResult
import com.marrakechguide.core.model.PackState
import com.marrakechguide.core.model.PackStatus
import com.marrakechguide.core.model.PackType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkInfo
import org.robolectric.shadows.ShadowConnectivityManager
import org.robolectric.shadows.ShadowNetworkCapabilities
import java.io.ByteArrayInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.SocketException
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DownloadServiceTest {

    private lateinit var context: Context
    private lateinit var service: DownloadServiceImpl

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        service = DownloadServiceImpl(context)
    }

    @Test
    fun `pack state progress clamps oversized downloaded bytes`() {
        val state = PackState(
            packId = "progress-clamp-overflow",
            status = PackStatus.DOWNLOADING,
            downloadedBytes = 4_096L,
            totalBytes = 1_024L
        )

        assertEquals(1f, state.progress, 0.0f)
        assertEquals(100, state.progressPercent)
    }

    @Test
    fun `pack state progress clamps negative downloaded bytes`() {
        val state = PackState(
            packId = "progress-clamp-negative",
            status = PackStatus.DOWNLOADING,
            downloadedBytes = -512L,
            totalBytes = 1_024L
        )

        assertEquals(0f, state.progress, 0.0f)
        assertEquals(0, state.progressPercent)
    }

    @Test
    fun `performDownloadForTest - http error disconnects connection`() = runBlocking {
        val packId = "http-error-pack-${System.nanoTime()}"
        val url = "mockhttp://download/$packId"
        val connection = FakeHttpURLConnection(
            url = URL(url),
            responseCodeValue = 503
        ) { ByteArrayInputStream(ByteArray(0)) }

        registerConnection(url, connection)

        val result = service.performDownloadForTest(
            pack = makePack(packId = packId, downloadUrl = url, sizeBytes = 128),
            resumeFrom = 0
        )

        assertTrue(result is DownloadResult.Error)
        val error = (result as DownloadResult.Error).error
        assertTrue(error is DownloadError.HttpError)
        assertEquals(503, (error as DownloadError.HttpError).code)
        assertTrue("HTTP connection should be disconnected on error", connection.disconnectCalled)
    }

    @Test
    fun `performDownloadForTest - inactive download returns progress and disconnects`() = runBlocking {
        val packId = "inactive-pack-${System.nanoTime()}"
        val url = "mockhttp://download/$packId"
        val payload = ByteArray(4096) { 1 }

        val connection = FakeHttpURLConnection(
            url = URL(url),
            responseCodeValue = 200
        ) {
            CallbackInputStream(ByteArrayInputStream(payload)) {
                service.deactivateDownloadForTest(packId)
            }
        }

        registerConnection(url, connection)

        val result = service.performDownloadForTest(
            pack = makePack(packId = packId, downloadUrl = url, sizeBytes = payload.size.toLong()),
            resumeFrom = 0
        )

        assertTrue(result is DownloadResult.Progress)
        assertEquals(packId, (result as DownloadResult.Progress).packId)
        assertTrue("HTTP connection should be disconnected on early return", connection.disconnectCalled)
    }

    @Test
    fun `performDownloadForTest - resume fallback to full response resets progress baseline`() = runBlocking {
        val packId = "resume-fallback-pack-${System.nanoTime()}"
        val url = "mockhttp://download/$packId"
        val payload = ByteArray(4096) { 2 }
        val resumeFrom = 2048L

        val tempDir = File(context.cacheDir, "packs_temp").apply { mkdirs() }
        val tempFile = File(tempDir, "$packId.tmp")
        tempFile.writeBytes(ByteArray(resumeFrom.toInt()) { 9 })

        val connection = FakeHttpURLConnection(
            url = URL(url),
            responseCodeValue = 200
        ) {
            CallbackInputStream(ByteArrayInputStream(payload)) {
                service.deactivateDownloadForTest(packId)
            }
        }
        registerConnection(url, connection)

        val result = service.performDownloadForTest(
            pack = makePack(packId = packId, downloadUrl = url, sizeBytes = payload.size.toLong()),
            resumeFrom = resumeFrom
        )

        assertTrue(result is DownloadResult.Progress)
        val progress = result as DownloadResult.Progress
        assertTrue(
            "Progress should be based on fresh download bytes when server ignores range",
            progress.downloadedBytes >= 0L && progress.downloadedBytes <= payload.size.toLong()
        )
        val tempBytes = tempFile.readBytes()
        assertTrue(
            "Temp file should only contain fresh-response bytes after fallback restart",
            tempBytes.all { byte -> byte == payload[0] }
        )
        assertTrue(
            "Temp file should be rewritten from offset 0 (never appended past payload length)",
            tempBytes.size <= payload.size
        )
        assertTrue("HTTP connection should be disconnected on early return", connection.disconnectCalled)
    }

    @Test
    fun `performDownloadForTest - stale resume offset larger than temp file restarts from zero`() = runBlocking {
        val packId = "stale-resume-pack-${System.nanoTime()}"
        val payload = ByteArray(512 * 1024) { index -> (index % 211).toByte() }
        val expectedSha256 = sha256Hex(payload)
        val resumeFrom = 2048L
        val tempDir = File(context.cacheDir, "packs_temp").apply { mkdirs() }
        val tempFile = File(tempDir, "$packId.tmp")
        tempFile.writeBytes(ByteArray(1024) { 1 }) // Shorter than resumeFrom (stale progress).

        val server = LocalRangeServer(payload)
        server.start()

        try {
            val result = service.performDownloadForTest(
                pack = makePack(
                    packId = packId,
                    downloadUrl = server.url,
                    sizeBytes = payload.size.toLong(),
                    sha256 = expectedSha256
                ),
                resumeFrom = resumeFrom
            )

            assertTrue(result is DownloadResult.Success)
            assertEquals(
                "Range header should not be used when temp file is shorter than resume offset",
                null,
                server.lastRangeHeader()
            )

            val installedFile = File(context.filesDir, "packs/$packId/data.pack")
            assertTrue("Installed pack file should exist", installedFile.exists())
            assertArrayEquals(payload, installedFile.readBytes())
        } finally {
            server.stop()
        }
    }

    @Test
    fun `performDownloadForTest - resume truncates stale trailing bytes before appending`() = runBlocking {
        val packId = "resume-truncate-stale-tail-pack-${System.nanoTime()}"
        val payload = ByteArray(4096) { index -> (index % 193).toByte() }
        val expectedSha256 = sha256Hex(payload)
        val resumeFrom = 2048L
        val resumeFromInt = resumeFrom.toInt()
        val tempDir = File(context.cacheDir, "packs_temp").apply { mkdirs() }
        val tempFile = File(tempDir, "$packId.tmp")
        tempFile.writeBytes(
            ByteArray(5000) { index ->
                if (index < resumeFromInt) payload[index] else 9.toByte()
            }
        ) // Valid resume prefix + stale trailing bytes past pack boundary.

        val server = LocalRangeServer(payload)
        server.start()

        try {
            val result = service.performDownloadForTest(
                pack = makePack(
                    packId = packId,
                    downloadUrl = server.url,
                    sizeBytes = payload.size.toLong(),
                    sha256 = expectedSha256
                ),
                resumeFrom = resumeFrom
            )

            assertTrue(result is DownloadResult.Success)
            assertEquals("bytes=$resumeFrom-", server.lastRangeHeader())
            assertTrue("Temp file should be removed after successful install", !tempFile.exists())

            val installedFile = File(context.filesDir, "packs/$packId/data.pack")
            assertTrue("Installed pack file should exist", installedFile.exists())
            assertArrayEquals(payload, installedFile.readBytes())
        } finally {
            server.stop()
        }
    }

    @Test
    fun `performDownloadForTest - inactive download persists latest progress below threshold`() = runBlocking {
        val packId = "inactive-progress-pack-${System.nanoTime()}"
        val url = "mockhttp://download/$packId"
        val payload = ByteArray(24 * 1024) { 3 } // Below 64KB threshold.

        val connection = FakeHttpURLConnection(
            url = URL(url),
            responseCodeValue = 200
        ) {
            ReadCountCallbackInputStream(
                delegate = ByteArrayInputStream(payload),
                triggerReadCount = 2
            ) {
                service.deactivateDownloadForTest(packId)
            }
        }
        registerConnection(url, connection)

        val result = service.performDownloadForTest(
            pack = makePack(packId = packId, downloadUrl = url, sizeBytes = payload.size.toLong()),
            resumeFrom = 0
        )

        assertTrue(result is DownloadResult.Progress)
        val progress = result as DownloadResult.Progress
        assertTrue(
            "Expected partial progress before deactivation",
            progress.downloadedBytes > 0 && progress.downloadedBytes < payload.size.toLong()
        )

        val persistedBytes = service.packStates.value[packId]?.downloadedBytes
        assertEquals(
            "Pack state should persist the same latest progress used for resume",
            progress.downloadedBytes,
            persistedBytes
        )
        assertTrue("HTTP connection should be disconnected on early return", connection.disconnectCalled)
    }

    @Test
    fun `performDownloadForTest - cancel mid-transfer keeps not-downloaded state`() = runBlocking {
        val packId = "cancel-mid-transfer-pack-${System.nanoTime()}"
        val url = "mockhttp://download/$packId"
        val payload = ByteArray(24 * 1024) { 6 }

        val connection = FakeHttpURLConnection(
            url = URL(url),
            responseCodeValue = 200
        ) {
            ReadCountCallbackInputStream(
                delegate = ByteArrayInputStream(payload),
                triggerReadCount = 2
            ) {
                runBlocking { service.cancelDownload(packId) }
            }
        }
        registerConnection(url, connection)

        val result = service.performDownloadForTest(
            pack = makePack(packId = packId, downloadUrl = url, sizeBytes = payload.size.toLong()),
            resumeFrom = 0
        )

        assertTrue(result is DownloadResult.Progress)
        assertEquals(0L, (result as DownloadResult.Progress).downloadedBytes)

        val state = service.packStates.value[packId]
        assertEquals(PackStatus.NOT_DOWNLOADED, state?.status)
        assertEquals(0L, state?.downloadedBytes)

        val tempFile = File(context.cacheDir, "packs_temp/$packId.tmp")
        assertTrue("Cancelled download should not leave resumable temp file path", !tempFile.exists())
        val installedFile = File(context.filesDir, "packs/$packId/data.pack")
        assertTrue("Cancelled download should not install the pack", !installedFile.exists())
    }

    @Test
    fun `performDownloadForTest - cancel with read exception returns interruption progress`() = runBlocking {
        val packId = "cancel-throw-pack-${System.nanoTime()}"
        val url = "mockhttp://download/$packId"

        val connection = FakeHttpURLConnection(
            url = URL(url),
            responseCodeValue = 200
        ) {
            CancelAndThrowInputStream {
                runBlocking { service.cancelDownload(packId) }
            }
        }
        registerConnection(url, connection)

        val result = service.performDownloadForTest(
            pack = makePack(packId = packId, downloadUrl = url, sizeBytes = 4096),
            resumeFrom = 0
        )

        assertTrue(
            "Cancelled exception path should return progress, got ${describeResult(result)}",
            result is DownloadResult.Progress
        )
        val progress = result as DownloadResult.Progress
        assertEquals(packId, progress.packId)
        assertEquals(0L, progress.downloadedBytes)

        val state = service.packStates.value[packId]
        assertTrue("Cancelled exception path must not end in FAILED", state?.status != PackStatus.FAILED)
    }

    @Test
    fun `cancelDownload with manifest-only pack keeps not-downloaded state`() = runBlocking {
        val packId = "cancel-manifest-only-pack-${System.nanoTime()}"
        val pack = makePack(
            packId = packId,
            downloadUrl = "http://127.0.0.1:1/not-used",
            sizeBytes = 4096
        )
        service.replaceAvailablePacksForTest(listOf(pack))

        val existingPackDir = File(context.filesDir, "packs/$packId").apply { mkdirs() }
        File(existingPackDir, "manifest.json").writeText(
            """{"id":"$packId","version":"0.8.0","installedAt":1700000000000}"""
        )
        service.cancelDownload(packId)

        val state = service.packStates.value[packId]
        assertEquals(PackStatus.NOT_DOWNLOADED, state?.status)
        assertEquals(0L, state?.downloadedBytes)
        assertTrue("Manifest-only fixture should remain manifest-only", !File(existingPackDir, "data.pack").exists())
    }

    @Test
    fun `pauseDownload with no active download does not change pack state`() = runBlocking {
        val packId = "pause-no-active-pack-${System.nanoTime()}"

        service.pauseDownload(packId)

        val state = service.packStates.value[packId]
        assertTrue("Pause with no active download should be a no-op", state == null || state.status != PackStatus.PAUSED)
    }

    @Test
    fun `resumeDownload returns insufficient storage when free space is below resume requirement`() = runBlocking {
        configureConnectedWifiNetwork()

        val packId = "resume-insufficient-space-pack-${System.nanoTime()}"
        val packSize = 4096L
        val tempLength = 1024L
        val pack = makePack(
            packId = packId,
            downloadUrl = "http://127.0.0.1:1/not-used",
            sizeBytes = packSize
        )

        val tempDir = File(context.cacheDir, "packs_temp").apply { mkdirs() }
        val tempFile = File(tempDir, "$packId.tmp")
        tempFile.writeBytes(ByteArray(tempLength.toInt()) { 8 })

        service.replaceAvailablePacksAndReloadForTest(listOf(pack))
        val stateBefore = service.packStates.value[packId]
        assertEquals(PackStatus.PAUSED, stateBefore?.status)
        assertEquals(tempLength, stateBefore?.downloadedBytes)

        val requiredFreeSpace = packSize + (packSize - tempLength)
        service.availableSpaceOverrideBytesForTest = requiredFreeSpace - 1
        try {
            val result = service.resumeDownload(packId)

            assertTrue(result is DownloadResult.Error)
            val error = (result as DownloadResult.Error).error
            assertTrue(error is DownloadError.InsufficientStorage)

            val stateAfter = service.packStates.value[packId]
            assertEquals(PackStatus.PAUSED, stateAfter?.status)
            assertEquals(tempLength, stateAfter?.downloadedBytes)
        } finally {
            service.availableSpaceOverrideBytesForTest = null
        }
    }

    @Test
    fun `startDownload returns insufficient storage when required space calculation overflows`() = runBlocking {
        configureConnectedWifiNetwork()

        val packId = "start-overflow-space-pack-${System.nanoTime()}"
        val hugePackSize = (Long.MAX_VALUE / 2L) + 16L
        val pack = makePack(
            packId = packId,
            downloadUrl = "http://127.0.0.1:1/not-used",
            sizeBytes = hugePackSize
        )
        service.replaceAvailablePacksForTest(listOf(pack))

        service.availableSpaceOverrideBytesForTest = Long.MAX_VALUE / 2L
        try {
            val result = service.startDownload(packId)

            assertTrue(result is DownloadResult.Error)
            val error = (result as DownloadResult.Error).error
            assertTrue(error is DownloadError.InsufficientStorage)
        } finally {
            service.availableSpaceOverrideBytesForTest = null
        }
    }

    @Test
    fun `resumeDownload returns insufficient storage when required resume space calculation overflows`() = runBlocking {
        configureConnectedWifiNetwork()

        val packId = "resume-overflow-space-pack-${System.nanoTime()}"
        val hugePackSize = (Long.MAX_VALUE / 2L) + 16L
        val tempLength = 1L
        val pack = makePack(
            packId = packId,
            downloadUrl = "http://127.0.0.1:1/not-used",
            sizeBytes = hugePackSize
        )

        val tempDir = File(context.cacheDir, "packs_temp").apply { mkdirs() }
        val tempFile = File(tempDir, "$packId.tmp")
        tempFile.writeBytes(ByteArray(tempLength.toInt()) { 8 })

        service.replaceAvailablePacksAndReloadForTest(listOf(pack))
        val stateBefore = service.packStates.value[packId]
        assertEquals(PackStatus.PAUSED, stateBefore?.status)
        assertEquals(tempLength, stateBefore?.downloadedBytes)

        service.availableSpaceOverrideBytesForTest = Long.MAX_VALUE / 2L
        try {
            val result = service.resumeDownload(packId)

            assertTrue(result is DownloadResult.Error)
            val error = (result as DownloadResult.Error).error
            assertTrue(error is DownloadError.InsufficientStorage)

            val stateAfter = service.packStates.value[packId]
            assertEquals(PackStatus.PAUSED, stateAfter?.status)
            assertEquals(tempLength, stateAfter?.downloadedBytes)
        } finally {
            service.availableSpaceOverrideBytesForTest = null
        }
    }

    @Test
    fun `cancelDownload clears stale installed metadata when restore fails`() = runBlocking {
        val packId = "cancel-stale-installed-metadata-pack-${System.nanoTime()}"
        val previousVersion = "0.4.0"
        val pack = makePack(
            packId = packId,
            downloadUrl = "http://127.0.0.1:1/not-used",
            sizeBytes = 4096
        )

        val packDir = File(context.filesDir, "packs/$packId").apply { mkdirs() }
        File(packDir, "data.pack").writeBytes(ByteArray(128) { 7 })
        File(packDir, "manifest.json").writeText(
            """{"id":"$packId","version":"$previousVersion","installedAt":1700000000000}"""
        )

        service.replaceAvailablePacksAndReloadForTest(listOf(pack))
        val preState = service.packStates.value[packId]
        assertEquals(PackStatus.UPDATE_AVAILABLE, preState?.status)
        assertEquals(previousVersion, preState?.installedVersion)

        packDir.deleteRecursively()
        service.cancelDownload(packId)

        val state = service.packStates.value[packId]
        assertEquals(PackStatus.NOT_DOWNLOADED, state?.status)
        assertEquals(0L, state?.downloadedBytes)
        assertNull(state?.installedVersion)
        assertNull(state?.installedAt)
        assertNull(state?.errorMessage)
    }

    @Test
    fun `replaceAvailablePacksAndReloadForTest marks outdated installed pack as update available`() = runBlocking {
        val packId = "reload-outdated-pack-${System.nanoTime()}"
        val pack = makePack(
            packId = packId,
            downloadUrl = "http://127.0.0.1:1/not-used",
            sizeBytes = 4096
        )

        val existingPackDir = File(context.filesDir, "packs/$packId").apply { mkdirs() }
        File(existingPackDir, "data.pack").writeBytes(ByteArray(1024) { 4 })
        File(existingPackDir, "manifest.json").writeText(
            """{"id":"$packId","version":"0.8.0","installedAt":1700000000000}"""
        )

        service.replaceAvailablePacksAndReloadForTest(listOf(pack))

        val state = service.packStates.value[packId]
        assertEquals(PackStatus.UPDATE_AVAILABLE, state?.status)
        assertEquals("0.8.0", state?.installedVersion)
        assertEquals(0L, state?.downloadedBytes)
        assertEquals(pack.sizeBytes, state?.totalBytes)
    }

    @Test
    fun `checkForUpdates resets update progress and total size for outdated installed pack`() = runBlocking {
        val packId = "check-updates-outdated-pack-${System.nanoTime()}"
        val installedSize = 4096L
        val updatedSize = 8192L
        val installedPack = makePack(
            packId = packId,
            downloadUrl = "http://127.0.0.1:1/not-used",
            sizeBytes = installedSize
        )

        val existingPackDir = File(context.filesDir, "packs/$packId").apply { mkdirs() }
        File(existingPackDir, "data.pack").writeBytes(ByteArray(1024) { 4 })
        File(existingPackDir, "manifest.json").writeText(
            """{"id":"$packId","version":"1.0.0","installedAt":1700000000000}"""
        )

        service.replaceAvailablePacksAndReloadForTest(listOf(installedPack))
        val preState = service.packStates.value[packId]
        assertEquals(PackStatus.INSTALLED, preState?.status)
        assertEquals(installedSize, preState?.downloadedBytes)
        assertEquals(installedSize, preState?.totalBytes)

        val updatedPack = installedPack.copy(version = "1.1.0", sizeBytes = updatedSize)
        service.replaceAvailablePacksForTest(listOf(updatedPack))
        service.checkForUpdates()

        val postState = service.packStates.value[packId]
        assertEquals(PackStatus.UPDATE_AVAILABLE, postState?.status)
        assertEquals(0L, postState?.downloadedBytes)
        assertEquals(updatedSize, postState?.totalBytes)
        assertEquals("1.0.0", postState?.installedVersion)
        assertTrue(postState?.canResume == false)
    }

    @Test
    fun `checkForUpdates resets paused outdated update and clears stale partial artifacts`() = runBlocking {
        val packId = "check-updates-paused-reset-pack-${System.nanoTime()}"
        val installedVersion = "1.0.0"
        val firstUpdateVersion = "1.1.0"
        val firstUpdateSize = 4096L
        val latestUpdateSize = 8192L
        val installedPack = makePack(
            packId = packId,
            downloadUrl = "http://127.0.0.1:1/not-used",
            sizeBytes = firstUpdateSize
        )

        val existingPackDir = File(context.filesDir, "packs/$packId").apply { mkdirs() }
        File(existingPackDir, "data.pack").writeBytes(ByteArray(2048) { 2 })
        File(existingPackDir, "manifest.json").writeText(
            """{"id":"$packId","version":"$installedVersion","installedAt":1700000000000}"""
        )

        val tempDir = File(context.cacheDir, "packs_temp").apply { mkdirs() }
        val tempFile = File(tempDir, "$packId.tmp")
        val resumeFile = File(tempDir, "$packId.resume")
        tempFile.writeBytes(ByteArray(1024) { 9 })
        resumeFile.writeText(firstUpdateVersion)

        val firstUpdatePack = installedPack.copy(version = firstUpdateVersion, sizeBytes = firstUpdateSize)
        service.replaceAvailablePacksAndReloadForTest(listOf(firstUpdatePack))
        val pausedState = service.packStates.value[packId]
        assertEquals(PackStatus.PAUSED, pausedState?.status)
        assertEquals(1024L, pausedState?.downloadedBytes)
        assertTrue(tempFile.exists())
        assertTrue(resumeFile.exists())

        val latestUpdatePack = installedPack.copy(version = "1.2.0", sizeBytes = latestUpdateSize)
        service.replaceAvailablePacksForTest(listOf(latestUpdatePack))
        service.checkForUpdates()

        val state = service.packStates.value[packId]
        assertEquals(PackStatus.UPDATE_AVAILABLE, state?.status)
        assertEquals(0L, state?.downloadedBytes)
        assertEquals(latestUpdateSize, state?.totalBytes)
        assertEquals(installedVersion, state?.installedVersion)
        assertTrue(state?.canResume == false)
        assertTrue("Temp file should be deleted when paused update is reset", !tempFile.exists())
        assertTrue("Resume metadata should be deleted when paused update is reset", !resumeFile.exists())
    }

    @Test
    fun `checkForUpdates preserves paused update progress when manifest target is unchanged`() = runBlocking {
        val packId = "check-updates-paused-preserve-pack-${System.nanoTime()}"
        val installedVersion = "1.0.0"
        val availableUpdateVersion = "1.1.0"
        val updateSize = 4096L
        val installedPack = makePack(
            packId = packId,
            downloadUrl = "http://127.0.0.1:1/not-used",
            sizeBytes = updateSize
        )

        val existingPackDir = File(context.filesDir, "packs/$packId").apply { mkdirs() }
        File(existingPackDir, "data.pack").writeBytes(ByteArray(2048) { 5 })
        File(existingPackDir, "manifest.json").writeText(
            """{"id":"$packId","version":"$installedVersion","installedAt":1700000000000}"""
        )

        val tempDir = File(context.cacheDir, "packs_temp").apply { mkdirs() }
        val tempFile = File(tempDir, "$packId.tmp")
        val resumeFile = File(tempDir, "$packId.resume")
        tempFile.writeBytes(ByteArray(1024) { 7 })
        resumeFile.writeText(availableUpdateVersion)

        val availableUpdatePack = installedPack.copy(version = availableUpdateVersion, sizeBytes = updateSize)
        service.replaceAvailablePacksAndReloadForTest(listOf(availableUpdatePack))
        val pausedState = service.packStates.value[packId]
        assertEquals(PackStatus.PAUSED, pausedState?.status)
        assertEquals(1024L, pausedState?.downloadedBytes)
        assertEquals(updateSize, pausedState?.totalBytes)
        assertEquals(installedVersion, pausedState?.installedVersion)
        assertTrue(tempFile.exists())
        assertTrue(resumeFile.exists())

        service.checkForUpdates()

        val stateAfterCheck = service.packStates.value[packId]
        assertEquals(PackStatus.PAUSED, stateAfterCheck?.status)
        assertEquals(1024L, stateAfterCheck?.downloadedBytes)
        assertEquals(updateSize, stateAfterCheck?.totalBytes)
        assertEquals(installedVersion, stateAfterCheck?.installedVersion)
        assertTrue(stateAfterCheck?.canResume == true)
        assertTrue("Temp file should remain for resumable paused update", tempFile.exists())
        assertTrue("Resume metadata should remain for resumable paused update", resumeFile.exists())
    }

    @Test
    fun `checkForUpdates resets paused update when manifest version changes but size is unchanged`() = runBlocking {
        val packId = "check-updates-paused-same-size-new-version-pack-${System.nanoTime()}"
        val installedVersion = "1.0.0"
        val firstUpdateVersion = "1.1.0"
        val nextUpdateVersion = "1.2.0"
        val updateSize = 4096L
        val installedPack = makePack(
            packId = packId,
            downloadUrl = "http://127.0.0.1:1/not-used",
            sizeBytes = updateSize
        )

        val existingPackDir = File(context.filesDir, "packs/$packId").apply { mkdirs() }
        File(existingPackDir, "data.pack").writeBytes(ByteArray(2048) { 6 })
        File(existingPackDir, "manifest.json").writeText(
            """{"id":"$packId","version":"$installedVersion","installedAt":1700000000000}"""
        )

        val tempDir = File(context.cacheDir, "packs_temp").apply { mkdirs() }
        val tempFile = File(tempDir, "$packId.tmp")
        val resumeFile = File(tempDir, "$packId.resume")
        tempFile.writeBytes(ByteArray(1024) { 8 })
        resumeFile.writeText(firstUpdateVersion)

        val firstUpdatePack = installedPack.copy(version = firstUpdateVersion, sizeBytes = updateSize)
        service.replaceAvailablePacksAndReloadForTest(listOf(firstUpdatePack))
        val pausedState = service.packStates.value[packId]
        assertEquals(PackStatus.PAUSED, pausedState?.status)
        assertEquals(1024L, pausedState?.downloadedBytes)
        assertEquals(updateSize, pausedState?.totalBytes)
        assertEquals(installedVersion, pausedState?.installedVersion)
        assertTrue(tempFile.exists())
        assertTrue(resumeFile.exists())

        val nextUpdatePack = installedPack.copy(version = nextUpdateVersion, sizeBytes = updateSize)
        service.replaceAvailablePacksForTest(listOf(nextUpdatePack))
        service.checkForUpdates()

        val stateAfterCheck = service.packStates.value[packId]
        assertEquals(PackStatus.UPDATE_AVAILABLE, stateAfterCheck?.status)
        assertEquals(0L, stateAfterCheck?.downloadedBytes)
        assertEquals(updateSize, stateAfterCheck?.totalBytes)
        assertEquals(installedVersion, stateAfterCheck?.installedVersion)
        assertTrue(stateAfterCheck?.canResume == false)
        assertTrue("Temp file should be deleted for stale paused update", !tempFile.exists())
        assertTrue("Resume metadata should be deleted for stale paused update", !resumeFile.exists())
    }

    @Test
    fun `replaceAvailablePacksAndReloadForTest clears stale paused update when resume target version mismatches`() = runBlocking {
        val packId = "reload-paused-stale-target-pack-${System.nanoTime()}"
        val installedVersion = "1.0.0"
        val firstUpdateVersion = "1.1.0"
        val nextUpdateVersion = "1.2.0"
        val updateSize = 4096L
        val installedPack = makePack(
            packId = packId,
            downloadUrl = "http://127.0.0.1:1/not-used",
            sizeBytes = updateSize
        )

        val existingPackDir = File(context.filesDir, "packs/$packId").apply { mkdirs() }
        File(existingPackDir, "data.pack").writeBytes(ByteArray(2048) { 3 })
        File(existingPackDir, "manifest.json").writeText(
            """{"id":"$packId","version":"$installedVersion","installedAt":1700000000000}"""
        )

        val tempDir = File(context.cacheDir, "packs_temp").apply { mkdirs() }
        val tempFile = File(tempDir, "$packId.tmp")
        val resumeFile = File(tempDir, "$packId.resume")
        tempFile.writeBytes(ByteArray(1024) { 4 })
        resumeFile.writeText(firstUpdateVersion)

        service.replaceAvailablePacksAndReloadForTest(listOf(installedPack.copy(version = firstUpdateVersion)))
        val pausedState = service.packStates.value[packId]
        assertEquals(PackStatus.PAUSED, pausedState?.status)
        assertEquals(1024L, pausedState?.downloadedBytes)
        assertTrue(tempFile.exists())
        assertTrue(resumeFile.exists())

        service.replaceAvailablePacksAndReloadForTest(listOf(installedPack.copy(version = nextUpdateVersion)))

        val stateAfterReload = service.packStates.value[packId]
        assertEquals(PackStatus.UPDATE_AVAILABLE, stateAfterReload?.status)
        assertEquals(0L, stateAfterReload?.downloadedBytes)
        assertEquals(updateSize, stateAfterReload?.totalBytes)
        assertEquals(installedVersion, stateAfterReload?.installedVersion)
        assertTrue("Stale paused temp file should be cleared on reload", !tempFile.exists())
        assertTrue("Stale paused resume metadata should be cleared on reload", !resumeFile.exists())
    }

    @Test
    fun `replaceAvailablePacksAndReloadForTest restores paused update progress when temp data exists`() = runBlocking {
        val packId = "reload-paused-update-pack-${System.nanoTime()}"
        val previousVersion = "0.8.0"
        val partialBytes = 1024L
        val pack = makePack(
            packId = packId,
            downloadUrl = "http://127.0.0.1:1/not-used",
            sizeBytes = 4096
        )

        val existingPackDir = File(context.filesDir, "packs/$packId").apply { mkdirs() }
        File(existingPackDir, "data.pack").writeBytes(ByteArray(2048) { 5 })
        File(existingPackDir, "manifest.json").writeText(
            """{"id":"$packId","version":"$previousVersion","installedAt":1700000000000}"""
        )

        val tempDir = File(context.cacheDir, "packs_temp").apply { mkdirs() }
        val tempFile = File(tempDir, "$packId.tmp")
        tempFile.writeBytes(ByteArray(partialBytes.toInt()) { 9 })

        service.replaceAvailablePacksAndReloadForTest(listOf(pack))

        val state = service.packStates.value[packId]
        assertEquals(PackStatus.PAUSED, state?.status)
        assertEquals(partialBytes, state?.downloadedBytes)
        assertEquals(pack.sizeBytes, state?.totalBytes)
        assertEquals(previousVersion, state?.installedVersion)
        assertTrue(state?.canResume == true)
    }

    @Test
    fun `replaceAvailablePacksAndReloadForTest keeps update available when outdated pack has zero-byte temp file`() = runBlocking {
        val packId = "reload-update-zero-temp-pack-${System.nanoTime()}"
        val previousVersion = "0.8.0"
        val pack = makePack(
            packId = packId,
            downloadUrl = "http://127.0.0.1:1/not-used",
            sizeBytes = 4096
        )

        val existingPackDir = File(context.filesDir, "packs/$packId").apply { mkdirs() }
        File(existingPackDir, "data.pack").writeBytes(ByteArray(2048) { 2 })
        File(existingPackDir, "manifest.json").writeText(
            """{"id":"$packId","version":"$previousVersion","installedAt":1700000000000}"""
        )

        val tempDir = File(context.cacheDir, "packs_temp").apply { mkdirs() }
        val tempFile = File(tempDir, "$packId.tmp")
        tempFile.writeBytes(ByteArray(0))

        service.replaceAvailablePacksAndReloadForTest(listOf(pack))

        val state = service.packStates.value[packId]
        assertEquals(PackStatus.UPDATE_AVAILABLE, state?.status)
        assertEquals(0L, state?.downloadedBytes)
        assertEquals(pack.sizeBytes, state?.totalBytes)
        assertEquals(previousVersion, state?.installedVersion)
        assertTrue(state?.canResume == false)
    }

    @Test
    fun `replaceAvailablePacksAndReloadForTest clamps oversized temp progress for paused updates`() = runBlocking {
        val packId = "reload-paused-update-oversized-temp-pack-${System.nanoTime()}"
        val previousVersion = "0.8.0"
        val pack = makePack(
            packId = packId,
            downloadUrl = "http://127.0.0.1:1/not-used",
            sizeBytes = 4096
        )

        val existingPackDir = File(context.filesDir, "packs/$packId").apply { mkdirs() }
        File(existingPackDir, "data.pack").writeBytes(ByteArray(2048) { 2 })
        File(existingPackDir, "manifest.json").writeText(
            """{"id":"$packId","version":"$previousVersion","installedAt":1700000000000}"""
        )

        val tempDir = File(context.cacheDir, "packs_temp").apply { mkdirs() }
        val tempFile = File(tempDir, "$packId.tmp")
        tempFile.writeBytes(ByteArray(8192) { 7 })

        service.replaceAvailablePacksAndReloadForTest(listOf(pack))

        val state = service.packStates.value[packId]
        assertEquals(PackStatus.PAUSED, state?.status)
        assertEquals(pack.sizeBytes, state?.downloadedBytes)
        assertEquals(pack.sizeBytes, state?.totalBytes)
        assertEquals(previousVersion, state?.installedVersion)
        assertTrue(state?.canResume == true)
    }

    @Test
    fun `replaceAvailablePacksAndReloadForTest clamps oversized paused temp progress to pack size`() = runBlocking {
        val packId = "reload-oversized-temp-pack-${System.nanoTime()}"
        val pack = makePack(
            packId = packId,
            downloadUrl = "http://127.0.0.1:1/not-used",
            sizeBytes = 4096
        )

        val tempDir = File(context.cacheDir, "packs_temp").apply { mkdirs() }
        val tempFile = File(tempDir, "$packId.tmp")
        tempFile.writeBytes(ByteArray(8192) { 3 })

        service.replaceAvailablePacksAndReloadForTest(listOf(pack))

        val state = service.packStates.value[packId]
        assertEquals(PackStatus.PAUSED, state?.status)
        assertEquals(pack.sizeBytes, state?.downloadedBytes)
        assertEquals(pack.sizeBytes, state?.totalBytes)
    }

    @Test
    fun `replaceAvailablePacksAndReloadForTest treats zero-byte temp file as not downloaded`() = runBlocking {
        val packId = "reload-zero-byte-temp-pack-${System.nanoTime()}"
        val pack = makePack(
            packId = packId,
            downloadUrl = "http://127.0.0.1:1/not-used",
            sizeBytes = 4096
        )

        val tempDir = File(context.cacheDir, "packs_temp").apply { mkdirs() }
        val tempFile = File(tempDir, "$packId.tmp")
        tempFile.writeBytes(ByteArray(0))

        service.replaceAvailablePacksAndReloadForTest(listOf(pack))

        val state = service.packStates.value[packId]
        assertEquals(PackStatus.NOT_DOWNLOADED, state?.status)
        assertEquals(0L, state?.downloadedBytes)
    }

    @Test
    fun `removePack clears partial temp artifacts and remains not downloaded after reload`() = runBlocking {
        val packId = "remove-pack-clears-temp-pack-${System.nanoTime()}"
        val pack = makePack(
            packId = packId,
            downloadUrl = "http://127.0.0.1:1/not-used",
            sizeBytes = 4096
        )
        service.replaceAvailablePacksForTest(listOf(pack))

        val existingPackDir = File(context.filesDir, "packs/$packId").apply { mkdirs() }
        File(existingPackDir, "data.pack").writeBytes(ByteArray(2048) { 4 })
        File(existingPackDir, "manifest.json").writeText(
            """{"id":"$packId","version":"1.0.0","installedAt":1700000000000}"""
        )

        val tempDir = File(context.cacheDir, "packs_temp").apply { mkdirs() }
        val tempFile = File(tempDir, "$packId.tmp")
        val resumeFile = File(tempDir, "$packId.resume")
        tempFile.writeBytes(ByteArray(1024) { 6 })
        resumeFile.writeBytes(ByteArray(16) { 1 })

        service.removePack(packId)

        assertTrue("removePack should delete installed pack directory", !existingPackDir.exists())
        assertTrue("removePack should delete temp download file", !tempFile.exists())
        assertTrue("removePack should delete resume metadata file", !resumeFile.exists())

        val stateAfterRemove = service.packStates.value[packId]
        assertEquals(PackStatus.NOT_DOWNLOADED, stateAfterRemove?.status)
        assertEquals(0L, stateAfterRemove?.downloadedBytes)

        service.replaceAvailablePacksAndReloadForTest(listOf(pack))
        val reloadedState = service.packStates.value[packId]
        assertEquals(PackStatus.NOT_DOWNLOADED, reloadedState?.status)
        assertEquals(0L, reloadedState?.downloadedBytes)
    }

    @Test
    fun `performDownloadForTest - remove mid-transfer keeps not-downloaded state and clears temp artifacts`() = runBlocking {
        val packId = "remove-mid-transfer-pack-${System.nanoTime()}"
        val url = "mockhttp://download/$packId"
        val payload = ByteArray(24 * 1024) { 6 }
        val pack = makePack(packId = packId, downloadUrl = url, sizeBytes = payload.size.toLong())
        service.replaceAvailablePacksForTest(listOf(pack))

        val connection = FakeHttpURLConnection(
            url = URL(url),
            responseCodeValue = 200
        ) {
            ReadCountCallbackInputStream(
                delegate = ByteArrayInputStream(payload),
                triggerReadCount = 2
            ) {
                runBlocking { service.removePack(packId) }
            }
        }
        registerConnection(url, connection)

        val result = service.performDownloadForTest(pack = pack, resumeFrom = 0)

        assertTrue(result is DownloadResult.Progress)
        assertEquals(0L, (result as DownloadResult.Progress).downloadedBytes)

        val state = service.packStates.value[packId]
        assertEquals(PackStatus.NOT_DOWNLOADED, state?.status)
        assertEquals(0L, state?.downloadedBytes)

        val tempFile = File(context.cacheDir, "packs_temp/$packId.tmp")
        val resumeFile = File(context.cacheDir, "packs_temp/$packId.resume")
        val installedFile = File(context.filesDir, "packs/$packId/data.pack")
        assertTrue("remove mid-transfer should not leave temp file", !tempFile.exists())
        assertTrue("remove mid-transfer should not leave resume file", !resumeFile.exists())
        assertTrue("remove mid-transfer should not install the pack", !installedFile.exists())

        service.replaceAvailablePacksAndReloadForTest(listOf(pack))
        val reloadedState = service.packStates.value[packId]
        assertEquals(PackStatus.NOT_DOWNLOADED, reloadedState?.status)
        assertEquals(0L, reloadedState?.downloadedBytes)
    }

    @Test
    fun `performDownloadForTest - paused at eof returns progress instead of installing`() = runBlocking {
        val packId = "pause-at-eof-pack-${System.nanoTime()}"
        val url = "mockhttp://download/$packId"
        val payload = ByteArray(4096) { 4 }
        val expectedSha256 = sha256Hex(payload)

        val connection = FakeHttpURLConnection(
            url = URL(url),
            responseCodeValue = 200
        ) {
            DeactivateOnEofInputStream(ByteArrayInputStream(payload)) {
                service.deactivateDownloadForTest(packId)
            }
        }
        registerConnection(url, connection)

        val result = service.performDownloadForTest(
            pack = makePack(
                packId = packId,
                downloadUrl = url,
                sizeBytes = payload.size.toLong(),
                sha256 = expectedSha256
            ),
            resumeFrom = 0
        )

        assertTrue(result is DownloadResult.Progress)
        val progress = result as DownloadResult.Progress
        assertEquals(payload.size.toLong(), progress.downloadedBytes)

        val persistedBytes = service.packStates.value[packId]?.downloadedBytes
        assertEquals(payload.size.toLong(), persistedBytes)

        val installedFile = File(context.filesDir, "packs/$packId/data.pack")
        assertTrue("Pack should not install when download is paused at EOF boundary", !installedFile.exists())
        assertTrue("HTTP connection should be disconnected", connection.disconnectCalled)
    }

    @Test
    fun `performDownloadForTest - paused oversized payload clamps progress to pack size`() = runBlocking {
        val packId = "pause-oversized-payload-pack-${System.nanoTime()}"
        val url = "mockhttp://download/$packId"
        val packSize = 4096L
        val payload = ByteArray(8192) { 5 }

        val connection = FakeHttpURLConnection(
            url = URL(url),
            responseCodeValue = 200
        ) {
            DeactivateOnEofInputStream(ByteArrayInputStream(payload)) {
                service.deactivateDownloadForTest(packId)
            }
        }
        registerConnection(url, connection)

        val result = service.performDownloadForTest(
            pack = makePack(
                packId = packId,
                downloadUrl = url,
                sizeBytes = packSize,
                sha256 = sha256Hex(ByteArray(packSize.toInt()) { 5 })
            ),
            resumeFrom = 0
        )

        assertTrue(result is DownloadResult.Progress)
        val progress = result as DownloadResult.Progress
        assertEquals(packSize, progress.downloadedBytes)
        assertEquals(packSize, progress.totalBytes)

        val persisted = service.packStates.value[packId]
        assertEquals(PackStatus.DOWNLOADING, persisted?.status)
        assertEquals(packSize, persisted?.downloadedBytes)
        assertEquals(packSize, persisted?.totalBytes)

        val installedFile = File(context.filesDir, "packs/$packId/data.pack")
        assertTrue("Oversized paused payload should not install pack directly", !installedFile.exists())
        assertTrue("HTTP connection should be disconnected", connection.disconnectCalled)
    }

    @Test
    fun `performDownloadForTest - complete temp file installs without network fetch`() = runBlocking {
        val packId = "complete-temp-pack-${System.nanoTime()}"
        val payload = ByteArray(4096) { 5 }
        val expectedSha256 = sha256Hex(payload)
        val tempDir = File(context.cacheDir, "packs_temp").apply { mkdirs() }
        val tempFile = File(tempDir, "$packId.tmp")
        tempFile.writeBytes(payload)

        val result = service.performDownloadForTest(
            pack = makePack(
                packId = packId,
                downloadUrl = "http://127.0.0.1:1/should-not-be-used",
                sizeBytes = payload.size.toLong(),
                sha256 = expectedSha256
            ),
            resumeFrom = payload.size.toLong()
        )

        assertTrue(
            "Expected complete temp-file path to install successfully, got ${describeResult(result)}",
            result is DownloadResult.Success
        )
        val installedFile = File(context.filesDir, "packs/$packId/data.pack")
        assertTrue("Installed pack file should exist", installedFile.exists())
        assertArrayEquals(payload, installedFile.readBytes())
    }

    @Test
    fun `performDownloadForTest - overlong complete temp file is truncated before verification`() = runBlocking {
        val packId = "complete-temp-overlong-pack-${System.nanoTime()}"
        val payload = ByteArray(4096) { index -> (index % 187).toByte() }
        val expectedSha256 = sha256Hex(payload)
        val tempDir = File(context.cacheDir, "packs_temp").apply { mkdirs() }
        val tempFile = File(tempDir, "$packId.tmp")
        tempFile.writeBytes(payload + ByteArray(512) { 9 })

        val result = service.performDownloadForTest(
            pack = makePack(
                packId = packId,
                downloadUrl = "http://127.0.0.1:1/should-not-be-used",
                sizeBytes = payload.size.toLong(),
                sha256 = expectedSha256
            ),
            resumeFrom = payload.size.toLong()
        )

        assertTrue(result is DownloadResult.Success)
        assertTrue("Temp file should be removed after successful install", !tempFile.exists())
        val installedFile = File(context.filesDir, "packs/$packId/data.pack")
        assertTrue("Installed pack file should exist", installedFile.exists())
        assertArrayEquals(payload, installedFile.readBytes())
    }

    @Test
    fun `performDownloadForTest - complete temp file with invalid checksum fails verification`() = runBlocking {
        val packId = "complete-temp-invalid-hash-pack-${System.nanoTime()}"
        val payload = ByteArray(4096) { 7 }
        val tempDir = File(context.cacheDir, "packs_temp").apply { mkdirs() }
        val tempFile = File(tempDir, "$packId.tmp")
        tempFile.writeBytes(payload)

        val result = service.performDownloadForTest(
            pack = makePack(
                packId = packId,
                downloadUrl = "http://127.0.0.1:1/should-not-be-used",
                sizeBytes = payload.size.toLong(),
                sha256 = "0000000000000000000000000000000000000000000000000000000000000000"
            ),
            resumeFrom = payload.size.toLong()
        )

        assertTrue(result is DownloadResult.Error)
        val error = (result as DownloadResult.Error).error
        assertTrue(error is DownloadError.VerificationFailed)
        assertTrue("Temp file should be removed after verification failure", !tempFile.exists())
        val installedFile = File(context.filesDir, "packs/$packId/data.pack")
        assertTrue("Invalid full temp file should not be installed", !installedFile.exists())
    }

    @Test
    fun `performDownloadForTest - update http error keeps update-available state`() = runBlocking {
        val packId = "update-http-error-pack-${System.nanoTime()}"
        val previousVersion = "0.9.0"
        val url = "mockhttp://download/$packId"

        val existingPackDir = File(context.filesDir, "packs/$packId").apply { mkdirs() }
        File(existingPackDir, "data.pack").writeBytes(ByteArray(128) { 1 })
        File(existingPackDir, "manifest.json").writeText(
            """{"id":"$packId","version":"$previousVersion","installedAt":1700000000000}"""
        )

        val connection = FakeHttpURLConnection(
            url = URL(url),
            responseCodeValue = 503
        ) { ByteArrayInputStream(ByteArray(0)) }
        registerConnection(url, connection)

        val result = service.performDownloadForTest(
            pack = makePack(packId = packId, downloadUrl = url, sizeBytes = 128, sha256 = "unused"),
            resumeFrom = 0
        )

        assertTrue(result is DownloadResult.Error)
        val error = (result as DownloadResult.Error).error
        assertTrue(error is DownloadError.HttpError)
        assertEquals(PackStatus.UPDATE_AVAILABLE, service.packStates.value[packId]?.status)
        assertTrue("Update HTTP failure should keep existing installed pack artifacts", existingPackDir.exists())
    }

    @Test
    fun `performDownloadForTest - update checksum failure keeps update-available state`() = runBlocking {
        val packId = "update-checksum-failure-pack-${System.nanoTime()}"
        val previousVersion = "0.8.0"
        val payload = ByteArray(4096) { 10 }
        val tempDir = File(context.cacheDir, "packs_temp").apply { mkdirs() }
        val tempFile = File(tempDir, "$packId.tmp")
        tempFile.writeBytes(payload)

        val existingPackDir = File(context.filesDir, "packs/$packId").apply { mkdirs() }
        File(existingPackDir, "data.pack").writeBytes(ByteArray(128) { 1 })
        File(existingPackDir, "manifest.json").writeText(
            """{"id":"$packId","version":"$previousVersion","installedAt":1700000000000}"""
        )

        val result = service.performDownloadForTest(
            pack = makePack(
                packId = packId,
                downloadUrl = "http://127.0.0.1:1/should-not-be-used",
                sizeBytes = payload.size.toLong(),
                sha256 = "0000000000000000000000000000000000000000000000000000000000000000"
            ),
            resumeFrom = payload.size.toLong()
        )

        assertTrue(result is DownloadResult.Error)
        val error = (result as DownloadResult.Error).error
        assertTrue(error is DownloadError.VerificationFailed)
        assertEquals(PackStatus.UPDATE_AVAILABLE, service.packStates.value[packId]?.status)
        assertTrue("Update checksum failure should keep existing installed pack artifacts", existingPackDir.exists())
        assertTrue("Temp file should be removed after verification failure", !tempFile.exists())
    }

    @Test
    fun `performDownloadForTest - update install failure keeps update-available state`() = runBlocking {
        val packId = "update-install-failure-pack-${System.nanoTime()}"
        val previousVersion = "0.7.0"
        val existingPayload = ByteArray(128) { 1 }
        val payload = ByteArray(4096) { 11 }
        val expectedSha256 = sha256Hex(payload)
        val tempDir = File(context.cacheDir, "packs_temp").apply { mkdirs() }
        val tempFile = File(tempDir, "$packId.tmp")
        tempFile.writeBytes(payload)

        val existingPackDir = File(context.filesDir, "packs/$packId").apply { mkdirs() }
        val existingDataFile = File(existingPackDir, "data.pack")
        existingDataFile.writeBytes(existingPayload)
        File(existingPackDir, "manifest.json").writeText(
            """{"id":"$packId","version":"$previousVersion","installedAt":1700000000000}"""
        )

        service.forceInstallFailureAfterStagingForTest = true
        try {
            val result = service.performDownloadForTest(
                pack = makePack(
                    packId = packId,
                    downloadUrl = "http://127.0.0.1:1/should-not-be-used",
                    sizeBytes = payload.size.toLong(),
                    sha256 = expectedSha256
                ),
                resumeFrom = payload.size.toLong()
            )

            assertTrue(result is DownloadResult.Error)
            val error = (result as DownloadResult.Error).error
            assertTrue(error is DownloadError.InstallationFailed)

            val state = service.packStates.value[packId]
            assertEquals(PackStatus.UPDATE_AVAILABLE, state?.status)
            assertEquals(previousVersion, state?.installedVersion)
            assertTrue("Existing installed pack artifacts should remain after update install failure", existingPackDir.exists())
            assertArrayEquals(existingPayload, existingDataFile.readBytes())
            assertTrue("Temp file should be removed after install failure", !tempFile.exists())
            assertTrue("Staged data file should be cleaned after install failure", !File(existingPackDir, "data.pack.staged").exists())
            assertTrue("Staged manifest file should be cleaned after install failure", !File(existingPackDir, "manifest.json.staged").exists())
            assertTrue("Backup data file should be cleaned after install failure", !File(existingPackDir, "data.pack.backup").exists())
            assertTrue("Backup manifest file should be cleaned after install failure", !File(existingPackDir, "manifest.json.backup").exists())
        } finally {
            service.forceInstallFailureAfterStagingForTest = false
        }
    }

    @Test
    fun `performDownloadForTest - reinstall failure keeps installed state when version matches`() = runBlocking {
        val packId = "reinstall-failure-pack-${System.nanoTime()}"
        val currentVersion = "1.0.0"
        val existingPayload = ByteArray(128) { 6 }
        val payload = ByteArray(4096) { 15 }
        val expectedSha256 = sha256Hex(payload)
        val tempDir = File(context.cacheDir, "packs_temp").apply { mkdirs() }
        val tempFile = File(tempDir, "$packId.tmp")
        tempFile.writeBytes(payload)

        val existingPackDir = File(context.filesDir, "packs/$packId").apply { mkdirs() }
        val existingDataFile = File(existingPackDir, "data.pack")
        existingDataFile.writeBytes(existingPayload)
        File(existingPackDir, "manifest.json").writeText(
            """{"id":"$packId","version":"$currentVersion","installedAt":1700000000000}"""
        )

        service.forceInstallFailureForTest = true
        try {
            val result = service.performDownloadForTest(
                pack = makePack(
                    packId = packId,
                    downloadUrl = "http://127.0.0.1:1/should-not-be-used",
                    sizeBytes = payload.size.toLong(),
                    sha256 = expectedSha256
                ),
                resumeFrom = payload.size.toLong()
            )

            assertTrue(result is DownloadResult.Error)
            val error = (result as DownloadResult.Error).error
            assertTrue(error is DownloadError.InstallationFailed)

            val state = service.packStates.value[packId]
            assertEquals(PackStatus.INSTALLED, state?.status)
            assertEquals(currentVersion, state?.installedVersion)
            assertEquals(payload.size.toLong(), state?.downloadedBytes)
            assertArrayEquals(existingPayload, existingDataFile.readBytes())
            assertTrue("Temp file should be removed after install failure", !tempFile.exists())
        } finally {
            service.forceInstallFailureForTest = false
        }
    }

    @Test
    fun `performDownloadForTest - update manifest swap failure rolls back data and keeps update-available state`() = runBlocking {
        val packId = "update-manifest-swap-failure-pack-${System.nanoTime()}"
        val previousVersion = "0.6.0"
        val existingPayload = ByteArray(128) { 4 }
        val payload = ByteArray(4096) { 12 }
        val expectedSha256 = sha256Hex(payload)
        val tempDir = File(context.cacheDir, "packs_temp").apply { mkdirs() }
        val tempFile = File(tempDir, "$packId.tmp")
        tempFile.writeBytes(payload)

        val existingPackDir = File(context.filesDir, "packs/$packId").apply { mkdirs() }
        val existingDataFile = File(existingPackDir, "data.pack")
        existingDataFile.writeBytes(existingPayload)
        val existingManifestFile = File(existingPackDir, "manifest.json")
        existingManifestFile.writeText(
            """{"id":"$packId","version":"$previousVersion","installedAt":1700000000000}"""
        )

        service.forceManifestSwapFailureForTest = true
        try {
            val result = service.performDownloadForTest(
                pack = makePack(
                    packId = packId,
                    downloadUrl = "http://127.0.0.1:1/should-not-be-used",
                    sizeBytes = payload.size.toLong(),
                    sha256 = expectedSha256
                ),
                resumeFrom = payload.size.toLong()
            )

            assertTrue(result is DownloadResult.Error)
            val error = (result as DownloadResult.Error).error
            assertTrue(error is DownloadError.InstallationFailed)

            val state = service.packStates.value[packId]
            assertEquals(PackStatus.UPDATE_AVAILABLE, state?.status)
            assertEquals(previousVersion, state?.installedVersion)
            assertArrayEquals(existingPayload, existingDataFile.readBytes())
            assertTrue(
                "Manifest should remain on previous version after rollback",
                existingManifestFile.readText().contains("\"version\":\"$previousVersion\"")
            )
            assertTrue("Temp file should be removed after install failure", !tempFile.exists())
            assertTrue("Staged data file should be cleaned after rollback", !File(existingPackDir, "data.pack.staged").exists())
            assertTrue("Staged manifest file should be cleaned after rollback", !File(existingPackDir, "manifest.json.staged").exists())
            assertTrue("Backup data file should be cleaned after rollback", !File(existingPackDir, "data.pack.backup").exists())
            assertTrue("Backup manifest file should be cleaned after rollback", !File(existingPackDir, "manifest.json.backup").exists())
        } finally {
            service.forceManifestSwapFailureForTest = false
        }
    }

    @Test
    fun `performDownloadForTest - update install failure with missing installed artifacts falls back to failed state`() = runBlocking {
        val packId = "update-install-restore-missing-pack-${System.nanoTime()}"
        val previousVersion = "0.5.0"
        val payload = ByteArray(4096) { 14 }
        val expectedSha256 = sha256Hex(payload)
        val tempDir = File(context.cacheDir, "packs_temp").apply { mkdirs() }
        val tempFile = File(tempDir, "$packId.tmp")
        tempFile.writeBytes(payload)

        val existingPackDir = File(context.filesDir, "packs/$packId").apply { mkdirs() }
        File(existingPackDir, "data.pack").writeBytes(ByteArray(128) { 5 })
        File(existingPackDir, "manifest.json").writeText(
            """{"id":"$packId","version":"$previousVersion","installedAt":1700000000000}"""
        )

        service.forceInstallFailureForTest = true
        service.onAfterInstallAttemptForTest = { attemptedPackId, installSucceeded ->
            if (attemptedPackId == packId && !installSucceeded) {
                existingPackDir.deleteRecursively()
            }
        }

        try {
            val result = service.performDownloadForTest(
                pack = makePack(
                    packId = packId,
                    downloadUrl = "http://127.0.0.1:1/should-not-be-used",
                    sizeBytes = payload.size.toLong(),
                    sha256 = expectedSha256
                ),
                resumeFrom = payload.size.toLong()
            )

            assertTrue(result is DownloadResult.Error)
            val error = (result as DownloadResult.Error).error
            assertTrue(error is DownloadError.InstallationFailed)

            val state = service.packStates.value[packId]
            assertEquals(PackStatus.FAILED, state?.status)
            assertNull(state?.installedVersion)
            assertNull(state?.installedAt)
        } finally {
            service.forceInstallFailureForTest = false
            service.onAfterInstallAttemptForTest = null
        }
    }

    @Test
    fun `performDownloadForTest - new install manifest swap failure cleans partial artifacts`() = runBlocking {
        val packId = "new-install-manifest-swap-failure-pack-${System.nanoTime()}"
        val payload = ByteArray(4096) { 13 }
        val expectedSha256 = sha256Hex(payload)
        val tempDir = File(context.cacheDir, "packs_temp").apply { mkdirs() }
        val tempFile = File(tempDir, "$packId.tmp")
        tempFile.writeBytes(payload)
        val packDir = File(context.filesDir, "packs/$packId")

        service.forceManifestSwapFailureForTest = true
        try {
            val result = service.performDownloadForTest(
                pack = makePack(
                    packId = packId,
                    downloadUrl = "http://127.0.0.1:1/should-not-be-used",
                    sizeBytes = payload.size.toLong(),
                    sha256 = expectedSha256
                ),
                resumeFrom = payload.size.toLong()
            )

            assertTrue(result is DownloadResult.Error)
            val error = (result as DownloadResult.Error).error
            assertTrue(error is DownloadError.InstallationFailed)

            val state = service.packStates.value[packId]
            assertEquals(PackStatus.FAILED, state?.status)
            assertTrue("Failed new install should clean partial pack directory", !packDir.exists())
            assertTrue("Temp file should be removed after install failure", !tempFile.exists())
        } finally {
            service.forceManifestSwapFailureForTest = false
        }
    }

    @Test
    fun `performDownloadForTest - cancel during verification keeps not-downloaded state`() = runBlocking {
        val packId = "cancel-during-verify-pack-${System.nanoTime()}"
        val payload = ByteArray(64 * 1024 * 1024) { index -> (index % 239).toByte() }
        val expectedSha256 = sha256Hex(payload)
        val tempDir = File(context.cacheDir, "packs_temp").apply { mkdirs() }
        val tempFile = File(tempDir, "$packId.tmp")
        tempFile.writeBytes(payload)
        val pack = makePack(
            packId = packId,
            downloadUrl = "http://127.0.0.1:1/should-not-be-used",
            sizeBytes = payload.size.toLong(),
            sha256 = expectedSha256
        )

        val downloadResult = async(Dispatchers.Default) {
            service.performDownloadForTest(pack = pack, resumeFrom = payload.size.toLong())
        }

        waitForCondition("verification to start for $packId") {
            service.packStates.value[packId]?.status == PackStatus.VERIFYING
        }
        service.cancelDownload(packId)

        val result = downloadResult.await()
        assertTrue(result is DownloadResult.Progress)
        assertEquals(0L, (result as DownloadResult.Progress).downloadedBytes)

        val state = service.packStates.value[packId]
        assertEquals(PackStatus.NOT_DOWNLOADED, state?.status)
        assertEquals(0L, state?.downloadedBytes)

        val installedFile = File(context.filesDir, "packs/$packId/data.pack")
        assertTrue("Cancelled verification should not install the pack", !installedFile.exists())
        assertTrue("Cancelled verification should remove temp file", !tempFile.exists())
    }

    @Test
    fun `performDownloadForTest - cancel after install attempt keeps not-downloaded state`() = runBlocking {
        val packId = "cancel-after-install-pack-${System.nanoTime()}"
        val payload = ByteArray(4096) { 8 }
        val expectedSha256 = sha256Hex(payload)
        val tempDir = File(context.cacheDir, "packs_temp").apply { mkdirs() }
        val tempFile = File(tempDir, "$packId.tmp")
        tempFile.writeBytes(payload)

        service.onAfterInstallAttemptForTest = { attemptedPackId, installSucceeded ->
            if (attemptedPackId == packId && installSucceeded) {
                runBlocking { service.cancelDownload(packId) }
            }
        }

        try {
            val result = service.performDownloadForTest(
                pack = makePack(
                    packId = packId,
                    downloadUrl = "http://127.0.0.1:1/should-not-be-used",
                    sizeBytes = payload.size.toLong(),
                    sha256 = expectedSha256
                ),
                resumeFrom = payload.size.toLong()
            )

            assertTrue(result is DownloadResult.Progress)
            assertEquals(0L, (result as DownloadResult.Progress).downloadedBytes)

            val state = service.packStates.value[packId]
            assertEquals(PackStatus.NOT_DOWNLOADED, state?.status)
            assertEquals(0L, state?.downloadedBytes)

            val packDir = File(context.filesDir, "packs/$packId")
            assertTrue("Cancelled install should not leave installed pack artifacts", !packDir.exists())
            assertTrue("Cancelled install should remove temp file", !tempFile.exists())
        } finally {
            service.onAfterInstallAttemptForTest = null
        }
    }

    @Test
    fun `performDownloadForTest - cancel update after install attempt keeps installed state`() = runBlocking {
        val packId = "cancel-update-after-install-pack-${System.nanoTime()}"
        val previousVersion = "0.9.0"
        val payload = ByteArray(4096) { 9 }
        val expectedSha256 = sha256Hex(payload)
        val tempDir = File(context.cacheDir, "packs_temp").apply { mkdirs() }
        val tempFile = File(tempDir, "$packId.tmp")
        tempFile.writeBytes(payload)

        val existingPackDir = File(context.filesDir, "packs/$packId").apply { mkdirs() }
        File(existingPackDir, "data.pack").writeBytes(ByteArray(1024) { 1 })
        File(existingPackDir, "manifest.json").writeText(
            """{"id":"$packId","version":"$previousVersion","installedAt":1700000000000}"""
        )

        service.onAfterInstallAttemptForTest = { attemptedPackId, installSucceeded ->
            if (attemptedPackId == packId && installSucceeded) {
                runBlocking { service.cancelDownload(packId) }
            }
        }

        try {
            val result = service.performDownloadForTest(
                pack = makePack(
                    packId = packId,
                    downloadUrl = "http://127.0.0.1:1/should-not-be-used",
                    sizeBytes = payload.size.toLong(),
                    sha256 = expectedSha256
                ),
                resumeFrom = payload.size.toLong()
            )

            assertTrue(result is DownloadResult.Progress)
            assertEquals(0L, (result as DownloadResult.Progress).downloadedBytes)

            val state = service.packStates.value[packId]
            assertEquals(PackStatus.INSTALLED, state?.status)
            assertEquals("1.0.0", state?.installedVersion)

            val packDir = File(context.filesDir, "packs/$packId")
            assertTrue("Cancelled update should keep installed pack artifacts", packDir.exists())
            assertTrue("Cancelled update should keep pack manifest", File(packDir, "manifest.json").exists())
            assertTrue("Cancelled update should remove temp file", !tempFile.exists())
        } finally {
            service.onAfterInstallAttemptForTest = null
        }
    }

    @Test
    fun `performDownloadForTest - remove update after install attempt keeps not-downloaded state`() = runBlocking {
        val packId = "remove-update-after-install-pack-${System.nanoTime()}"
        val previousVersion = "0.9.0"
        val payload = ByteArray(4096) { 10 }
        val expectedSha256 = sha256Hex(payload)
        val tempDir = File(context.cacheDir, "packs_temp").apply { mkdirs() }
        val tempFile = File(tempDir, "$packId.tmp")
        tempFile.writeBytes(payload)

        val existingPackDir = File(context.filesDir, "packs/$packId").apply { mkdirs() }
        File(existingPackDir, "data.pack").writeBytes(ByteArray(1024) { 1 })
        File(existingPackDir, "manifest.json").writeText(
            """{"id":"$packId","version":"$previousVersion","installedAt":1700000000000}"""
        )

        val pack = makePack(
            packId = packId,
            downloadUrl = "http://127.0.0.1:1/should-not-be-used",
            sizeBytes = payload.size.toLong(),
            sha256 = expectedSha256
        )
        service.replaceAvailablePacksForTest(listOf(pack))

        service.onAfterInstallAttemptForTest = { attemptedPackId, installSucceeded ->
            if (attemptedPackId == packId && installSucceeded) {
                runBlocking { service.removePack(packId) }
            }
        }

        try {
            val result = service.performDownloadForTest(pack = pack, resumeFrom = payload.size.toLong())

            assertTrue(result is DownloadResult.Progress)
            assertEquals(0L, (result as DownloadResult.Progress).downloadedBytes)

            val state = service.packStates.value[packId]
            assertEquals(PackStatus.NOT_DOWNLOADED, state?.status)
            assertEquals(0L, state?.downloadedBytes)

            val packDir = File(context.filesDir, "packs/$packId")
            assertTrue("Removed update should not leave installed pack artifacts", !packDir.exists())
            assertTrue("Removed update should remove temp file", !tempFile.exists())
            assertTrue(
                "Removed update should remove resume file",
                !File(context.cacheDir, "packs_temp/$packId.resume").exists()
            )

            service.replaceAvailablePacksAndReloadForTest(listOf(pack))
            val reloadedState = service.packStates.value[packId]
            assertEquals(PackStatus.NOT_DOWNLOADED, reloadedState?.status)
            assertEquals(0L, reloadedState?.downloadedBytes)
        } finally {
            service.onAfterInstallAttemptForTest = null
        }
    }

    @Test
    fun `performDownloadForTest - cancel update during verification keeps update-available state`() = runBlocking {
        val packId = "cancel-update-during-verify-pack-${System.nanoTime()}"
        val previousVersion = "0.8.0"
        val payload = ByteArray(64 * 1024 * 1024) { index -> (index % 233).toByte() }
        val expectedSha256 = sha256Hex(payload)
        val tempDir = File(context.cacheDir, "packs_temp").apply { mkdirs() }
        val tempFile = File(tempDir, "$packId.tmp")
        tempFile.writeBytes(payload)

        val existingPackDir = File(context.filesDir, "packs/$packId").apply { mkdirs() }
        File(existingPackDir, "data.pack").writeBytes(ByteArray(1024) { 2 })
        File(existingPackDir, "manifest.json").writeText(
            """{"id":"$packId","version":"$previousVersion","installedAt":1700000000000}"""
        )

        val pack = makePack(
            packId = packId,
            downloadUrl = "http://127.0.0.1:1/should-not-be-used",
            sizeBytes = payload.size.toLong(),
            sha256 = expectedSha256
        )
        val downloadResult = async(Dispatchers.Default) {
            service.performDownloadForTest(pack = pack, resumeFrom = payload.size.toLong())
        }

        waitForCondition("verification to start for $packId") {
            service.packStates.value[packId]?.status == PackStatus.VERIFYING
        }
        service.cancelDownload(packId)

        val result = downloadResult.await()
        assertTrue(result is DownloadResult.Progress)
        assertEquals(0L, (result as DownloadResult.Progress).downloadedBytes)

        val state = service.packStates.value[packId]
        assertEquals(PackStatus.UPDATE_AVAILABLE, state?.status)
        assertEquals(previousVersion, state?.installedVersion)

        val packDir = File(context.filesDir, "packs/$packId")
        assertTrue("Cancelled update should keep installed pack artifacts", packDir.exists())
        assertTrue("Cancelled update should keep pack manifest", File(packDir, "manifest.json").exists())
        assertTrue("Cancelled verification should remove temp file", !tempFile.exists())
    }

    @Test
    fun `public api pause then cancel update keeps update-available state`() = runBlocking {
        configureConnectedWifiNetwork()

        val packId = "pause-cancel-update-pack-${System.nanoTime()}"
        val previousVersion = "0.6.0"
        val payload = ByteArray(8 * 1024 * 1024) { index -> (index % 227).toByte() }
        val expectedSha256 = sha256Hex(payload)
        val existingPackDir = File(context.filesDir, "packs/$packId").apply { mkdirs() }
        File(existingPackDir, "data.pack").writeBytes(ByteArray(1024) { 3 })
        File(existingPackDir, "manifest.json").writeText(
            """{"id":"$packId","version":"$previousVersion","installedAt":1700000000000}"""
        )

        val server = LocalRangeServer(payload, firstRequestChunkDelayMs = 2)
        server.start()

        try {
            val pack = makePack(
                packId = packId,
                downloadUrl = server.url,
                sizeBytes = payload.size.toLong(),
                sha256 = expectedSha256
            )
            service.replaceAvailablePacksForTest(listOf(pack))
            service.availableSpaceOverrideBytesForTest = Long.MAX_VALUE / 4
            service.updatePreferences(DownloadPreferences(wifiOnly = true, autoUpdate = false))

            val firstAttempt = async(Dispatchers.Default) { service.startDownload(packId) }
            waitForCondition("initial payload bytes to be served") {
                server.bytesServed() > 0
            }
            service.pauseDownload(packId)
            val firstResult = firstAttempt.await()
            assertTrue(
                "First attempt should pause with progress, got ${describeResult(firstResult)}",
                firstResult is DownloadResult.Progress
            )

            service.cancelDownload(packId)

            val state = service.packStates.value[packId]
            assertEquals(PackStatus.UPDATE_AVAILABLE, state?.status)
            assertEquals(previousVersion, state?.installedVersion)
            assertTrue("Cancelled paused update should keep installed pack artifacts", existingPackDir.exists())
        } finally {
            server.stop()
        }
    }

    @Test
    fun `parseInstalledPackMetadata parses compact manifest json`() {
        val metadata = service.parseInstalledPackMetadata(
            """{"id":"medina-map","version":"2026.02.01","installedAt":1739059200000}"""
        )

        assertEquals("2026.02.01", metadata.version)
        assertEquals(1739059200000L, metadata.installedAt)
    }

    @Test
    fun `parseInstalledPackMetadata parses manifest with whitespace`() {
        val metadata = service.parseInstalledPackMetadata(
            """
            {
              "id": "medina-map",
              "version" : "2026.03.00",
              "installedAt" : 1739150000000
            }
            """.trimIndent()
        )

        assertEquals("2026.03.00", metadata.version)
        assertEquals(1739150000000L, metadata.installedAt)
    }

    @Test
    fun `parseInstalledPackMetadata returns null fields when absent`() {
        val metadata = service.parseInstalledPackMetadata("""{"id":"medina-map"}""")

        assertNull(metadata.version)
        assertNull(metadata.installedAt)
    }

    @Test
    fun `performDownloadForTest - pause and resume via local http server uses range and installs full payload`() = runBlocking {
        val packId = "resume-pack-${System.nanoTime()}"
        val payload = ByteArray(8 * 1024 * 1024) { index -> (index % 251).toByte() }
        val expectedSha256 = sha256Hex(payload)
        val server = LocalRangeServer(payload, firstRequestChunkDelayMs = 2)
        server.start()

        try {
            val downloadUrl = server.url
            val pack = makePack(
                packId = packId,
                downloadUrl = downloadUrl,
                sizeBytes = payload.size.toLong(),
                sha256 = expectedSha256
            )

            val firstAttempt = async(Dispatchers.Default) {
                service.performDownloadForTest(pack = pack, resumeFrom = 0)
            }

            waitForCondition("initial payload bytes to be served") {
                server.bytesServed() > 0
            }
            service.deactivateDownloadForTest(packId)
            val firstResult = firstAttempt.await()

            assertTrue(
                "First attempt should pause and return progress, got ${describeResult(firstResult)}",
                firstResult is DownloadResult.Progress
            )
            val pausedProgress = (firstResult as DownloadResult.Progress).downloadedBytes
            assertTrue("Paused progress should be > 0", pausedProgress > 0)
            assertTrue(
                "Paused progress should be less than total bytes",
                pausedProgress < payload.size.toLong()
            )

            val secondResult = service.performDownloadForTest(pack = pack, resumeFrom = pausedProgress)
            assertTrue("Second attempt should complete download", secondResult is DownloadResult.Success)

            assertTrue("Expected at least two HTTP requests", server.requestCount() >= 2)
            val lastRangeHeader = server.lastRangeHeader()
            assertEquals("bytes=$pausedProgress-", lastRangeHeader)

            val installedFile = File(context.filesDir, "packs/$packId/data.pack")
            assertTrue("Installed pack file should exist", installedFile.exists())
            assertArrayEquals(payload, installedFile.readBytes())
        } finally {
            server.stop()
        }
    }

    @Test
    fun `public api start pause resume uses range and installs full payload`() = runBlocking {
        configureConnectedWifiNetwork()

        val packId = "public-api-pack-${System.nanoTime()}"
        val payload = ByteArray(8 * 1024 * 1024) { index -> (index % 241).toByte() }
        val expectedSha256 = sha256Hex(payload)
        val server = LocalRangeServer(payload, firstRequestChunkDelayMs = 2)
        server.start()

        try {
            val pack = makePack(
                packId = packId,
                downloadUrl = server.url,
                sizeBytes = payload.size.toLong(),
                sha256 = expectedSha256
            )
            service.replaceAvailablePacksForTest(listOf(pack))
            service.availableSpaceOverrideBytesForTest = Long.MAX_VALUE / 4
            service.updatePreferences(DownloadPreferences(wifiOnly = true, autoUpdate = false))

            val firstAttempt = async(Dispatchers.Default) { service.startDownload(packId) }
            waitForCondition("initial payload bytes to be served") {
                server.bytesServed() > 0
            }
            service.pauseDownload(packId)
            val firstResult = firstAttempt.await()

            assertTrue(
                "First attempt should pause and return progress, got ${describeResult(firstResult)}",
                firstResult is DownloadResult.Progress
            )
            val pausedProgress = (firstResult as DownloadResult.Progress).downloadedBytes
            assertTrue(pausedProgress > 0)
            assertTrue(pausedProgress < payload.size.toLong())
            val resumeStart = service.packStates.value[packId]?.downloadedBytes ?: -1
            assertTrue(
                "Expected persisted paused state bytes > 0, got $resumeStart",
                resumeStart > 0
            )
            assertTrue(
                "Persisted paused bytes should be <= immediate progress ($resumeStart <= $pausedProgress)",
                resumeStart <= pausedProgress
            )

            val resumedResult = service.resumeDownload(packId)
            assertTrue("Resume should complete successfully", resumedResult is DownloadResult.Success)

            assertTrue("Expected at least two HTTP requests", server.requestCount() >= 2)
            assertEquals("bytes=$resumeStart-", server.lastRangeHeader())

            val installedFile = File(context.filesDir, "packs/$packId/data.pack")
            assertTrue("Installed pack file should exist", installedFile.exists())
            assertArrayEquals(payload, installedFile.readBytes())
        } finally {
            server.stop()
        }
    }

    @Test
    fun `public api startDownload - concurrent second attempt returns already in progress`() = runBlocking {
        configureConnectedWifiNetwork()

        val packId = "public-concurrent-pack-${System.nanoTime()}"
        val payload = ByteArray(4 * 1024 * 1024) { index -> (index % 199).toByte() }
        val expectedSha256 = sha256Hex(payload)
        val server = LocalRangeServer(payload, firstRequestChunkDelayMs = 3)
        server.start()

        try {
            val pack = makePack(
                packId = packId,
                downloadUrl = server.url,
                sizeBytes = payload.size.toLong(),
                sha256 = expectedSha256
            )
            service.replaceAvailablePacksForTest(listOf(pack))
            service.availableSpaceOverrideBytesForTest = Long.MAX_VALUE / 4
            service.updatePreferences(DownloadPreferences(wifiOnly = true, autoUpdate = false))

            val firstAttempt = async(Dispatchers.Default) { service.startDownload(packId) }
            waitForCondition("first download request to start") {
                server.requestCount() >= 1
            }

            val secondResult = service.startDownload(packId)
            assertTrue(
                "Second concurrent start should be rejected, got ${describeResult(secondResult)}",
                secondResult is DownloadResult.Error
            )
            val secondError = (secondResult as DownloadResult.Error).error
            assertTrue(secondError is DownloadError.Unknown)
            assertEquals("Download already in progress", (secondError as DownloadError.Unknown).message)

            service.pauseDownload(packId)
            val firstResult = firstAttempt.await()
            assertTrue(
                "First attempt should either pause with progress or finish successfully, got ${describeResult(firstResult)}",
                firstResult is DownloadResult.Progress || firstResult is DownloadResult.Success
            )
        } finally {
            server.stop()
        }
    }

    @Test
    fun `performDownloadForTest - concurrent second attempt returns already in progress`() = runBlocking {
        val packId = "concurrent-pack-${System.nanoTime()}"
        val payload = ByteArray(4 * 1024 * 1024) { index -> (index % 223).toByte() }
        val expectedSha256 = sha256Hex(payload)
        val server = LocalRangeServer(payload, firstRequestChunkDelayMs = 3)
        server.start()

        try {
            val pack = makePack(
                packId = packId,
                downloadUrl = server.url,
                sizeBytes = payload.size.toLong(),
                sha256 = expectedSha256
            )

            val firstAttempt = async(Dispatchers.Default) {
                service.performDownloadForTest(pack = pack, resumeFrom = 0)
            }

            waitForCondition("first download request to start") {
                server.requestCount() >= 1
            }
            val secondResult = service.performDownloadForTest(pack = pack, resumeFrom = 0)
            assertTrue(
                "Second attempt should be rejected while first is active, got ${describeResult(secondResult)}",
                secondResult is DownloadResult.Error
            )
            val secondError = (secondResult as DownloadResult.Error).error
            assertTrue(secondError is DownloadError.Unknown)
            assertEquals("Download already in progress", (secondError as DownloadError.Unknown).message)

            service.deactivateDownloadForTest(packId)
            val firstResult = firstAttempt.await()
            assertTrue(
                "First attempt should either pause with progress or finish successfully, got ${describeResult(firstResult)}",
                firstResult is DownloadResult.Progress || firstResult is DownloadResult.Success
            )
        } finally {
            server.stop()
        }
    }

    @Test
    fun `performDownloadForTest - stale interrupted session does not override newer active session`() = runBlocking staleSession@{
        val packId = "stale-session-pack-${System.nanoTime()}"
        val firstUrl = "mockhttp://download/$packId/first"
        val secondUrl = "mockhttp://download/$packId/second"
        val firstPayload = ByteArray(128 * 1024) { 3 }
        val secondPayload = ByteArray(128 * 1024) { 7 }
        val secondSha256 = sha256Hex(secondPayload)

        val secondStarted = CountDownLatch(1)
        val releaseSecond = CountDownLatch(1)
        var secondAttempt: kotlinx.coroutines.Deferred<DownloadResult>? = null

        val secondConnection = FakeHttpURLConnection(
            url = URL(secondUrl),
            responseCodeValue = 200
        ) {
            CallbackInputStream(ByteArrayInputStream(secondPayload)) {
                secondStarted.countDown()
                assertTrue(
                    "Second download should remain blocked until released by test",
                    releaseSecond.await(2, TimeUnit.SECONDS)
                )
            }
        }
        registerConnection(secondUrl, secondConnection)

        val firstConnection = FakeHttpURLConnection(
            url = URL(firstUrl),
            responseCodeValue = 200
        ) {
            ReadCountCallbackInputStream(
                delegate = ByteArrayInputStream(firstPayload),
                triggerReadCount = 1
            ) {
                service.deactivateDownloadForTest(packId)
                secondAttempt = this@staleSession.async(Dispatchers.Default) {
                    service.performDownloadForTest(
                        pack = makePack(
                            packId = packId,
                            downloadUrl = secondUrl,
                            sizeBytes = secondPayload.size.toLong(),
                            sha256 = secondSha256
                        ),
                        resumeFrom = 0
                    )
                }
                assertTrue(
                    "Second session should become active before stale first session continues",
                    secondStarted.await(2, TimeUnit.SECONDS)
                )
            }
        }
        registerConnection(firstUrl, firstConnection)

        val firstResult = service.performDownloadForTest(
            pack = makePack(
                packId = packId,
                downloadUrl = firstUrl,
                sizeBytes = firstPayload.size.toLong(),
                sha256 = sha256Hex(firstPayload)
            ),
            resumeFrom = 0
        )

        assertTrue(firstResult is DownloadResult.Progress)
        assertEquals(0L, (firstResult as DownloadResult.Progress).downloadedBytes)

        val stateWhileSecondActive = service.packStates.value[packId]
        assertEquals(PackStatus.DOWNLOADING, stateWhileSecondActive?.status)
        assertEquals(0L, stateWhileSecondActive?.downloadedBytes)

        releaseSecond.countDown()
        val secondResult = secondAttempt?.await() ?: error("Second download attempt did not start")
        assertTrue(
            "Second session should complete successfully, got ${describeResult(secondResult)}",
            secondResult is DownloadResult.Success
        )

        val installedFile = File(context.filesDir, "packs/$packId/data.pack")
        assertTrue("Installed pack file should exist after second session", installedFile.exists())
        assertArrayEquals(secondPayload, installedFile.readBytes())
    }

    @Test
    fun `performDownloadForTest - stale http error session does not cancel newer active session`() = runBlocking staleHttpSession@{
        val packId = "stale-http-error-pack-${System.nanoTime()}"
        val firstUrl = "mockhttp://download/$packId/first"
        val secondUrl = "mockhttp://download/$packId/second"
        val secondPayload = ByteArray(128 * 1024) { 11 }
        val secondSha256 = sha256Hex(secondPayload)

        val firstResponseRequested = CountDownLatch(1)
        val releaseFirstResponse = CountDownLatch(1)
        val secondReadStarted = CountDownLatch(1)
        val releaseSecondRead = CountDownLatch(1)

        val secondConnection = FakeHttpURLConnection(
            url = URL(secondUrl),
            responseCodeValue = 200
        ) {
            CallbackInputStream(ByteArrayInputStream(secondPayload)) {
                secondReadStarted.countDown()
                assertTrue(
                    "Second session should remain blocked until stale HTTP branch is released",
                    releaseSecondRead.await(2, TimeUnit.SECONDS)
                )
            }
        }
        registerConnection(secondUrl, secondConnection)

        val firstConnection = FakeHttpURLConnection(
            url = URL(firstUrl),
            responseCodeValue = 503,
            inputStreamFactory = { ByteArrayInputStream(ByteArray(0)) },
            responseCodeProvider = {
                firstResponseRequested.countDown()
                assertTrue(
                    "First session response should stay blocked until second session is active",
                    releaseFirstResponse.await(2, TimeUnit.SECONDS)
                )
                503
            }
        )
        registerConnection(firstUrl, firstConnection)

        val firstAttempt = async(Dispatchers.Default) {
            service.performDownloadForTest(
                pack = makePack(
                    packId = packId,
                    downloadUrl = firstUrl,
                    sizeBytes = secondPayload.size.toLong(),
                    sha256 = "unused"
                ),
                resumeFrom = 0
            )
        }

        assertTrue(
            "First session should reach response-code wait point",
            firstResponseRequested.await(2, TimeUnit.SECONDS)
        )

        service.deactivateDownloadForTest(packId)

        val secondAttempt = this@staleHttpSession.async(Dispatchers.Default) {
            service.performDownloadForTest(
                pack = makePack(
                    packId = packId,
                    downloadUrl = secondUrl,
                    sizeBytes = secondPayload.size.toLong(),
                    sha256 = secondSha256
                ),
                resumeFrom = 0
            )
        }

        assertTrue(
            "Second session should begin reading before first session is released",
            secondReadStarted.await(2, TimeUnit.SECONDS)
        )

        releaseFirstResponse.countDown()
        val firstResult = firstAttempt.await()
        assertTrue(
            "Stale first session should exit as interruption progress, got ${describeResult(firstResult)}",
            firstResult is DownloadResult.Progress
        )
        assertEquals(0L, (firstResult as DownloadResult.Progress).downloadedBytes)

        val stateWhileSecondBlocked = service.packStates.value[packId]
        assertEquals(PackStatus.DOWNLOADING, stateWhileSecondBlocked?.status)

        releaseSecondRead.countDown()
        val secondResult = secondAttempt.await()
        assertTrue(
            "Second session should still succeed after stale first HTTP error branch, got ${describeResult(secondResult)}",
            secondResult is DownloadResult.Success
        )

        val installedFile = File(context.filesDir, "packs/$packId/data.pack")
        assertTrue("Installed file should exist after second session success", installedFile.exists())
        assertArrayEquals(secondPayload, installedFile.readBytes())
    }

    private fun makePack(
        packId: String,
        downloadUrl: String,
        sizeBytes: Long,
        sha256: String = "unused"
    ): ContentPack {
        return ContentPack(
            id = packId,
            type = PackType.MEDINA_MAP,
            displayName = "Test Pack",
            description = "Test only",
            version = "1.0.0",
            sizeBytes = sizeBytes,
            sha256 = sha256,
            downloadUrl = downloadUrl
        )
    }

    private fun parseRangeStart(rangeHeader: String?): Int {
        if (rangeHeader == null) return 0
        val prefix = "bytes="
        if (!rangeHeader.startsWith(prefix)) return 0
        val startText = rangeHeader.removePrefix(prefix).substringBefore('-')
        return startText.toIntOrNull() ?: 0
    }

    private fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun describeResult(result: DownloadResult): String {
        return when (result) {
            is DownloadResult.Progress -> "Progress(${result.downloadedBytes}/${result.totalBytes})"
            is DownloadResult.Success -> "Success(${result.packId})"
            is DownloadResult.Error -> "Error(${result.error::class.simpleName})"
        }
    }

    private suspend fun waitForCondition(
        description: String,
        timeoutMs: Long = 3_000,
        pollIntervalMs: Long = 20,
        condition: () -> Boolean
    ) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (condition()) {
                return
            }
            delay(pollIntervalMs)
        }
        assertTrue("Timed out waiting for $description", condition())
    }

    private fun configureConnectedWifiNetwork() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val shadowConnectivityManager: ShadowConnectivityManager = shadowOf(connectivityManager)
        val networkInfo = ShadowNetworkInfo.newInstance(
            NetworkInfo.DetailedState.CONNECTED,
            ConnectivityManager.TYPE_WIFI,
            0,
            true,
            true
        )
        shadowConnectivityManager.setActiveNetworkInfo(networkInfo)
        shadowConnectivityManager.setDefaultNetworkActive(true)

        val activeNetwork = connectivityManager.activeNetwork
        requireNotNull(activeNetwork) { "Expected active network to be configured in test" }

        val capabilities = ShadowNetworkCapabilities.newInstance()
        val shadowCapabilities = shadowOf(capabilities)
        shadowCapabilities.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        shadowCapabilities.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowConnectivityManager.setNetworkCapabilities(activeNetwork, capabilities)
    }

    private class LocalRangeServer(
        private val payload: ByteArray,
        private val firstRequestChunkDelayMs: Long = 0
    ) {
        private val requestCounter = AtomicInteger(0)
        private val bytesServedCounter = AtomicInteger(0)
        private val ranges = Collections.synchronizedList(mutableListOf<String?>())
        private val serverSocket = ServerSocket(0, 8)
        @Volatile
        private var running = false
        private var acceptThread: Thread? = null

        val url: String
            get() = "http://127.0.0.1:${serverSocket.localPort}/pack"

        fun start() {
            running = true
            acceptThread = thread(name = "LocalRangeServer-Accept", isDaemon = true) {
                while (running) {
                    try {
                        val socket = serverSocket.accept()
                        thread(name = "LocalRangeServer-Client", isDaemon = true) {
                            handleClient(socket)
                        }
                    } catch (_: SocketException) {
                        if (!running) return@thread
                    }
                }
            }
        }

        fun stop() {
            running = false
            runCatching { serverSocket.close() }
            acceptThread?.join(1_000)
        }

        fun requestCount(): Int = requestCounter.get()

        fun bytesServed(): Int = bytesServedCounter.get()

        fun lastRangeHeader(): String? = synchronized(ranges) { ranges.lastOrNull() }

        private fun handleClient(socket: java.net.Socket) {
            socket.use { client ->
                val input = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.US_ASCII))
                val output = BufferedOutputStream(client.getOutputStream())

                val requestLine = input.readLine() ?: return
                if (!requestLine.startsWith("GET ")) {
                    return
                }

                var rangeHeader: String? = null
                while (true) {
                    val headerLine = input.readLine() ?: break
                    if (headerLine.isEmpty()) break
                    val separator = headerLine.indexOf(':')
                    if (separator <= 0) continue
                    val name = headerLine.substring(0, separator).trim()
                    val value = headerLine.substring(separator + 1).trim()
                    if (name.equals("Range", ignoreCase = true)) {
                        rangeHeader = value
                    }
                }

                requestCounter.incrementAndGet()
                ranges.add(rangeHeader)

                val start = parseRangeStart(rangeHeader)
                val body = if (start >= payload.size) {
                    ByteArray(0)
                } else {
                    payload.copyOfRange(start, payload.size)
                }

                if (rangeHeader != null) {
                    writeHeaders(
                        output = output,
                        statusLine = "HTTP/1.1 206 Partial Content",
                        contentLength = body.size,
                        contentRange = "bytes $start-${payload.size - 1}/${payload.size}"
                    )
                } else {
                    writeHeaders(
                        output = output,
                        statusLine = "HTTP/1.1 200 OK",
                        contentLength = body.size,
                        contentRange = null
                    )
                }

                val chunkDelayMs = if (rangeHeader == null) firstRequestChunkDelayMs else 0L
                var offset = 0
                while (offset < body.size && running) {
                    val chunkSize = minOf(8 * 1024, body.size - offset)
                    try {
                        output.write(body, offset, chunkSize)
                        output.flush()
                        bytesServedCounter.addAndGet(chunkSize)
                        offset += chunkSize
                        if (chunkDelayMs > 0) {
                            Thread.sleep(chunkDelayMs)
                        }
                    } catch (_: Exception) {
                        // Client can disconnect mid-transfer when we force a pause.
                        break
                    }
                }
            }
        }

        private fun writeHeaders(
            output: BufferedOutputStream,
            statusLine: String,
            contentLength: Int,
            contentRange: String?
        ) {
            val headerBuilder = StringBuilder()
                .append(statusLine).append("\r\n")
                .append("Content-Length: ").append(contentLength).append("\r\n")
                .append("Connection: close").append("\r\n")
            if (contentRange != null) {
                headerBuilder.append("Content-Range: ").append(contentRange).append("\r\n")
            }
            headerBuilder.append("\r\n")
            output.write(headerBuilder.toString().toByteArray(Charsets.US_ASCII))
            output.flush()
        }

        private fun parseRangeStart(rangeHeader: String?): Int {
            if (rangeHeader == null) return 0
            val prefix = "bytes="
            if (!rangeHeader.startsWith(prefix)) return 0
            val startText = rangeHeader.removePrefix(prefix).substringBefore('-')
            return startText.toIntOrNull() ?: 0
        }
    }

    private class CallbackInputStream(
        private val delegate: InputStream,
        private val onFirstRead: () -> Unit
    ) : InputStream() {
        private var hasTriggered = false

        override fun read(): Int {
            triggerOnce()
            return delegate.read()
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            triggerOnce()
            return delegate.read(b, off, len)
        }

        override fun close() {
            delegate.close()
        }

        private fun triggerOnce() {
            if (!hasTriggered) {
                hasTriggered = true
                onFirstRead()
            }
        }
    }

    private class ReadCountCallbackInputStream(
        private val delegate: InputStream,
        private val triggerReadCount: Int,
        private val onTrigger: () -> Unit
    ) : InputStream() {
        private var readCount = 0
        private var triggered = false

        override fun read(): Int {
            val value = delegate.read()
            if (value != -1) {
                onRead()
            }
            return value
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val bytesRead = delegate.read(b, off, len)
            if (bytesRead != -1) {
                onRead()
            }
            return bytesRead
        }

        override fun close() {
            delegate.close()
        }

        private fun onRead() {
            readCount += 1
            if (!triggered && readCount >= triggerReadCount) {
                triggered = true
                onTrigger()
            }
        }
    }

    private class CancelAndThrowInputStream(
        private val onTrigger: () -> Unit
    ) : InputStream() {
        private var triggered = false

        override fun read(): Int {
            triggerAndThrow()
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            triggerAndThrow()
        }

        private fun triggerAndThrow(): Nothing {
            if (!triggered) {
                triggered = true
                onTrigger()
            }
            throw java.io.IOException("forced read failure after cancellation")
        }
    }

    private class DeactivateOnEofInputStream(
        private val delegate: InputStream,
        private val onEof: () -> Unit
    ) : InputStream() {
        private var eofTriggered = false

        override fun read(): Int {
            val value = delegate.read()
            triggerEofIfNeeded(value)
            return value
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val bytesRead = delegate.read(b, off, len)
            triggerEofIfNeeded(bytesRead)
            return bytesRead
        }

        override fun close() {
            delegate.close()
        }

        private fun triggerEofIfNeeded(readResult: Int) {
            if (readResult == -1 && !eofTriggered) {
                eofTriggered = true
                onEof()
            }
        }
    }

    private class FakeHttpURLConnection(
        url: URL,
        private val responseCodeValue: Int,
        private val responseCodeProvider: (() -> Int)? = null,
        private val inputStreamFactory: () -> InputStream
    ) : HttpURLConnection(url) {
        @Volatile
        var disconnectCalled: Boolean = false
            private set

        override fun connect() = Unit

        override fun disconnect() {
            disconnectCalled = true
        }

        override fun usingProxy(): Boolean = false

        override fun getResponseCode(): Int = responseCodeProvider?.invoke() ?: responseCodeValue

        override fun getInputStream(): InputStream = inputStreamFactory()
    }

    companion object {
        private val connectionsByUrl = ConcurrentHashMap<String, FakeHttpURLConnection>()

        init {
            registerMockProtocolHandler()
        }

        private fun registerConnection(url: String, connection: FakeHttpURLConnection) {
            connectionsByUrl[url] = connection
        }

        private fun registerMockProtocolHandler() {
            try {
                URL.setURLStreamHandlerFactory { protocol ->
                    if (protocol == "mockhttp") {
                        object : URLStreamHandler() {
                            override fun openConnection(url: URL): URLConnection {
                                return connectionsByUrl[url.toString()]
                                    ?: error("No fake connection registered for $url")
                            }
                        }
                    } else {
                        null
                    }
                }
            } catch (_: Error) {
                // URLStreamHandlerFactory is a one-time JVM global.
                // If already set by another test, this suite assumes mockhttp is already supported.
            }
        }
    }
}

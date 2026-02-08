package com.marrakechguide.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.content.pm.PackageInfoCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ContentDatabaseTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `syncFromAssetsIfNeeded reseeds corrupted database when seeded version matches`() {
        val dbFile = testDatabaseFile("corrupt_content")
        dbFile.parentFile?.mkdirs()
        dbFile.writeText("not a sqlite file")

        val currentVersionCode = currentVersionCode()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(SEEDED_VERSION_KEY, currentVersionCode)
            .commit()

        invokeSyncFromAssetsIfNeeded(dbFile)

        assertTrue(
            "Expected sync to reseed a corrupted content DB",
            invokeIsUsableSQLiteDatabase(dbFile)
        )
        assertTrue(
            "Expected synced DB to have SQLite header",
            hasSQLiteHeader(dbFile)
        )
    }

    @Test
    fun `isUsableSQLiteDatabase rejects non sqlite files`() {
        val dbFile = testDatabaseFile("plain_text")
        dbFile.parentFile?.mkdirs()
        dbFile.writeText("plain text")

        assertFalse(invokeIsUsableSQLiteDatabase(dbFile))
    }

    @Test
    fun `isUsableSQLiteDatabase rejects sqlite files without places table`() {
        val dbFile = testDatabaseFile("sqlite_without_places")
        createSQLiteFile(dbFile, withPlacesTable = false)

        assertFalse(
            "A generic SQLite file should not be accepted as the content database",
            invokeIsUsableSQLiteDatabase(dbFile)
        )
    }

    @Test
    fun `isUsableSQLiteDatabase accepts sqlite files with places table`() {
        val dbFile = testDatabaseFile("sqlite_with_places")
        createSQLiteFile(dbFile, withPlacesTable = true)

        assertTrue(invokeIsUsableSQLiteDatabase(dbFile))
    }

    @Test
    fun `syncFromAssetsIfNeeded keeps existing usable database when reseed fails`() {
        val parentDir = File(context.cacheDir, "fallback_sync_${UUID.randomUUID()}").apply {
            mkdirs()
        }
        val dbFile = File(parentDir, "content.db")
        createSQLiteFile(dbFile, withPlacesTable = true)

        val staleSeededVersion = -42L
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(SEEDED_VERSION_KEY, staleSeededVersion)
            .commit()

        // Force reseed staging to fail deterministically.
        // copyDatabaseFromAssets always writes to "<db parent>/content.db.tmp";
        // making that path a directory guarantees FileOutputStream fails.
        File(parentDir, "content.db.tmp").mkdir()

        invokeSyncFromAssetsIfNeeded(dbFile)

        assertTrue(
            "Expected fallback to keep the previous usable DB when reseed fails",
            invokeIsUsableSQLiteDatabase(dbFile)
        )
        val seededVersionAfterFailure = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(SEEDED_VERSION_KEY, Long.MIN_VALUE)
        assertTrue(
            "Expected seeded version marker to remain unchanged when reseed fails",
            seededVersionAfterFailure == staleSeededVersion
        )
    }

    private fun currentVersionCode(): Long {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return PackageInfoCompat.getLongVersionCode(packageInfo)
    }

    private fun testDatabaseFile(prefix: String): File {
        return context.getDatabasePath("${prefix}_${UUID.randomUUID()}.db")
    }

    private fun createSQLiteFile(file: File, withPlacesTable: Boolean) {
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            if (withPlacesTable) {
                db.execSQL("CREATE TABLE places (id TEXT PRIMARY KEY)")
            } else {
                db.execSQL("CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT)")
            }
        }
    }

    private fun hasSQLiteHeader(file: File): Boolean {
        if (!file.exists() || file.length() < SQLITE_HEADER.size.toLong()) {
            return false
        }
        val bytes = ByteArray(SQLITE_HEADER.size)
        return try {
            file.inputStream().use { input ->
                var totalRead = 0
                while (totalRead < bytes.size) {
                    val read = input.read(bytes, totalRead, bytes.size - totalRead)
                    if (read <= 0) {
                        return false
                    }
                    totalRead += read
                }
            }
            bytes.contentEquals(SQLITE_HEADER)
        } catch (_: Exception) {
            false
        }
    }

    private fun invokeSyncFromAssetsIfNeeded(dbFile: File) {
        val method = ContentDatabase.Companion::class.java.getDeclaredMethod(
            "syncFromAssetsIfNeeded",
            Context::class.java,
            File::class.java
        )
        method.isAccessible = true
        method.invoke(ContentDatabase.Companion, context, dbFile)
    }

    private fun invokeIsUsableSQLiteDatabase(file: File): Boolean {
        val method = ContentDatabase.Companion::class.java.getDeclaredMethod(
            "isUsableSQLiteDatabase",
            File::class.java
        )
        method.isAccessible = true
        return method.invoke(ContentDatabase.Companion, file) as Boolean
    }

    companion object {
        private const val PREFS_NAME = "content_db_prefs"
        private const val SEEDED_VERSION_KEY = "content_db_seeded_version_code"
        private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    }
}

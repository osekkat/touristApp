package com.marrakechguide.core.database

import androidx.room.TypeConverter
import org.json.JSONArray
import org.json.JSONException

/**
 * Type converters for Room database.
 * Handles conversion between Kotlin types and SQLite-compatible types.
 * Uses Android's built-in org.json for JSON parsing.
 */
class Converters {
    // String List converters
    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return value?.let { JSONArray(it).toString() }
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        return value?.let {
            try {
                val array = JSONArray(it)
                (0 until array.length()).map { i -> array.getString(i) }
            } catch (e: JSONException) {
                null
            }
        }
    }

    // Int List converters (for source_refs)
    @TypeConverter
    fun fromIntList(value: List<Int>?): String? {
        return value?.let { JSONArray(it).toString() }
    }

    @TypeConverter
    fun toIntList(value: String?): List<Int>? {
        return value?.let {
            try {
                val array = JSONArray(it)
                (0 until array.length()).map { i -> array.getInt(i) }
            } catch (e: JSONException) {
                null
            }
        }
    }
}

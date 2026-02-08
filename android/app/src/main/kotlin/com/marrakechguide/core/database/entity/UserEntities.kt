package com.marrakechguide.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * UserSetting entity - key-value settings storage.
 */
@Entity(tableName = "user_settings")
data class UserSettingEntity(
    @PrimaryKey val key: String,
    val value: String?,  // JSON encoded
    @ColumnInfo(name = "updated_at") val updatedAt: String
)

/**
 * Favorite entity - saved favorite items.
 */
@Entity(
    tableName = "favorites",
    indices = [
        Index(value = ["content_type"]),
        Index(value = ["content_type", "content_id"], unique = true)
    ]
)
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "content_type") val contentType: String,
    @ColumnInfo(name = "content_id") val contentId: String,
    @ColumnInfo(name = "created_at") val createdAt: String
)

/**
 * Recent entity - recently viewed items.
 */
@Entity(
    tableName = "recents",
    indices = [Index(value = ["viewed_at"])]
)
data class RecentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "content_type") val contentType: String,
    @ColumnInfo(name = "content_id") val contentId: String,
    @ColumnInfo(name = "viewed_at") val viewedAt: String
)

/**
 * SavedPlan entity - saved My Day plans.
 */
@Entity(tableName = "saved_plans")
data class SavedPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    @ColumnInfo(name = "plan_date") val planDate: String? = null,
    @ColumnInfo(name = "plan_data") val planData: String,  // JSON encoded
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String
)

/**
 * RouteProgress entity - tracking progress on routes.
 */
@Entity(
    tableName = "route_progress",
    foreignKeys = [
        ForeignKey(
            entity = SavedPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["plan_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["plan_id"]),
        Index(value = ["plan_id", "step_index"], unique = true)
    ]
)
data class RouteProgressEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "plan_id") val planId: Long,
    @ColumnInfo(name = "step_index") val stepIndex: Int,
    @ColumnInfo(name = "completed_at") val completedAt: String? = null,
    val skipped: Boolean = false
)

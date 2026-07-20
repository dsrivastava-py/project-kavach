package com.kavach.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cached incident row for offline display.
 * Mirrored from the backend [incidents] table structure.
 */
@Entity(
    tableName = "incidents",
    indices = [Index(value = ["elder_id"]), Index(value = ["status"])],
)
data class IncidentEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "elder_id")
    val elderId: String,

    @ColumnInfo(name = "status")
    val status: String,           // open | graduated_1..4 | resolved | false_positive

    @ColumnInfo(name = "risk_score")
    val riskScore: Double,

    @ColumnInfo(name = "started_at")
    val startedAt: String,        // ISO-8601

    @ColumnInfo(name = "resolved_at")
    val resolvedAt: String? = null,

    @ColumnInfo(name = "resolution_note")
    val resolutionNote: String? = null,

    @ColumnInfo(name = "cached_at")
    val cachedAt: Long = System.currentTimeMillis(),
)

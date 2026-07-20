package com.kavach.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached billing plan for offline display.
 * Features are stored as a comma-separated string (Room does not support List<String>
 * natively without a TypeConverter — we use the converter in [KavachDatabase]).
 */
@Entity(tableName = "plans")
data class PlanEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "price_inr")
    val priceInr: Int,

    @ColumnInfo(name = "billing_cycle")
    val billingCycle: String? = null,

    /** Pipe-separated feature strings — split on read. */
    @ColumnInfo(name = "features")
    val features: String,

    @ColumnInfo(name = "cached_at")
    val cachedAt: Long = System.currentTimeMillis(),
)

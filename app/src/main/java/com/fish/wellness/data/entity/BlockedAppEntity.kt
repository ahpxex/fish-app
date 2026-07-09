package com.fish.wellness.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "blocked_apps",
    foreignKeys = [
        ForeignKey(
            entity = PolicyEntity::class,
            parentColumns = ["id"],
            childColumns = ["policyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("policyId"), Index(value = ["policyId", "packageName"], unique = true)]
)
data class BlockedAppEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val policyId: Long,
    val packageName: String,
    val appName: String,
    val dailyLimitMinutes: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isFullBlock: Boolean get() = dailyLimitMinutes <= 0
}

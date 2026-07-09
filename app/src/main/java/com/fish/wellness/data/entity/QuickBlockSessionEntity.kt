package com.fish.wellness.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quick_block_sessions")
data class QuickBlockSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startAt: Long,
    val endAt: Long,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

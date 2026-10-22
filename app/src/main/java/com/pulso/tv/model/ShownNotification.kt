package com.pulso.tv.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shown_notifications")
class ShownNotification(
    @PrimaryKey
    val channelId: String,
    val startedAt: Long,
)
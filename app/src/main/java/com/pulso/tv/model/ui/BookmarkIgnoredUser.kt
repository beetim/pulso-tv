package com.pulso.tv.model.ui

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vod_bookmark_ignored_users")
class BookmarkIgnoredUser(
    @PrimaryKey
    @ColumnInfo(name = "user_id")
    val userId: String,
)

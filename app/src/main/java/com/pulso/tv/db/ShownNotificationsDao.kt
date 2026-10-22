package com.pulso.tv.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pulso.tv.model.ShownNotification

@Dao
interface ShownNotificationsDao {

    @Query("SELECT * FROM shown_notifications")
    fun getAll(): List<ShownNotification>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertList(items: List<ShownNotification>)

    @Delete
    fun deleteList(items: List<ShownNotification>)
}
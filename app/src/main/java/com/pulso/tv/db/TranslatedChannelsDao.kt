package com.pulso.tv.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.pulso.tv.model.ui.TranslatedChannel

@Dao
interface TranslatedChannelsDao {

    @Query("SELECT * FROM translate_all_messages WHERE channelId = :id")
    fun getById(id: String): TranslatedChannel?

    @Insert
    fun insert(item: TranslatedChannel)

    @Delete
    fun delete(item: TranslatedChannel)
}
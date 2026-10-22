package com.pulso.tv.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.pulso.tv.model.ui.SavedFilter

@Dao
interface SavedFiltersDao {

    @Query("SELECT * FROM filters")
    fun getAll(): PagingSource<Int, SavedFilter>

    @Insert
    fun insert(item: SavedFilter)

    @Delete
    fun delete(item: SavedFilter)
}
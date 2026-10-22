package com.pulso.tv.repository

import com.pulso.tv.db.SavedFiltersDao
import com.pulso.tv.model.ui.SavedFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SavedFiltersRepository(
    private val savedFiltersDao: SavedFiltersDao,
) {

    fun getAll() = savedFiltersDao.getAll()

    suspend fun save(item: SavedFilter) = withContext(Dispatchers.IO) {
        savedFiltersDao.insert(item)
    }

    suspend fun delete(item: SavedFilter) = withContext(Dispatchers.IO) {
        savedFiltersDao.delete(item)
    }
}

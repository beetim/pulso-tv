package com.pulso.tv.repository

import com.pulso.tv.db.RecentSearchesDao
import com.pulso.tv.model.ui.RecentSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RecentSearchesRepository(
    private val recentSearchesDao: RecentSearchesDao,
) {

    fun getAll(type: String) = recentSearchesDao.getAll(type)

    suspend fun getItem(query: String, type: String) = withContext(Dispatchers.IO) {
        recentSearchesDao.getItem(query, type)
    }

    suspend fun save(item: RecentSearch) = withContext(Dispatchers.IO) {
        recentSearchesDao.ensureMaxSizeAndInsert(item)
    }

    suspend fun delete(item: RecentSearch) = withContext(Dispatchers.IO) {
        recentSearchesDao.delete(item)
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        recentSearchesDao.deleteAll()
    }
}
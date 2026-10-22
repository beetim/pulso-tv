package com.pulso.tv.repository

import com.pulso.tv.db.GameSortDao
import com.pulso.tv.model.ui.GameSort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GameSortRepository(
    private val gameSortDao: GameSortDao,
) {

    suspend fun getById(id: String) = withContext(Dispatchers.IO) {
        gameSortDao.getById(id)
    }

    suspend fun save(item: GameSort) = withContext(Dispatchers.IO) {
        gameSortDao.insert(item)
    }

    suspend fun delete(item: GameSort) = withContext(Dispatchers.IO) {
        gameSortDao.delete(item)
    }
}

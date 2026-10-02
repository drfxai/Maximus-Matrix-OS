package ai.drfx.maximus.matrixai.database.repository

import ai.drfx.maximus.matrixai.database.dao.UserSessionDataDao
import ai.drfx.maximus.matrixai.database.entities.UserSessionDataEntity
import kotlinx.coroutines.flow.Flow

class UserSessionRepository(private val dao: UserSessionDataDao) {
    suspend fun get(key: String): String? {
        return dao.getByKey(key)?.value
    }

    suspend fun set(key: String, value: String, category: String = "general") {
        dao.set(UserSessionDataEntity(key = key, value = value, category = category, updatedMs = System.currentTimeMillis()))
    }

    fun observeCategory(category: String): Flow<List<UserSessionDataEntity>> {
        return dao.observeByCategory(category)
    }

    suspend fun remove(key: String) {
        dao.delete(key)
    }
}

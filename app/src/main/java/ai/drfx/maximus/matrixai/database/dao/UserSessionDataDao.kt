package ai.drfx.maximus.matrixai.database.dao

import androidx.room.*
import ai.drfx.maximus.matrixai.database.entities.UserSessionDataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserSessionDataDao {
    @Query("SELECT * FROM user_session_data WHERE `key` = :key")
    suspend fun getByKey(key: String): UserSessionDataEntity?

    @Query("SELECT * FROM user_session_data WHERE category = :category ORDER BY updatedMs DESC")
    fun observeByCategory(category: String): Flow<List<UserSessionDataEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(data: UserSessionDataEntity): Long

    @Query("DELETE FROM user_session_data WHERE `key` = :key")
    suspend fun delete(key: String): Int
}

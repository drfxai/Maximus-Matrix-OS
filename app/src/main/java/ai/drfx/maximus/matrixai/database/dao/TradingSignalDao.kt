package ai.drfx.maximus.matrixai.database.dao

import androidx.room.*
import ai.drfx.maximus.matrixai.database.entities.TradingSignalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TradingSignalDao {
    @Query("SELECT * FROM live_trading_signals ORDER BY timestampMs DESC")
    fun observeAllSignals(): Flow<List<TradingSignalEntity>>

    @Query("SELECT * FROM live_trading_signals WHERE status = :status ORDER BY timestampMs DESC")
    fun observeSignalsByStatus(status: String): Flow<List<TradingSignalEntity>>

    @Query("SELECT * FROM live_trading_signals WHERE assetClass = :assetClass ORDER BY timestampMs DESC")
    fun observeSignalsByAssetClass(assetClass: String): Flow<List<TradingSignalEntity>>

    @Query("SELECT * FROM live_trading_signals WHERE isBookmarked = 1 ORDER BY timestampMs DESC")
    fun observeBookmarkedSignals(): Flow<List<TradingSignalEntity>>

    @Query("SELECT * FROM live_trading_signals WHERE id = :id")
    suspend fun getSignalById(id: String): TradingSignalEntity?

    @Query("SELECT * FROM live_trading_signals WHERE id = :id")
    fun observeSignalById(id: String): Flow<TradingSignalEntity?>

    @Query("SELECT COUNT(*) FROM live_trading_signals")
    suspend fun getSignalCount(): Int

    @Query("SELECT COUNT(*) FROM live_trading_signals WHERE status = 'ACTIVE'")
    fun observeActiveSignalCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignal(signal: TradingSignalEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignals(signals: List<TradingSignalEntity>): List<Long>

    @Update
    suspend fun updateSignal(signal: TradingSignalEntity): Int

    @Query("UPDATE live_trading_signals SET isBookmarked = :bookmarked WHERE id = :id")
    suspend fun updateBookmark(id: String, bookmarked: Boolean): Int

    @Query("UPDATE live_trading_signals SET currentPrice = :price, status = :status WHERE id = :id")
    suspend fun updatePriceAndStatus(id: String, price: Double, status: String): Int

    @Delete
    suspend fun deleteSignal(signal: TradingSignalEntity): Int

    @Query("DELETE FROM live_trading_signals WHERE id = :id")
    suspend fun deleteSignalById(id: String): Int

    @Query("DELETE FROM live_trading_signals")
    suspend fun clearAllSignals(): Int
}

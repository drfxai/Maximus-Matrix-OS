package ai.drfx.maximus.matrixai.database.di

import android.content.Context
import ai.drfx.maximus.matrixai.database.AppDatabase
import ai.drfx.maximus.matrixai.database.dao.ChatDao
import ai.drfx.maximus.matrixai.database.dao.MatrixEventDao
import ai.drfx.maximus.matrixai.database.dao.MatrixGraphDao
import ai.drfx.maximus.matrixai.database.dao.UserSessionDataDao
import ai.drfx.maximus.matrixai.database.repository.ChatHistoryRepository
import ai.drfx.maximus.matrixai.database.repository.MatrixEventRepository
import ai.drfx.maximus.matrixai.database.repository.MatrixGraphRepository
import ai.drfx.maximus.matrixai.database.repository.UserSessionRepository

/**
 * Dependency Injection Module for Database and Repository instances.
 * Provides thread-safe singleton access for constructor injection across ViewModels.
 */
class DatabaseModule private constructor(context: Context) {
    val database: AppDatabase = AppDatabase.getInstance(context)

    val matrixGraphDao: MatrixGraphDao by lazy { database.matrixGraphDao() }
    val chatDao: ChatDao by lazy { database.chatDao() }
    val matrixEventDao: MatrixEventDao by lazy { database.matrixEventDao() }
    val userSessionDataDao: UserSessionDataDao by lazy { database.userSessionDataDao() }

    val matrixGraphRepository: MatrixGraphRepository by lazy {
        MatrixGraphRepository(matrixGraphDao)
    }

    val chatHistoryRepository: ChatHistoryRepository by lazy {
        ChatHistoryRepository(chatDao)
    }

    val matrixEventRepository: MatrixEventRepository by lazy {
        MatrixEventRepository(matrixEventDao)
    }

    val userSessionRepository: UserSessionRepository by lazy {
        UserSessionRepository(userSessionDataDao)
    }

    companion object {
        @Volatile
        private var INSTANCE: DatabaseModule? = null

        fun getInstance(context: Context): DatabaseModule {
            return INSTANCE ?: synchronized(this) {
                val instance = DatabaseModule(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}

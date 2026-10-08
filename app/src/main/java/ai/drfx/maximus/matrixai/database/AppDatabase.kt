package ai.drfx.maximus.matrixai.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import ai.drfx.maximus.matrixai.database.dao.ChatDao
import ai.drfx.maximus.matrixai.database.dao.MatrixEventDao
import ai.drfx.maximus.matrixai.database.dao.MatrixGraphDao
import ai.drfx.maximus.matrixai.database.dao.TradingSignalDao
import ai.drfx.maximus.matrixai.database.dao.UserSessionDataDao
import ai.drfx.maximus.matrixai.database.entities.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        MatrixNodeEntity::class,
        MatrixEdgeEntity::class,
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        MatrixEventEntity::class,
        UserSessionDataEntity::class,
        TradingSignalEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun matrixGraphDao(): MatrixGraphDao
    abstract fun chatDao(): ChatDao
    abstract fun matrixEventDao(): MatrixEventDao
    abstract fun userSessionDataDao(): UserSessionDataDao
    abstract fun tradingSignalDao(): TradingSignalDao

    companion object {
        const val DATABASE_NAME = "maximus_matrix.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Returns the thread-safe singleton instance of [AppDatabase]
         * configured for dependency injection.
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                .addCallback(DatabaseCallback())
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        seedInitialData(database)
                    }
                }
            }

            private suspend fun seedInitialData(database: AppDatabase) {
                // Seed initial Matrix Graph Hubs
                val initialNodes = listOf(
                    MatrixNodeEntity(
                        id = "maximus",
                        label = "MAXIMUS",
                        groupName = "MATRIX CORE",
                        x = 0f,
                        y = 0f,
                        radius = 16f,
                        colorHex = "#38C79B",
                        description = "Central operating graph for MAXIMUS AI and internal Matrix OS engines.",
                        relations = "Mission Control · Agent Factory · Models · Knowledge · Research · Validation",
                        isHub = true
                    ),
                    MatrixNodeEntity(
                        id = "models",
                        label = "AI Gateway",
                        groupName = "MODEL ROUTING",
                        x = 210f,
                        y = -40f,
                        radius = 16f,
                        colorHex = "#6ABCC9",
                        description = "Multi-provider AI gateway connecting OpenAI, Anthropic, Gemini, and NVIDIA NIM.",
                        relations = "Models · Compatible Agents · Usage",
                        isHub = true
                    ),
                    MatrixNodeEntity(
                        id = "agents",
                        label = "Agent Factory",
                        groupName = "AGENT RUNTIME",
                        x = 260f,
                        y = 150f,
                        radius = 16f,
                        colorHex = "#38C79B",
                        description = "Registry of capability-gated MAXIMUS agents.",
                        relations = "Executive · Research · Trading · Pine · Code · Validation · Automation · Quant",
                        isHub = true
                    ),
                    MatrixNodeEntity(
                        id = "mission",
                        label = "Mission Control",
                        groupName = "EXECUTION",
                        x = 70f,
                        y = 230f,
                        radius = 16f,
                        colorHex = "#9B72DA",
                        description = "Plans missions, applies policy, invokes tools and records execution events.",
                        relations = "Planner · Policy · Tools · Validation · Artifacts",
                        isHub = true
                    ),
                    MatrixNodeEntity(
                        id = "company",
                        label = "Company Data",
                        groupName = "COMPANY KNOWLEDGE",
                        x = -130f,
                        y = 245f,
                        radius = 16f,
                        colorHex = "#6ABCC9",
                        description = "Company knowledge base and data center integration.",
                        relations = "Documents · Projects · Pine Sources · Research",
                        isHub = true
                    )
                )
                database.matrixGraphDao().insertNodes(initialNodes)

                // Seed default Chat Session
                database.chatDao().insertSession(
                    ChatSessionEntity(
                        id = "default_session",
                        title = "Primary Matrix Chat",
                        provider = "OPENAI",
                        model = "gpt-4o",
                        agentId = "executive"
                    )
                )
            }
        }
    }
}


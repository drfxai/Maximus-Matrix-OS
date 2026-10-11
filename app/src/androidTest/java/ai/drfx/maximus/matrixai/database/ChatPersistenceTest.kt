package ai.drfx.maximus.matrixai.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ai.drfx.maximus.matrixai.database.entities.ChatMessageEntity
import ai.drfx.maximus.matrixai.database.entities.ChatSessionEntity
import ai.drfx.maximus.matrixai.database.repository.ChatHistoryRepository
import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatPersistenceTest {
    @Test fun versionOneUpgradePreservesHistoryAndCreatesSignalTable() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-chat-test.db"
        context.deleteDatabase(name)
        var db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        val session = ChatSessionEntity("session", "Keep me", "GEMINI", "verified-model", "general")
        db.chatDao().insertSession(session)
        db.chatDao().insertMessage(ChatMessageEntity(sessionId = "session", role = "user", content = "preserved"))
        db.openHelper.writableDatabase.execSQL("DROP TABLE live_trading_signals")
        db.openHelper.writableDatabase.execSQL("PRAGMA user_version = 1")
        db.close()
        db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(AppDatabase.MIGRATION_1_2).build()
        try {
            assertEquals("preserved", db.chatDao().getMessages("session").single().content)
            assertEquals(session, db.chatDao().getSession("session"))
            db.openHelper.writableDatabase.query("SELECT * FROM live_trading_signals").close()
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun encryptedBodiesRoundTripStableOrderingAndDelete() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repository = ChatHistoryRepository(db.chatDao(), ChatContentCipher())
            repository.saveSession(ChatSessionEntity("a", "A", "GEMINI", "model", "general"))
            repository.saveMessage(ChatMessageEntity(sessionId = "a", role = "user", content = "private", timestampMs = 1))
            repository.saveMessage(ChatMessageEntity(sessionId = "a", role = "assistant", content = "reply", timestampMs = 1))
            assertFalse(db.chatDao().getMessages("a").first().content.contains("private"))
            assertEquals(listOf("private", "reply"), repository.getMessages("a").map { it.content })
            assertEquals(1, repository.updateMessageContent("a", 1, "user", "persisted transcript"))
            assertEquals("persisted transcript", repository.getMessages("a").first().content)
            assertFalse(db.chatDao().getMessages("a").first().content.contains("persisted transcript"))
            assertEquals(0, repository.updateMessageContent("other", 1, "user", "do not touch"))
            assertEquals("reply", repository.getMessages("a").last().content)
            repository.renameSession("a", "Renamed")
            assertEquals("Renamed", repository.getSession("a")?.title)
            repository.deleteSession("a")
            assertNull(repository.getSession("a"))
            assertTrue(repository.getMessages("a").isEmpty())
        } finally { db.close() }
    }
}

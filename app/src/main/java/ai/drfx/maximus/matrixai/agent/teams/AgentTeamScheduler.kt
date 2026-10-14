package ai.drfx.maximus.matrixai.agent.teams

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ai.drfx.maximus.matrixai.capabilities.AndroidCapabilities
import ai.drfx.maximus.matrixai.news.AiNewsIntelligenceAgent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * WorkManager is best-effort under Doze, thermal and user battery policies.
 * It CANNOT guarantee continuous 24/7 operation or run arbitrary generated code.
 */
internal object AgentTeamScheduler {
    private fun periodicName(id: String) = "maximus.team.periodic.$id"
    private fun manualName(id: String) = "maximus.team.manual.$id"

    fun schedule(context: Context, team: AgentTeamSpec) {
        val manager = WorkManager.getInstance(context.applicationContext)
        if (!team.enabled) {
            manager.cancelUniqueWork(periodicName(team.id))
            manager.cancelUniqueWork(manualName(team.id))
            return
        }
        val conditions = Constraints.Builder().apply {
            if (team.job == AgentTeamJob.VERIFIED_NEWS)
                setRequiredNetworkType(NetworkType.CONNECTED)
        }.build()
        val periodic = PeriodicWorkRequestBuilder<AgentTeamWorker>(
            team.periodMinutes, TimeUnit.MINUTES
        ).setConstraints(conditions)
            .setInputData(Data.Builder().putString(AgentTeamWorker.KEY_ID, team.id).build())
            .addTag("maximus.team")
            .build()
        manager.enqueueUniquePeriodicWork(
            periodicName(team.id), ExistingPeriodicWorkPolicy.UPDATE, periodic
        )
    }

    fun runNow(context: Context, team: AgentTeamSpec) {
        require(team.enabled)
        val conditions = Constraints.Builder().apply {
            if (team.job == AgentTeamJob.VERIFIED_NEWS)
                setRequiredNetworkType(NetworkType.CONNECTED)
        }.build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            manualName(team.id), ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<AgentTeamWorker>()
                .setInputData(Data.Builder().putString(AgentTeamWorker.KEY_ID, team.id).build())
                .setConstraints(conditions)
                .addTag("maximus.team")
                .build()
        )
    }

    fun cancel(context: Context, id: String) {
        WorkManager.getInstance(context.applicationContext).apply {
            cancelUniqueWork(periodicName(id))
            cancelUniqueWork(manualName(id))
        }
    }
}

internal class AgentTeamWorker(
    context: Context, params: WorkerParameters
) : CoroutineWorker(context, params) {
    companion object {
        const val KEY_ID = "team_id"
        private val locks = ConcurrentHashMap<String, Mutex>()
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString(KEY_ID).orEmpty()
        val store = AgentTeamStore(applicationContext)
        val team = store.find(id) ?: return@withContext Result.success()
        if (!team.enabled) return@withContext Result.success()
        val lock = locks.getOrPut(id) { Mutex() }
        lock.withLock {
            val current = store.find(id)
            if (current?.enabled != true) return@withLock Result.success()
            try {
                // Stage 1: Collector fetches REAL evidence, not synthetic model output.
                // Stage 2: Validator checks source availability before producing a report.
                // Stage 3: Reporter persists a bounded user-viewable result.
                val outcome: Pair<String, Int> = when (current.job) {
                    AgentTeamJob.DEVICE_HEALTH -> {
                        val evidence = AndroidCapabilities(applicationContext).deviceInfo()
                        if (!evidence.success) error("Device info not available.")
                        "Device health: " + evidence.output to 1
                    }
                    AgentTeamJob.VERIFIED_NEWS -> {
                        val feed = AiNewsIntelligenceAgent(applicationContext)
                        val (_, articles) = feed.refreshWithAi()
                        val verified = articles.filter {
                            it.sourceUrl.startsWith("https://", ignoreCase = true) &&
                                it.sourceName.isNotBlank() &&
                                it.headline.isNotBlank()
                        }.distinctBy { it.sourceUrl }
                        val summary = if (verified.isEmpty())
                            "No verified publisher reports are available; no news was fabricated."
                        else "Publisher sources: " +
                            verified.map { it.sourceName }.distinct().take(4).joinToString(", ") +
                            "\n" + verified.take(4).joinToString("\n") { "• " + it.headline }
                        summary to verified.size
                    }
                }
                store.record(id, AgentTeamReport(
                    atEpochMs = System.currentTimeMillis(),
                    status = if (outcome.second > 0) "VERIFIED" else "UNAVAILABLE",
                    summary = outcome.first,
                    evidenceCount = outcome.second
                ))
                Result.success()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                store.record(id, AgentTeamReport(
                    atEpochMs = System.currentTimeMillis(), status = "UNAVAILABLE",
                    summary = (e.message ?: "Background task unavailable.").take(350)
                ))
                // Allow the next explicitly scheduled scan. No tight retry loops.
                Result.success()
            }
        }
    }
}

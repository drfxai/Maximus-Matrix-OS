package ai.drfx.maximus.matrixai.studio.phonehost

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import ai.drfx.maximus.matrixai.MainActivity
import ai.drfx.maximus.matrixai.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Keep a user-approved real Android Gradle job observable while the screen is backgrounded. */
class PhoneLocalBuildService : Service() {
    companion object {
        const val BUILD = "ai.drfx.maximus.matrixai.phonehost.BUILD"
        const val CANCEL = "ai.drfx.maximus.matrixai.phonehost.CANCEL_BUILD"
        const val PROJECT_ID = "project_uuid"
        const val JOB_ID = "approved_job_uuid"
        private const val CHANNEL = "maximus_phone_build"
        private const val NOTIFICATION = 3733
        private val cleanup = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val reserved = java.util.concurrent.atomic.AtomicBoolean(false)
    private val cancelRequested = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var ownedJobId: String? = null
    @Volatile private var ownedOperationId: String? = null
    @Volatile private var ownedJobs: PhoneBuildJobs? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Building APK on this phone", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val builder = PhoneBuildSession.forContext(applicationContext)
        if (intent?.action == CANCEL) {
            cancelRequested.set(true)
            if (reserved.get()) cleanup.launch {
                val id = ownedJobId
                if (id != null) ownedJobs?.cancel(id) else ownedOperationId?.let { builder.cancelOwned(it) }
            } else stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != BUILD) {
            if (!reserved.get()) stopSelf()
            return START_NOT_STICKY
        }
        val requestedJob = intent.getStringExtra(JOB_ID)
        val directProject = intent.getStringExtra(PROJECT_ID).orEmpty()
        if (!reserved.compareAndSet(false, true)) {
            if (requestedJob != null && requestedJob != ownedJobId) cleanup.launch {
                PhoneHostSession.active?.jobs?.cancel(requestedJob)
            }
            return START_NOT_STICKY
        }
        ownedJobId = requestedJob
        ownedOperationId = requestedJob ?: java.util.UUID.randomUUID().toString()
        cancelRequested.set(false)
        startForeground(NOTIFICATION, notification("Preparing Gradle Debug APK build…"))
        // Registry capture/hash/artifact locks may perform bounded bulk IO. Never
        // wait for them in onStartCommand or other Main lifecycle callbacks.
        scope.launch(Dispatchers.IO) {
            try {
                check(!cancelRequested.get())
                require(!builder.state.value.busy)
                if (requestedJob != null) {
                    val registry = requireNotNull(PhoneHostSession.active?.jobs)
                    ownedJobs = registry
                    registry.start(requestedJob) { claim ->
                        check(!cancelRequested.get())
                        val guarded = claim.copy(isValid = { claim.isValid() && !cancelRequested.get() })
                        builder.buildApproved(guarded)
                    }
                } else {
                    require(directProject.matches(PhoneBuildJobs.UUID_PATTERN))
                    builder.buildForService(directProject, requireNotNull(ownedOperationId)) { !cancelRequested.get() }
                }
                builder.state.collect { state ->
                    if (state.ownerJobId != ownedOperationId) return@collect
                    ownedJobId?.let { ownedJobs?.observe(it, state) }
                    withContext(Dispatchers.Main.immediate) {
                        getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(state.message))
                        if (!state.busy && state.phase in setOf("succeeded", "failed", "cancelled", "unavailable")) {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                    }
                }
            } catch (_: Exception) {
                ownedJobId?.let { ownedJobs?.cancel(it) }
                withContext(Dispatchers.Main.immediate) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun notification(message: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val cancel = PendingIntent.getService(
            this, 1, Intent(this, PhoneLocalBuildService::class.java).apply { action = CANCEL },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Maximus · On-phone APK build")
            .setContentText(message.take(105))
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel build", cancel)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        cancelRequested.set(true)
        val builder = PhoneBuildSession.forContext(applicationContext)
        val id = ownedJobId
        val registry = ownedJobs
        val operation = ownedOperationId
        if (reserved.get()) cleanup.launch {
            if (id != null) registry?.cancel(id) else operation?.let { builder.cancelOwned(it) }
        }
        scope.cancel()
        super.onDestroy()
    }
}

package ai.drfx.maximus.matrixai.studio.phonehost

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import ai.drfx.maximus.matrixai.MainActivity
import ai.drfx.maximus.matrixai.R

/** Process-local state; the one-time access token is never persisted or logged. */
internal object PhoneHostSession {
    @Volatile var active: PhoneHarnessHttpServer? = null
        private set
    @Volatile var lastError: String = ""
        private set

    private val lifecycle = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "phone-host-lifecycle").apply { isDaemon = true }
    }
    private val lifecycleLock = Any()
    private var generation = 0L
    private var starting = false

    fun start(context: Context, onFailure: () -> Unit = {}) {
        val epoch = synchronized(lifecycleLock) {
            if (active != null || starting) return
            starting = true
            generation
        }
        lifecycle.execute {
            try {
                if (synchronized(lifecycleLock) { generation != epoch }) return@execute
                java.io.File(context.cacheDir, "phone-server-jobs").deleteRecursively()
                val created = PhoneHarnessHttpServer(context)
                val accepted = synchronized(lifecycleLock) {
                    if (generation == epoch && starting) {
                        active = created; starting = false; lastError = ""; true
                    } else false
                }
                if (!accepted) created.close()
            } catch (_: Exception) {
                val current = synchronized(lifecycleLock) {
                    if (generation == epoch) {
                        starting = false
                        lastError = "Cannot bind phone host to 127.0.0.1:18767."
                        true
                    } else false
                }
                if (current) onFailure()
            }
        }
    }

    fun stop() {
        val detached = synchronized(lifecycleLock) {
            generation++; starting = false
            active.also { active = null }
        }
        // Stop admitting work/valid approvals now; bulk teardown never blocks Main.
        detached?.invalidate()
        lifecycle.execute { detached?.close() }
    }

    fun token(): String = active?.bearerToken.orEmpty()
    fun sharedProject(): String? = active?.approvedProject
    fun setSharedProject(id: String?) {
        val server = active ?: error("Start phone host before approving a project.")
        server.approveProject(id)
    }
}

/**
 * Foreground service only while a user has explicitly enabled the local
 * development gateway. Restart is opt-in: no background autostart on boot.
 */
class PhoneHarnessService : Service() {
    companion object {
        const val ACTION_START = "ai.drfx.maximus.matrixai.phonehost.START"
        const val ACTION_STOP = "ai.drfx.maximus.matrixai.phonehost.STOP"
        private const val CHANNEL = "maximus_phone_harness"
        private const val NOTIFICATION = 3701
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL, "Phone Development Server", NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            PhoneHostSession.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION, notification())
        try {
            PhoneHostSession.start(applicationContext) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        } catch (_: Exception) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, PhoneHarnessService::class.java).apply {
                action = ACTION_STOP
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Maximus Phone Host")
            .setContentText("Local-only workspace server running · 127.0.0.1")
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop server", stop)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        PhoneHostSession.stop()
        super.onDestroy()
    }
}

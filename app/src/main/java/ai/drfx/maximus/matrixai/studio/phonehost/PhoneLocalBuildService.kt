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

/** Keep a user-approved real Android Gradle job observable while the screen is backgrounded. */
class PhoneLocalBuildService : Service() {
    companion object {
        const val BUILD = "ai.drfx.maximus.matrixai.phonehost.BUILD"
        const val CANCEL = "ai.drfx.maximus.matrixai.phonehost.CANCEL_BUILD"
        const val PROJECT_ID = "project_uuid"
        private const val CHANNEL = "maximus_phone_build"
        private const val NOTIFICATION = 3733
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observing = false

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
            builder.cancel()
            return START_NOT_STICKY
        }
        if (intent?.action != BUILD) {
            stopSelf()
            return START_NOT_STICKY
        }
        val project = intent.getStringExtra(PROJECT_ID).orEmpty()
        if (!project.matches(Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))) {
            stopSelf()
            return START_NOT_STICKY
        }
        // A duplicate start must not stop/cancel a build already owned by this service.
        if (builder.state.value.busy) {
            if (!observing) stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION, notification("Preparing Gradle Debug APK build…"))
        // Accept the new job before subscribing: a restored previous success is not
        // the terminal event of this start request (Main.immediate emits synchronously).
        if (runCatching { builder.build(project) }.isFailure) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!observing) {
            observing = true
            scope.launch {
                builder.state.collect { state ->
                    getSystemService(NotificationManager::class.java).notify(
                        NOTIFICATION, notification(state.message)
                    )
                    if (!state.busy && state.phase in setOf(
                        "succeeded", "failed", "cancelled", "unavailable")) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
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
        val builder = PhoneBuildSession.forContext(applicationContext)
        if (observing && builder.state.value.busy) builder.cancel()
        scope.cancel()
        super.onDestroy()
    }
}

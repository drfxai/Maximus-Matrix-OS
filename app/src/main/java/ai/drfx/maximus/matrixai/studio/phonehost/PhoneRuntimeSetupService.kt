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

/** Foreground only when the user explicitly installs the large on-phone toolchain. */
class PhoneRuntimeSetupService : Service() {
    companion object {
        const val START = "ai.drfx.maximus.matrixai.phonehost.INSTALL"
        const val CANCEL = "ai.drfx.maximus.matrixai.phonehost.CANCEL_INSTALL"
        private const val CHANNEL = "maximus_phone_runtime"
        private const val NOTIFICATION = 3722
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL, "On-device Android build tools", NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val installer = PhoneRuntimeInstallSession.forContext(applicationContext)
        if (intent?.action == CANCEL) {
            installer.cancel()
            return START_NOT_STICKY
        }
        if (intent?.action != START) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION, notice("Preparing private Linux and Android toolchain", 0))
        if (!observing) {
            observing = true
            scope.launch {
                installer.state.collect { progress ->
                    val current = progress.fraction.coerceIn(0f, 1f)
                    getSystemService(NotificationManager::class.java).notify(
                        NOTIFICATION, notice(progress.message, (current * 100).toInt())
                    )
                    if (!progress.busy && progress.phase in setOf(
                            "installed", "failed", "cancelled")) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
        installer.install()
        return START_NOT_STICKY
    }

    private fun notice(message: String, percent: Int): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancel = PendingIntent.getService(
            this, 1, Intent(this, PhoneRuntimeSetupService::class.java).apply {
                action = CANCEL
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Maximus · Android build tools")
            .setContentText(message.take(110))
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent, percent <= 0)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel setup", cancel)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        val installer = PhoneRuntimeInstallSession.forContext(applicationContext)
        if (installer.state.value.busy) installer.cancel()
        scope.cancel()
        super.onDestroy()
    }
}

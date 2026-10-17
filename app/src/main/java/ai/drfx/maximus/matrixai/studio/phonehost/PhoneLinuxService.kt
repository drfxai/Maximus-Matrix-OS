package ai.drfx.maximus.matrixai.studio.phonehost

import android.app.*
import android.content.Intent
import android.os.IBinder
import ai.drfx.maximus.matrixai.MainActivity
import ai.drfx.maximus.matrixai.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/** Started only by the local confirmation dialog, never through the loopback API. */
class PhoneLinuxService : Service() {
    companion object {
        const val RUN = "maximus.linux.RUN"
        const val CANCEL = "maximus.linux.CANCEL"
        const val COMMAND = "command"
        const val ID = "session_id"
        private const val CHANNEL = "maximus_linux"
        private const val NOTICE = 3734
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var owned: String? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.linux_title), NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val session = PhoneLinuxSessions.get(applicationContext)
        if (intent?.action == CANCEL) {
            intent.getStringExtra(ID)?.takeIf { it == owned }?.let(session::cancel)
            if (owned == null) stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != RUN || owned != null) {
            if (owned == null) stopSelf()
            return START_NOT_STICKY
        }
        owned = intent.getStringExtra(ID)
        startForeground(NOTICE, notification())
        try {
            val id = requireNotNull(intent.getStringExtra(ID))
            val command = requireNotNull(intent.getStringExtra(COMMAND))
            session.start(id, command, approved = true)
            owned = id
            scope.launch {
                session.state.collect { state ->
                    if (state.id == id && !state.busy) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        } catch (_: Exception) {
            owned?.let(session::reportStartFailure)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }
    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 0, Intent(this, PhoneLinuxService::class.java).apply {
            action = CANCEL; putExtra(ID, owned)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL).setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.linux_title)).setContentText(getString(R.string.linux_running))
            .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.linux_stop), stop).build()
    }
    override fun onDestroy() {
        owned?.let { PhoneLinuxSessions.get(applicationContext).cancel(it) }
        scope.cancel()
        super.onDestroy()
    }
}

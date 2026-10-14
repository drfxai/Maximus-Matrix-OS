package ai.drfx.maximus.matrixai.capabilities

import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import android.os.BatteryManager
import android.os.Build
import ai.drfx.maximus.matrixai.agent.ToolOutcome

class AndroidCapabilities(private val context: Context) {
    fun deviceInfo(): ToolOutcome {
        val battery = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return ToolOutcome(
            success = true,
            output = "Device: ${Build.MANUFACTURER} ${Build.MODEL}; Android ${Build.VERSION.RELEASE}; API ${Build.VERSION.SDK_INT}; Battery $level%.",
            evidence = mapOf(
                "manufacturer" to Build.MANUFACTURER,
                "model" to Build.MODEL,
                "api" to Build.VERSION.SDK_INT.toString(),
                "battery" to level.toString()
            )
        )
    }

    fun openUrl(url: String): ToolOutcome =
        launch(Intent(Intent.ACTION_VIEW, Uri.parse(normalizeUrl(url))), "Opened URL")

    fun webSearch(query: String): ToolOutcome {
        if (query.isBlank()) return ToolOutcome(false, "Search query is empty.")
        val url = "https://www.google.com/search?q=" + Uri.encode(query.take(350))
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            // Prefer the user's requested Chrome app, while falling back to
            // the chosen browser if Chrome is not installed.
            setPackage("com.android.chrome")
        }
        val result = launch(intent, "Opened Google search in Chrome")
        return if (result.success) result else
            launch(Intent(Intent.ACTION_VIEW, Uri.parse(url)),
                "Opened Google search in the device browser")
    }

    fun openSettings(section: String): ToolOutcome {
        val action = when (section.lowercase()) {
            "wifi" -> if (Build.VERSION.SDK_INT >= 29)
                Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        return launch(Intent(action), "Opened Android settings: ${section.ifBlank { "main" }}")
    }

    fun openCamera(): ToolOutcome =
        // Opening the device's camera app requires the user to press its shutter;
        // no hidden capture or ambient camera access is performed.
        launch(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            "Opened the camera app; take a photo using the camera controls.")

    fun setAlarm(hour: Int, minute: Int, label: String): ToolOutcome {
        val safeHour = hour.coerceIn(0, 23)
        val safeMinute = minute.coerceIn(0, 59)
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, safeHour)
            .putExtra(AlarmClock.EXTRA_MINUTES, safeMinute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label)
        return launch(intent, "Opened alarm editor for %02d:%02d".format(safeHour, safeMinute))
    }

    fun createCalendarEvent(title: String): ToolOutcome {
        if (title.isBlank()) return ToolOutcome(false, "Calendar event title is empty.")
        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, title)
        return launch(intent, "Opened calendar event editor")
    }

    fun openDialer(number: String): ToolOutcome {
        if (number.isBlank()) return ToolOutcome(false, "Phone number is empty.")
        return launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}")), "Opened dialer")
    }

    fun composeSms(number: String, message: String): ToolOutcome {
        if (number.isBlank()) return ToolOutcome(false, "SMS recipient is empty.")
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}"))
            .putExtra("sms_body", message)
        return launch(intent, "Opened SMS composer")
    }

    fun shareText(text: String): ToolOutcome {
        if (text.isBlank()) return ToolOutcome(false, "Share text is empty.")
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        return launch(Intent.createChooser(send, "Share with"), "Opened Android share sheet")
    }

    fun copyToClipboard(text: String): ToolOutcome {
        if (text.isBlank()) return ToolOutcome(false, "Clipboard text is empty.")
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("MAXIMUS AI", text))
        return ToolOutcome(true, "Copied text to clipboard", mapOf("characters" to text.length.toString()))
    }

    fun launchPackage(packageName: String): ToolOutcome {
        if (packageName.isBlank()) return ToolOutcome(false, "Package name is empty.")
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return ToolOutcome(false, "Application is not installed or is not visible to this app.")
        return launch(intent, "Opened application: $packageName")
    }

    private fun launch(intent: Intent, success: String): ToolOutcome = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        ToolOutcome(true, success, mapOf("androidIntent" to (intent.action ?: "unknown")))
    } catch (error: Throwable) {
        ToolOutcome(false, error.message ?: "Android action failed")
    }

    private fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return "https://www.google.com"
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
    }
}

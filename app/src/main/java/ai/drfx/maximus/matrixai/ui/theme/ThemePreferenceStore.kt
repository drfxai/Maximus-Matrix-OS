package ai.drfx.maximus.matrixai.ui.theme

import android.content.Context

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

class ThemePreferenceStore(context: Context) {
    private val prefs = context.getSharedPreferences("maximus_theme", Context.MODE_PRIVATE)

    fun load(): AppThemeMode = runCatching {
        AppThemeMode.valueOf(prefs.getString("mode", AppThemeMode.DARK.name).orEmpty())
    }.getOrDefault(AppThemeMode.DARK)

    fun save(mode: AppThemeMode) {
        prefs.edit().putString("mode", mode.name).apply()
    }
}

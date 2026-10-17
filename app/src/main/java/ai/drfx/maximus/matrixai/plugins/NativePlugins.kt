package ai.drfx.maximus.matrixai.plugins

import android.content.Context
import java.io.File

internal object NativePlugins {
    fun registry(context: Context) = PluginRegistry(File(context.applicationContext.filesDir, "plugins/native-v1.json"))
}

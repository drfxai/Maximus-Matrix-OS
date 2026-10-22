package ai.drfx.maximus.matrixai.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.UiDevice
import ai.drfx.maximus.matrixai.MainActivity
import ai.drfx.maximus.matrixai.R
import ai.drfx.maximus.matrixai.ui.theme.AppLanguage
import ai.drfx.maximus.matrixai.ui.theme.LanguagePreferenceStore
import ai.drfx.maximus.matrixai.ui.theme.MaximusMatrixTheme
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith

/** Actual panel interaction without API requests, usable credentials or production UI hooks. */
@RunWith(AndroidJUnit4::class)
class DeveloperConnectionsUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    @Test fun offlineRepositoryEditingAndCredentialStateRemainSafe() {
        val preferences = context.getSharedPreferences("maximus_developer_connections", Context.MODE_PRIVATE)
        val originalPreferences = preferences.all.toMap()
        val languages = LanguagePreferenceStore(context)
        val originalLanguage = languages.load()
        val originalLocale = Locale.getDefault()
        try {
            assertTrue(preferences.edit().clear().commit())
            languages.save(AppLanguage.ENGLISH)
            device.executeShellCommand("am start -W -f 0x10008000 -n ${context.packageName}/${MainActivity::class.java.name}")
            onActivity { activity ->
                // Isolate the real panel from unrelated continuously animated screens.
                // Test tags identify real panel controls without production hooks.
                activity.setContent {
                    MaximusMatrixTheme {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            DeveloperConnectionsPanel()
                        }
                    }
                }
            }
            status(R.string.dev_not_connected).assertIsDisplayed()
            button(R.string.dev_verify).assertIsNotEnabled()
            button(R.string.dev_save_token).assertIsNotEnabled()

            // Exercise the real Compose field callback and parser, including each
            // incomplete URL stage, with the same clock used by layout assertions.
            for (partial in listOf("h", "https://", "https://github.com/", "https://github.com/[")) {
                field("developer_repository_url").performTextReplacement(partial)
                compose.onNodeWithText(text(R.string.dev_repository_invalid))
                    .performScrollTo().assertIsDisplayed()
            }
            field("developer_repository_url").performTextReplacement("https://github.com/ExampleOrg/ExampleRepo")
            val selected = text(R.string.dev_repository_selected, "ExampleOrg", "ExampleRepo")
            compose.onNodeWithText(selected).performScrollTo().assertIsDisplayed()
            field("developer_repository_url").performTextReplacement("https://github.com.evil.test/ExampleOrg/ExampleRepo")
            compose.onNodeWithText(text(R.string.dev_repository_invalid)).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(selected).assertDoesNotExist()

            field("developer_advanced_editor_toggle").performClick()
            compose.onNodeWithText(text(R.string.dev_hide_advanced)).assertExists()
            compose.onNodeWithText(text(R.string.dev_git_editor)).performScrollTo().assertIsDisplayed()
            button(R.string.dev_load_source).assertIsNotEnabled()
            field("developer_advanced_editor_toggle").performClick()
            compose.onNodeWithText(text(R.string.dev_show_advanced)).assertExists()
            compose.onNodeWithText(text(R.string.dev_git_editor)).assertDoesNotExist()

            // Fixture is encrypted by the actual Android Keystore vault, never verified.
            field("developer_token_input").performTextReplacement("fixture-not-a-real-api-token-00000000")
            button(R.string.dev_save_token).performClick()
            status(R.string.dev_stored_unverified).assertIsDisplayed()
            button(R.string.dev_verify).assertIsEnabled()
            button(R.string.dev_revoke).assertIsEnabled()
            compose.onNodeWithText("Cloudflare").performScrollTo().performClick()
            status(R.string.dev_not_connected).assertIsDisplayed()
            button(R.string.dev_verify).assertIsNotEnabled()
            compose.onNodeWithText("GitHub").performScrollTo().performClick()
            status(R.string.dev_stored_unverified).assertIsDisplayed()
            button(R.string.dev_revoke).performClick()
            status(R.string.dev_not_connected).assertIsDisplayed()
            button(R.string.dev_verify).assertIsNotEnabled()
            assertTrue(preferences.all.isEmpty())
        } catch (failure: Throwable) {
            runCatching {
                val hierarchy = java.io.ByteArrayOutputStream()
                device.dumpWindowHierarchy(hierarchy)
                hierarchy.toString("UTF-8").take(180_000).chunked(2800).forEachIndexed { index, part ->
                    android.util.Log.i("DeveloperConnectionsUiTest", "Failure hierarchy[$index]: $part")
                }
            }
            runCatching {
                device.takeScreenshot(java.io.File(context.getExternalFilesDir(null), "failure-state.png"))
            }
            throw failure
        } finally {
            runCatching { onActivity { it.finish() } }
            try {
                val editor = preferences.edit().clear()
                // Vault stores strings only. Restore original ciphertext, never decrypt or log it.
                originalPreferences.forEach { (key, value) ->
                    check(value is String) { "Unexpected developer credential preference type" }
                    editor.putString(key, value)
                }
                assertTrue(editor.commit())
            } finally {
                try { languages.save(originalLanguage) } finally {
                    Locale.setDefault(originalLocale)
                }
            }
        }
    }

    @Test fun pluginPermissionReviewAndRemovalRevokeTheSavedServiceToken() {
        val registryFile = java.io.File(context.filesDir, "plugins/native-v1.json")
        val originalRegistry = registryFile.takeIf { it.isFile }?.readBytes()
        val preferences = context.getSharedPreferences("maximus_developer_connections", Context.MODE_PRIVATE)
        val originalPreferences = preferences.all.toMap()
        val languages = LanguagePreferenceStore(context)
        val originalLanguage = languages.load()
        val originalLocale = Locale.getDefault()
        val registry = ai.drfx.maximus.matrixai.plugins.NativePlugins.registry(context)
        val vault = ai.drfx.maximus.matrixai.studio.connections.DeveloperCredentialVault(context)
        try {
            check(registryFile.delete() || !registryFile.exists())
            check(preferences.edit().clear().commit())
            languages.save(AppLanguage.ENGLISH)
            device.executeShellCommand("am start -W -f 0x10008000 -n ${context.packageName}/${MainActivity::class.java.name}")
            onActivity { activity -> activity.setContent {
                MaximusMatrixTheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    PluginStorePanel()
                } }
            } }
            compose.onNodeWithTag("plugin_github").performScrollTo().performClick()
            compose.onNodeWithText(text(R.string.plugin_github_permissions)).assertIsDisplayed()
            assertFalse(registry.installed("github"))
            compose.onNodeWithTag("plugin_cancel_review").performClick()
            assertFalse(registry.installed("github"))
            compose.onNodeWithTag("plugin_github").performScrollTo().performClick()
            compose.onNodeWithTag("plugin_confirm_install").performClick()
            compose.waitUntil(5000) { registry.installed("github") }
            vault.save(ai.drfx.maximus.matrixai.studio.connections.DevelopmentService.GITHUB,
                "fixture-not-a-real-api-token-00000000")
            compose.onNodeWithTag("plugin_github").performScrollTo().performClick()
            compose.onNodeWithTag("plugin_confirm_remove").performClick()
            compose.waitUntil(5000) { !registry.installed("github") }
            assertFalse(vault.configured(ai.drfx.maximus.matrixai.studio.connections.DevelopmentService.GITHUB))
        } finally {
            runCatching { onActivity { it.finish() } }
            if (originalRegistry == null) registryFile.delete() else {
                registryFile.parentFile?.mkdirs(); registryFile.writeBytes(originalRegistry)
            }
            val editor = preferences.edit().clear()
            originalPreferences.forEach { (key, value) ->
                check(value is String); editor.putString(key, value)
            }
            check(editor.commit())
            languages.save(originalLanguage)
            Locale.setDefault(originalLocale)
        }
    }

    private fun text(id: Int, vararg args: Any): String =
        LanguagePreferenceStore.localizedContext(context, AppLanguage.ENGLISH).getString(id, *args)

    private fun status(id: Int): SemanticsNodeInteraction =
        compose.onAllNodesWithText(text(id)).onFirst().performScrollTo()

    private fun field(tag: String): SemanticsNodeInteraction =
        compose.onNodeWithTag(tag).performScrollTo()

    private fun button(id: Int): SemanticsNodeInteraction =
        // Merged Compose semantics owns the action and Disabled state. Android's
        // accessibility TextView/decorative Button children do not carry these.
        compose.onNodeWithText(text(id)).assertHasClickAction().performScrollTo()

    private fun onActivity(block: (MainActivity) -> Unit) {
        val completed = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>(null)
        Handler(Looper.getMainLooper()).post {
            try {
                val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().singleOrNull()
                block(checkNotNull(activity) { "MainActivity is not resumed" })
            } catch (error: Throwable) { failure.set(error) }
            finally { completed.countDown() }
        }
        check(completed.await(10, TimeUnit.SECONDS)) { "Main thread did not respond within 10 seconds" }
        failure.get()?.let { throw it }
    }

}

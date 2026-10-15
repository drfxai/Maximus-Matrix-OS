package ai.drfx.maximus.matrixai.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
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
import org.junit.runner.RunWith

/** Actual panel interaction without API requests, usable credentials or production UI hooks. */
@RunWith(AndroidJUnit4::class)
class DeveloperConnectionsUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    @OptIn(ExperimentalComposeUiApi::class)
    @Test fun offlineRepositoryEditingAndCredentialStateRemainSafe() {
        val preferences = context.getSharedPreferences("maximus_developer_connections", Context.MODE_PRIVATE)
        val originalPreferences = preferences.all.toMap()
        val languages = LanguagePreferenceStore(context)
        val originalLanguage = languages.load()
        val originalLocale = Locale.getDefault()
        val configurator = Configurator.getInstance()
        val originalIdleTimeout = configurator.waitForIdleTimeout
        try {
            assertTrue(preferences.edit().clear().commit())
            languages.save(AppLanguage.ENGLISH)
            configurator.setWaitForIdleTimeout(500)
            device.executeShellCommand("am start -W -f 0x10008000 -n ${context.packageName}/${MainActivity::class.java.name}")
            onActivity { activity ->
                // Isolate the real panel from unrelated continuously animated screens.
                // Resource IDs are exposed by this test wrapper only.
                activity.setContent {
                    MaximusMatrixTheme {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .semantics { testTagsAsResourceId = true }
                            .testTag("developer_ui_test_scroll")) {
                            DeveloperConnectionsPanel()
                        }
                    }
                }
            }
            await("Disconnected state was not rendered") {
                device.hasObject(By.text(text(R.string.dev_not_connected)))
            }
            assertFalse(button(R.string.dev_verify).isEnabled)
            assertFalse(button(R.string.dev_save_token).isEnabled)

            // Accessibility text edits invoke the actual Compose onValueChange/parser
            // wiring without requiring or dismissing the system keyboard.
            for (partial in listOf("h", "https://", "https://github.com/", "https://github.com/[")) {
                field("developer_repository_url").text = partial
                scroll().scrollIntoView(UiSelector().text(text(R.string.dev_repository_invalid)))
                await("Partial repository input did not render its validation error") {
                    device.hasObject(By.text(text(R.string.dev_repository_invalid)))
                }
            }
            field("developer_repository_url").text = "https://github.com/DrFXAi/Maximus-Matrix-OS"
            val selected = text(R.string.dev_repository_selected, "DrFXAi", "Maximus-Matrix-OS")
            scroll().scrollIntoView(UiSelector().text(selected))
            await("Valid repository selection was not rendered") { device.hasObject(By.text(selected)) }
            field("developer_repository_url").text = "https://github.com.evil.test/DrFXAi/Maximus-Matrix-OS"
            scroll().scrollIntoView(UiSelector().text(text(R.string.dev_repository_invalid)))
            await("Invalid origin retained an old repository selection") { !device.hasObject(By.text(selected)) }
            assertTrue(device.hasObject(By.text(text(R.string.dev_repository_invalid))))

            tagged("developer_advanced_editor_toggle").click()
            await("Advanced editor did not expand") { device.hasObject(By.text(text(R.string.dev_hide_advanced))) }
            tagged("developer_advanced_editor_toggle").click()
            await("Advanced editor did not collapse") { device.hasObject(By.text(text(R.string.dev_show_advanced))) }

            // Fixture is encrypted by the actual Android Keystore vault, never verified.
            field("developer_token_input").text = "fixture-not-a-real-api-token-00000000"
            button(R.string.dev_save_token).click()
            scroll().scrollToBeginning(12)
            await("Saved token was incorrectly shown as verified") {
                device.hasObject(By.text(text(R.string.dev_stored_unverified)))
            }
            assertTrue(button(R.string.dev_verify).isEnabled)
            assertTrue(button(R.string.dev_revoke).isEnabled)
            tagged("developer_token_input") // Scroll back to the service selector above the token.
            scroll().scrollToBeginning(12)
            checkNotNull(device.findObject(By.text("Cloudflare"))).click()
            await("Cloudflare inherited the GitHub credential state") {
                device.hasObject(By.text(text(R.string.dev_not_connected)))
            }
            assertFalse(button(R.string.dev_verify).isEnabled)
            scroll().scrollToBeginning(12)
            checkNotNull(device.findObject(By.text("GitHub"))).click()
            await("Returning to GitHub lost its stored-unverified state") {
                device.hasObject(By.text(text(R.string.dev_stored_unverified)))
            }
            button(R.string.dev_revoke).click()
            scroll().scrollToBeginning(12)
            await("Revocation did not restore disconnected state") {
                device.hasObject(By.text(text(R.string.dev_not_connected)))
            }
            assertFalse(button(R.string.dev_verify).isEnabled)
            assertTrue(preferences.all.isEmpty())
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
                    configurator.setWaitForIdleTimeout(originalIdleTimeout)
                }
            }
        }
    }

    private fun text(id: Int, vararg args: Any): String =
        LanguagePreferenceStore.localizedContext(context, AppLanguage.ENGLISH).getString(id, *args)

    private fun scroll() = UiScrollable(UiSelector().resourceId("developer_ui_test_scroll"))
        .setMaxSearchSwipes(12)

    private fun tagged(tag: String): UiObject2 {
        scroll().scrollToBeginning(12)
        scroll().scrollIntoView(UiSelector().resourceId(tag))
        return checkNotNull(device.findObject(By.res(tag))) { "Missing panel control: $tag" }
    }

    private fun field(tag: String): UiObject2 {
        val node = tagged(tag)
        return if (node.className == "android.widget.EditText") node
        else checkNotNull(node.findObject(By.clazz("android.widget.EditText"))) { "Missing editable input: $tag" }
    }

    private fun button(id: Int): UiObject2 {
        scroll().scrollToBeginning(12)
        scroll().scrollIntoView(UiSelector().text(text(id)))
        return checkNotNull(device.findObject(By.text(text(id)))) { "Missing panel button: ${text(id)}" }
    }

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

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        fail(message)
    }
}

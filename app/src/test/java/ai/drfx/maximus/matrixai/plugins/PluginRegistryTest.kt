package ai.drfx.maximus.matrixai.plugins

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class PluginRegistryTest {
    private fun registry(test: (PluginRegistry, java.io.File) -> Unit) {
        val dir = Files.createTempDirectory("native-plugins").toFile()
        try { test(PluginRegistry(java.io.File(dir, "plugins.json")), dir) }
        finally { dir.deleteRecursively() }
    }
    @Test fun permissionsNeedExplicitReviewAndPersistAcrossInstances() = registry { store, dir ->
        val plugin = PluginCatalog.find("github")
        assertFalse(store.installed(plugin.id))
        assertThrows(IllegalStateException::class.java) { store.install(plugin.id, plugin.fingerprint, false) }
        assertThrows(IllegalStateException::class.java) { store.install(plugin.id, "0".repeat(64), true) }
        store.install(plugin.id, plugin.fingerprint, true)
        val restored = PluginRegistry(java.io.File(dir, "plugins.json"))
        assertTrue(restored.installed(plugin.id))
        assertFalse(restored.installed("cloudflare"))
        restored.remove(plugin.id)
        assertFalse(store.installed(plugin.id))
    }
    @Test fun unknownPluginsAndCorruptStateNeverGainAuthority() = registry { store, dir ->
        assertThrows(IllegalArgumentException::class.java) { store.installed("downloaded-code") }
        java.io.File(dir, "plugins.json").writeText("corrupt")
        assertFalse(store.installed("github"))
        val p = PluginCatalog.find("github")
        assertThrows(Exception::class.java) { store.install(p.id, p.fingerprint, true) }
    }
    @Test fun changedManifestRequiresNewReview() = registry { store, dir ->
        val p = PluginCatalog.find("phone-linux")
        store.install(p.id, p.fingerprint, true)
        val file = java.io.File(dir, "plugins.json")
        file.writeText(file.readText().replace(p.fingerprint, "a".repeat(64)))
        assertFalse(store.installed(p.id))
        store.install(p.id, p.fingerprint, true)
        assertTrue(store.installed(p.id))
    }
    @Test fun registryInstancesDoNotLoseIndependentInstalls() = registry { store, dir ->
        val other = PluginRegistry(java.io.File(dir, "plugins.json"))
        val a = PluginCatalog.find("github")
        val b = PluginCatalog.find("cloudflare")
        store.install(a.id, a.fingerprint, true)
        other.install(b.id, b.fingerprint, true)
        assertTrue(store.installed(a.id))
        assertTrue(store.installed(b.id))
    }
}

package ai.drfx.maximus.matrixai.studio.phonehost

import org.junit.Assert.*
import org.junit.Test

class PhoneRuntimePathPolicyTest {
    @Test fun ubuntuMultiarchPackageMetadataIsAValidUnixPath() {
        val member = "var/lib/dpkg/info/libc6:arm64.list"
        assertEquals(member, PhoneRuntimePathPolicy.member("./$member"))
        assertEquals(member, PhoneRuntimePathPolicy.mappedRelative(member, emptyMap()))
        assertEquals("usr/lib/libc6:arm64", PhoneRuntimePathPolicy.linkTarget(
            "usr/bin/runtime", "../lib/libc6:arm64"))
        assertEquals("usr/lib/libc6:arm64/data", PhoneRuntimePathPolicy.mappedRelative(
            "lib/data", mapOf("lib" to "/usr/lib/libc6:arm64")))
    }

    @Test fun validRootfsNamesAndGuestLinksAreContained() {
        assertEquals("usr/bin/bash", PhoneRuntimePathPolicy.member("./usr/bin/bash"))
        assertEquals("usr/bin/bash", PhoneRuntimePathPolicy.mappedRelative(
            "bin/bash", mapOf("bin" to "usr/bin")))
        assertEquals("usr/lib/libc.so", PhoneRuntimePathPolicy.mappedRelative(
            "lib/libc.so", mapOf("lib" to "/usr/lib")))
        assertEquals("usr/bin", PhoneRuntimePathPolicy.linkTarget("bin", "/usr/bin"))
        assertEquals("usr/lib", PhoneRuntimePathPolicy.linkTarget(
            "usr/bin/rel", "../lib"))
    }

    @Test fun maliciousArchiveNamesCannotEscapeRoot() {
        for (path in listOf("../etc/passwd", "/etc/passwd", "./../../system/app",
            "usr/../../../system", "usr\\bin\\bash", "C:drive", "C:/Windows",
            "./C:drive", "name\u0000.txt")) {
            assertThrows(Exception::class.java) { PhoneRuntimePathPolicy.member(path) }
        }
    }

    @Test fun symlinkTraversalAndCyclesCannotEscapeRoot() {
        assertThrows(Exception::class.java) {
            PhoneRuntimePathPolicy.linkTarget("usr/bin/link", "C:drive")
        }
        assertThrows(Exception::class.java) {
            PhoneRuntimePathPolicy.member("var/lib/libc6:arm64/../../../../escape")
        }
        assertThrows(Exception::class.java) {
            PhoneRuntimePathPolicy.mappedRelative(
                "usr/bin/script", mapOf("usr" to "../../../../data"))
        }
        assertThrows(Exception::class.java) {
            PhoneRuntimePathPolicy.mappedRelative(
                "usr/bin/script", mapOf("usr" to "/usr"))
        }
        assertThrows(Exception::class.java) {
            PhoneRuntimePathPolicy.linkTarget("etc/conf", "../../../system")
        }
    }

    @Test fun linkTargetMayResolveWithinLinuxRootButNotAndroidHost() {
        val mapped = PhoneRuntimePathPolicy.mappedRelative(
            "etc/localtime", mapOf("etc" to "/usr/etc"))
        assertEquals("usr/etc/localtime", mapped)
        assertThrows(Exception::class.java) {
            PhoneRuntimePathPolicy.linkTarget("usr/local/escape", "../../../../etc")
        }
    }
}

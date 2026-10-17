package ai.drfx.maximus.matrixai.studio.phonehost

import org.junit.Assert.*
import org.junit.Test
import javax.xml.parsers.DocumentBuilderFactory

class PhoneAndroidStarterTest {
    @Test fun safeProjectNamesProduceAParseableApplicationAndRelativeSourcePaths() {
        for (name in listOf("My App", "A" + "x".repeat(48))) {
            val files = PhoneAndroidStarter.files(name, "ai.drfx.generated.example")
            assertTrue(files.keys.all { !it.startsWith('/') && ".." !in it && '\\' !in it })
            val xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                files.getValue("app/src/main/AndroidManifest.xml").byteInputStream())
            assertEquals(name, xml.getElementsByTagName("application").item(0).attributes
                .getNamedItem("android:label").nodeValue)
            assertEquals(1, xml.getElementsByTagName("activity").length)
        }
    }
    @Test fun namesAndNamespacesCannotInjectBuildCodeXmlOrSourcePaths() {
        for (name in listOf("a\";run{}", "../project", "A&project", "A".repeat(50))) {
            assertThrows(IllegalArgumentException::class.java) {
                PhoneAndroidStarter.files(name, "ai.drfx.generated.example")
            }
        }
        for (namespace in listOf("../escape", "a.\"evil", "a/b", "a..b")) {
            assertThrows(IllegalArgumentException::class.java) { PhoneAndroidStarter.files("Valid", namespace) }
        }
    }
}

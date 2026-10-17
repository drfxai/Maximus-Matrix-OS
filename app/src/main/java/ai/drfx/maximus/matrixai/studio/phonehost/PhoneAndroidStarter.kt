package ai.drfx.maximus.matrixai.studio.phonehost

/** This template targets the actual offline Maven repository in the pinned ARM64 bundle. */
internal object PhoneAndroidStarter {
    const val AGP = "8.11.0"
    const val KOTLIN = "1.9.22"
    const val COMPOSE_COMPILER = "1.5.10"
    const val COMPOSE_BOM = "2022.10.00"
    const val ACTIVITY = "1.5.1"
    const val SDK = 36
    const val BUILD_TOOLS = "35.0.0"

    fun files(name: String, namespace: String): Map<String, String> {
        require(name.matches(Regex("[A-Za-z][A-Za-z0-9 _-]{1,48}")))
        require(namespace.matches(Regex("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)+")))
        return linkedMapOf(
            "settings.gradle.kts" to """
                pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
                dependencyResolutionManagement {
                    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                    repositories { google(); mavenCentral() }
                }
                rootProject.name = "$name"
                include(":app")
            """.trimIndent(),
            "build.gradle.kts" to """
                plugins {
                    id("com.android.application") version "$AGP" apply false
                    id("org.jetbrains.kotlin.android") version "$KOTLIN" apply false
                }
            """.trimIndent(),
            "gradle.properties" to "org.gradle.jvmargs=-Xmx768m\nandroid.useAndroidX=true\nkotlin.compiler.execution.strategy=in-process\n",
            "app/build.gradle.kts" to """
                plugins {
                    id("com.android.application")
                    id("org.jetbrains.kotlin.android")
                }
                android {
                    namespace = "$namespace"
                    compileSdk = $SDK
                    buildToolsVersion = "$BUILD_TOOLS"
                    defaultConfig {
                        applicationId = "$namespace"
                        minSdk = 26
                        targetSdk = 35
                        versionCode = 1
                        versionName = "1.0.0"
                    }
                    buildFeatures { compose = true }
                    composeOptions { kotlinCompilerExtensionVersion = "$COMPOSE_COMPILER" }
                    compileOptions {
                        sourceCompatibility = JavaVersion.VERSION_17
                        targetCompatibility = JavaVersion.VERSION_17
                    }
                    kotlinOptions { jvmTarget = "17" }
                }
                // Explicit offline versions available in the pinned phone repository.
                configurations.configureEach {
                    resolutionStrategy.force(
                        "androidx.annotation:annotation:1.8.1",
                        "androidx.collection:collection:1.4.2",
                        "androidx.core:core-ktx:1.8.0",
                        "androidx.lifecycle:lifecycle-runtime-ktx:2.3.1",
                        "org.jetbrains.kotlin:kotlin-stdlib-jdk7:$KOTLIN",
                        "org.jetbrains.kotlin:kotlin-stdlib-jdk8:$KOTLIN"
                    )
                }
                dependencies {
                    implementation(platform("androidx.compose:compose-bom:$COMPOSE_BOM"))
                    implementation("androidx.activity:activity-compose:$ACTIVITY")
                    implementation("androidx.compose.material3:material3")
                }
            """.trimIndent(),
            "app/src/main/AndroidManifest.xml" to """
                <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                    <application android:label="$name" android:theme="@android:style/Theme.Material.Light.NoActionBar">
                        <activity android:name=".MainActivity" android:exported="true">
                            <intent-filter>
                                <action android:name="android.intent.action.MAIN" />
                                <category android:name="android.intent.category.LAUNCHER" />
                            </intent-filter>
                        </activity>
                    </application>
                </manifest>
            """.trimIndent(),
            "app/src/main/java/" + namespace.replace('.', '/') + "/MainActivity.kt" to """
                package $namespace
                import android.os.Bundle
                import androidx.activity.ComponentActivity
                import androidx.activity.compose.setContent
                import androidx.compose.material3.MaterialTheme
                import androidx.compose.material3.Surface
                import androidx.compose.material3.Text
                class MainActivity : ComponentActivity() {
                    override fun onCreate(savedInstanceState: Bundle?) {
                        super.onCreate(savedInstanceState)
                        setContent { MaterialTheme { Surface { Text("Welcome to $name") } } }
                    }
                }
            """.trimIndent(),
            "README.md" to "# $name\n\nOffline-compatible Compose starter. No APK has been compiled or tested yet.\n"
        )
    }
}

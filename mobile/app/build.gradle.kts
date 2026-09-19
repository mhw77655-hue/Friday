import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.tasks.testing.Test
import java.util.Properties
import java.io.FileInputStream

// BUG FIX (JV-TOKEN-01): JarvisBrainBridge.kt had the live HF Space bearer
// token hardcoded as a plaintext string literal, committed to git/GitHub.
// The Genesis roadmap doc claimed this was already fixed to read from
// BuildConfig.HF_TOKEN/local.properties — it wasn't; no BuildConfig/
// local.properties scaffolding existed anywhere in this file. This block
// reads HF_TOKEN from local.properties (gitignored, device-local, never
// committed) and exposes it as a generated BuildConfig field.
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

    android {
    namespace = "com.jarvis.app"
    compileSdk = 36
    ndkVersion = "27.3.13750724"

    defaultConfig {
        applicationId = "com.jarvis.app"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.2-phase2"

        // R1 voice substrate: instrumentation tests run on-device and prove
        // process isolation + supervised restart against the real :voice
        // service (VoiceSubstrateTest). androidx.test is not otherwise used.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "HF_TOKEN",
            "\"${localProperties.getProperty("HF_TOKEN", "")}\""
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // PRE-EXISTING BUILD FIX (1/2): sherpa-onnx bundles its own
    // libonnxruntime.so and onnxruntime-android (the disabled Silero VAD dep)
    // ships the same path — the native merge rejects the duplicate. sherpa's
    // copy is the canonical one for the shipped ABI; pickFirst resolves it.
    // Without this the APK cannot assemble, blocking on-device verification.
    //
    // PRE-EXISTING BUILD FIX (2/2): this host is an aarch64 container, but the
    // NDK ships an x86_64 llvm-strip that cannot execute here, so AGP's
    // stripDebugSymbols step fails on any APK containing native libs. Keeping
    // debug symbols in all .so skips the strip step entirely. Debug-only
    // environment — symbol stripping is purely a size optimization.
    packaging {
        jniLibs {
            pickFirsts += "**/libonnxruntime.so"
            keepDebugSymbols += "**/*.so"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // RALPH-VERIFY-DECOUPLE-LIVE-OLLAMA (2026-09-09): the two on-device-only
    // live-Ollama test classes (OllamaModelBackendTest + OllamaModelBackendLiveWiringTest)
    // are EXCLUDED from every default AGP unit-test task so a `--tests`
    // wildcard sweep can NEVER depend on the local Ollama server. The
    // recurring auto-reject loop across 2026-09-05..09 was a >120s
    // real-model-reply timeout (latency.*) and a socket timeout (model.*)
    // whenever the cold/evicted model made a live call slow — it failed the
    // ENTIRE batch regardless of which story was being verified. The tests are
    // NOT deleted — run them on demand via `:mobile:app:verifyLiveOllama` when
    // the server is warm (REPO_FACTS.md: serve pinned to 127.0.0.1:8080 with
    // OLLAMA_KEEP_ALIVE=-1 + a preloaded model).
    testOptions {
        unitTests {
            all {
                it.filter.excludeTestsMatching("com.jarvis.app.latency.OllamaModelBackendLiveWiringTest")
                it.filter.excludeTestsMatching("com.jarvis.app.model.OllamaModelBackendTest")
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-process:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")

    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.github.k2-fsa:sherpa-onnx:v1.12.40")

    // Voice model lifecycle (R2): tar.bz2 extraction for model archives
    // downloaded from the sherpa-onnx GitHub release (no Android built-in for bzip2).
    implementation("org.apache.commons:commons-compress:1.26.2")

    // ONNX Runtime for Silero VAD
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.16.3")

    // Human Core unit tests run as pure JVM tests (no device). The Android
    // platform org.json is only a stub under test, so the real org.json jar is
    // provided on the test classpath — standard practice, test-only.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")

    // Galaxy Memory Stage 02 unit tests run against pure-Kotlin zero-native
    // reference implementations (FakeMemoryGraphStore, KotlinVectorStore in
    // src/test) implementing the same bi-temporal/vector contracts as the
    // production Android-native stores. They use NO JDBC driver of any kind
    // (in particular no xerial sqlite-jdbc, whose glibc-linked native cannot
    // load on Android's bionic runtime) and no runtime-downloaded native. The
    // PRODUCTION stores (AndroidMemoryGraphStore, AndroidVectorStore) use
    // android.database.sqlite.SQLiteDatabase directly and are what ship in the
    // APK: binary-quantized BLOBs searched by pure-Kotlin Hamming distance, no
    // JDBC, no native vector-search extension. android.database.sqlite cannot
    // run in testDebugUnitTest
    // on this aarch64 host (Robolectric is unusable: its conscrypt dependency
    // ships only a linux-x86_64 native, so the Android runtime sandbox cannot
    // initialize on aarch64), so the reference stores exercise the same
    // semantics in plain JVM tests.

    // R1 voice substrate instrumentation tests (VoiceSubstrateTest) — the
    // on-device isolation proof. androidx.test is only on the androidTest
    // classpath; the app itself does not depend on it.
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("junit:junit:4.13.2")
}

// TERMUX-LIVE-INSTANCE-BROWSER-INTERFACE (AC6): the exact copy-paste run command
// for the standalone JVM entry point. Runs TermuxJarvisServer's main() on the
// JVM with the unit-test classpath (real production main classes + JVM deps).
val runTermuxJarvisServer by tasks.registering(JavaExec::class) {
    group = "application"
    description = "Run the JARVIS Termux live instance (localhost browser interface)."
    val mainOut = tasks.named("compileDebugKotlin", KotlinCompile::class).get().destinationDirectory
    val testOut = tasks.named("compileDebugUnitTestKotlin", KotlinCompile::class).get().destinationDirectory
    val runtimeCp = configurations.named("debugUnitTestRuntimeClasspath").get()
    classpath = files(mainOut.get().asFileTree, testOut.get().asFileTree, runtimeCp)
    mainClass.set("com.jarvis.app.termux.TermuxJarvisServer")
    // Pass-through: --port=8081 --ollama=127.0.0.1:8080 --model=<id>
}

// ---------------------------------------------------------------------------
// RALPH-VERIFY-DECOUPLE-LIVE-OLLAMA companion task: `testOptions.unitTests.all`
// (inside the android{} block above) EXCLUDES the two live-Ollama classes from
// every default sweep; this task runs those exact two classes on demand.
val verifyLiveOllama = tasks.register<Test>("verifyLiveOllama") {
    group = "verification"
    description = "Runs ONLY the two on-device live-Ollama test classes (OllamaModelBackendTest + OllamaModelBackendLiveWiringTest) against the local server at 127.0.0.1:8080. The default unit-test tasks exclude them; run THIS task on demand (server must be warm)."
    dependsOn("compileDebugKotlin", "compileDebugUnitTestKotlin")
    val mainOut = tasks.named("compileDebugKotlin", KotlinCompile::class).get().destinationDirectory
    val testOut = tasks.named("compileDebugUnitTestKotlin", KotlinCompile::class).get().destinationDirectory
    val runtimeCp = configurations.named("debugUnitTestRuntimeClasspath").get()
    // Classpath entries must be the output DIRECTORIES, not per-file expansions:
    // a JVM classloader cannot resolve package structure from individual
    // `.class` file entries (expanding with .asFileTree yields exactly that and
    // produces ClassNotFoundException for every test class).
    testClassesDirs = files(testOut)
    classpath = files(mainOut, testOut, runtimeCp)
    filter {
        includeTestsMatching("com.jarvis.app.latency.OllamaModelBackendLiveWiringTest")
        includeTestsMatching("com.jarvis.app.model.OllamaModelBackendTest")
        isFailOnNoMatchingTests = true
    }
}
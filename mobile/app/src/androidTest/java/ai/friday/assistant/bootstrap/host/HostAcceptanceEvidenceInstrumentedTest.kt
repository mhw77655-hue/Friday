package ai.friday.assistant.bootstrap.host

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class HostAcceptanceEvidenceInstrumentedTest {
    @Test
    fun installedAndroidHostPassesObservedFoundationProbes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.filesDir, "friday-acceptance-instrumented")
        val directories = listOf("workspace", "candidates", "evidence", "cache", "checkpoints")
            .map { File(root, it) }
        directories.forEach {
            assertTrue("Could not create ${it.name}", it.mkdirs() || it.isDirectory)
            assertTrue("${it.name} is not writable", it.canWrite())
        }

        val probe = File(directories[2], "read-write-probe.tmp")
        val marker = "friday-device-probe"
        probe.writeText(marker, Charsets.UTF_8)
        val readBack = probe.readText(Charsets.UTF_8)
        assertTrue(probe.delete())
        assertEquals(marker, readBack)

        val planExists = context.assets.open("friday/FRIDAY_DELIVERY_PLAN.md").use { it.read() >= 0 }
        val packageIdentity = context.packageName == "ai.friday.assistant"
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        val storage = StatFs(context.filesDir.absolutePath)
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val capacity = HostCapacity(
            apiLevel = Build.VERSION.SDK_INT,
            availableMemoryMb = memory.availMem / (1024L * 1024L),
            memoryClassMb = manager.memoryClass,
            availableStorageMb = storage.availableBytes / (1024L * 1024L),
            cpuCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
            cpuLoadPercent = -1.0,
            processPssMb = 0L,
            primaryAbi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            thermalStatus = if (Build.VERSION.SDK_INT >= 29) power.currentThermalStatus else -1,
            batteryPercent = -1,
            charging = false,
            powerSave = power.isPowerSaveMode,
            installedGgufCount = 0,
            nativeInferenceLibraryPresent = false
        )
        val config = HostBootstrapPolicy.configure(capacity)
        val bounded = config.contextTokenBudget in 1..32768 &&
            config.maxConcurrentExpensiveTasks in 1..8 &&
            config.recommendedModelMaxBytes >= 0L &&
            config.recommendedModelMaxBytes <= capacity.availableMemoryMb * 1024L * 1024L
        val honestLocalAdmission = !config.allowLocalModelLoad

        val evidence = HostAcceptanceEvidence.evaluate(
            HostAcceptanceEvidence.Checks(
                workspaceDirectoriesReady = directories.all { it.isDirectory && it.canWrite() },
                workspaceReadWriteVerified = true,
                hostProfileObserved = capacity.apiLevel == Build.VERSION.SDK_INT &&
                    capacity.availableMemoryMb > 0L && capacity.cpuCores > 0,
                resourcePolicyBounded = bounded,
                localInferenceAdmissionHonest = honestLocalAdmission,
                canonicalPlanBundled = planExists,
                appIdentityPresent = packageIdentity
            ),
            sourceRevision = "instrumented-test"
        )

        assertEquals(HostAcceptanceEvidence.FOUNDATION_READY, evidence.getString("status"))
        assertEquals(7, evidence.getInt("passed_count"))
        assertEquals(0, evidence.getInt("failed_count"))
        root.deleteRecursively()
    }
}

package ai.friday.assistant.companioncore.presence

import android.content.Context
import android.os.Build
import android.os.BatteryManager
import android.os.PowerManager
import ai.friday.assistant.companioncore.engine.CompanionClock
import ai.friday.assistant.companioncore.engine.FridayRuntimeIntegration
import ai.friday.assistant.companioncore.engine.FridayLocalRuntimeBinding
import ai.friday.assistant.companioncore.render.AnimationController
import ai.friday.assistant.companioncore.render.OrbStateMachine
import ai.friday.assistant.companioncore.resource.MobileResourceManagement
import ai.friday.assistant.companioncore.resource.MobileResourceManagement.DeviceConditions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.newSingleThreadContext

/**
 * Application-scoped holder for the Companion Core (plan §1.6 / §1.7).
 *
 * Owns the **Engine Tick Lane** — a dedicated single thread ("CompanionTick")
 * on which the Presence Engine's tick loop runs and every timing-sensitive
 * subsystem reads its inputs. Never blocks, does no I/O and makes no
 * framework-UI/audio calls on this lane (plan §1.6 rules).
 *
 * The tick loop starts at app init; [CompanionCore.onBackground] /
 * [CompanionCore.onForeground] (driven by the MainActivity lifecycle) drop the
 * tick to 0.1Hz and suspend rendering while keeping the low-rate signal
 * polling alive (plan §1.7). Device conditions (thermal/battery) are read from
 * Android's Power/Battery managers and feed §2.31 tier steps.
 */
object CompanionCoreHolder {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val tickLane = newSingleThreadContext("CompanionTick")
    private val laneScope = CoroutineScope(SupervisorJob() + tickLane)

    @Volatile private var core: CompanionCore? = null

    /**
     * Build (once) and start the Companion Core on FridayEngine's background
     * initialization lane. Synchronized construction prevents a duplicate tick loop.
     */
    @Synchronized
    fun init(context: Context): CompanionCore {
        val existing = core
        if (existing != null) return existing

        val clock = CompanionClock()
        // Every timing domain (Presence Engine mode timing, staleness, resource
        // governor cooldown) reads epoch time from the ONE master clock — no
        // second free-running System.currentTimeMillis source (spec Design
        // Principle 4 / Hard Invariant 5, audit F5).
        val nowEpoch = { clock.nowEpochMs() }
        val built = CompanionCore(
            integration = FridayRuntimeIntegration(
                FridayLocalRuntimeBinding(nowMs = nowEpoch),
                nowMs = nowEpoch
            ),
            presenceEngine = PresenceEngine(nowMs = nowEpoch),
            orbStateMachine = OrbStateMachine(),
            animationController = AnimationController(clock),
            resourceManagement = MobileResourceManagement(nowMs = nowEpoch),
            clock = clock,
            nowMs = nowEpoch,
            conditionsProvider = androidConditions(context.applicationContext)
        )
        built.start(laneScope)
        core = built
        return built
    }

    /** The active instance, or null before [init]. */
    fun instance(): CompanionCore? = core

    /** Android thermal/battery conditions for §2.31 (spec §2.31 inputs). */
    private fun androidConditions(context: Context): () -> DeviceConditions {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return {
            // API 30+ only; below that the frame-miss watchdog is the sole
            // throttling signal (spec §2.31 failure handling).
            val thermal: Float? =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) pm.getThermalHeadroom(0) else null
            val status = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            DeviceConditions(
                // Headroom ≤ 0°C means the device is currently throttling (§2.31).
                thermalThrottling = thermal != null && thermal <= 0f,
                thermalUnknown = thermal == null,
                batteryPercent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
                charging = charging,
                consecutiveFrameMisses = 0 // tick() merges the real watchdog value
            )
        }
    }

}

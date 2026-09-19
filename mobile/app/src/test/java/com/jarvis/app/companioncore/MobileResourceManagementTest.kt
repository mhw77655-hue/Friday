package com.jarvis.app.companioncore

import com.jarvis.app.companioncore.resource.MobileResourceManagement
import com.jarvis.app.companioncore.resource.MobileResourceManagement.DeviceConditions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §2.31 — Mobile Resource Management (core) tests.
 *
 * Tier ladder (HIGH→MEDIUM→LOW), concrete triggers (thermal, battery <15%
 * non-charging, sustained frame-miss streak), the 10s anti-flap cooldown,
 * background suspension, and the `thermalUnknown` fallback that blocks a
 * false step-up when the thermal API is unavailable.
 */
class MobileResourceManagementTest {

    private val clock = ManualEpochClock()
    private fun mrm() = MobileResourceManagement(nowMs = { clock.now })

    private fun conditions(
        thermalThrottling: Boolean = false,
        thermalUnknown: Boolean = false,
        batteryPercent: Int = 100,
        charging: Boolean = true,
        misses: Int = 0
    ) = DeviceConditions(
        thermalThrottling = thermalThrottling,
        thermalUnknown = thermalUnknown,
        batteryPercent = batteryPercent,
        charging = charging,
        consecutiveFrameMisses = misses
    )

    // ------------------------------------------------------------------ ladder

    @Test
    fun `starts at HIGH with the full budget`() {
        val m = mrm()
        assertEquals(MobileResourceManagement.QualityTier.HIGH, m.tier)
        assertEquals(60.0, m.budget.targetFps, 0.0)
        assertEquals(180, m.budget.maxParticleDensity)
        assertTrue(m.budget.avatarEligible)
    }

    @Test
    fun `thermal throttling steps down one rung`() {
        val m = mrm()
        m.evaluate(conditions(thermalThrottling = true))
        assertEquals(MobileResourceManagement.QualityTier.MEDIUM, m.tier)
        assertEquals(30.0, m.budget.targetFps, 0.0)
        assertFalse(m.budget.avatarEligible)
    }

    @Test
    fun `sustained throttling steps to LOW after the cooldown`() {
        val m = mrm()
        m.evaluate(conditions(thermalThrottling = true)) // HIGH → MEDIUM
        m.evaluate(conditions(thermalThrottling = true)) // within cooldown → stays
        assertEquals(MobileResourceManagement.QualityTier.MEDIUM, m.tier)
        clock.advance(MobileResourceManagement.COOLDOWN_MS + 1)
        m.evaluate(conditions(thermalThrottling = true)) // → LOW
        assertEquals(MobileResourceManagement.QualityTier.LOW, m.tier)
        assertEquals(15.0, m.budget.targetFps, 0.0)
    }

    // ------------------------------------------------------------------ battery

    @Test
    fun `battery below 15 percent and not charging steps down`() {
        val m = mrm()
        m.evaluate(conditions(batteryPercent = 10, charging = false))
        assertEquals(MobileResourceManagement.QualityTier.MEDIUM, m.tier)
    }

    @Test
    fun `low battery while charging does not step down`() {
        val m = mrm()
        m.evaluate(conditions(batteryPercent = 10, charging = true))
        assertEquals(MobileResourceManagement.QualityTier.HIGH, m.tier)
    }

    @Test
    fun `unknown battery never trips the low-battery trigger`() {
        val m = mrm()
        m.evaluate(conditions(batteryPercent = -1, charging = true))
        assertEquals(MobileResourceManagement.QualityTier.HIGH, m.tier)
    }

    // ------------------------------------------------------------------ frame-miss ladder

    @Test
    fun `30 frame misses steps HIGH to MEDIUM`() {
        val m = mrm()
        m.evaluate(conditions(misses = MobileResourceManagement.MISSES_MEDIUM))
        assertEquals(MobileResourceManagement.QualityTier.MEDIUM, m.tier)
    }

    @Test
    fun `60 frame misses steps MEDIUM to LOW`() {
        val m = mrm()
        m.evaluate(conditions(misses = MobileResourceManagement.MISSES_MEDIUM)) // → MEDIUM
        clock.advance(MobileResourceManagement.COOLDOWN_MS + 1)
        m.evaluate(conditions(misses = MobileResourceManagement.MISSES_LOW)) // → LOW
        assertEquals(MobileResourceManagement.QualityTier.LOW, m.tier)
    }

    @Test
    fun `a low miss count never steps down`() {
        val m = mrm()
        m.evaluate(conditions(misses = 5))
        assertEquals(MobileResourceManagement.QualityTier.HIGH, m.tier)
    }

    // ------------------------------------------------------------------ step-up + cooldown

    @Test
    fun `conditions clear plus cooldown steps back up`() {
        val m = mrm()
        m.evaluate(conditions(thermalThrottling = true)) // → MEDIUM, cooldown set
        clock.advance(MobileResourceManagement.COOLDOWN_MS + 1)
        m.evaluate(conditions()) // healthy → HIGH
        assertEquals(MobileResourceManagement.QualityTier.HIGH, m.tier)
    }

    @Test
    fun `cooldown prevents oscillation while conditions improve early`() {
        val m = mrm()
        m.evaluate(conditions(thermalThrottling = true)) // → MEDIUM, cooldown set
        clock.advance(1_000)
        m.evaluate(conditions()) // healthy but within cooldown → no step-up
        assertEquals(MobileResourceManagement.QualityTier.MEDIUM, m.tier)
    }

    @Test
    fun `unknown thermal signal never causes a step-up`() {
        val m = mrm()
        m.evaluate(conditions(thermalThrottling = true)) // → MEDIUM
        clock.advance(MobileResourceManagement.COOLDOWN_MS + 1)
        // No thermal API on this device: thermalUnknown means "don't assume healthy".
        m.evaluate(conditions(thermalUnknown = true))
        assertEquals(MobileResourceManagement.QualityTier.MEDIUM, m.tier)
    }

    // ------------------------------------------------------------------ background

    @Test
    fun `background suspension blocks tier changes`() {
        val m = mrm()
        m.setBackgroundSuspended(true)
        assertTrue(m.backgroundSuspended)
        m.evaluate(conditions(thermalThrottling = true))
        assertEquals("no tier churn while suspended", MobileResourceManagement.QualityTier.HIGH, m.tier)
    }

    @Test
    fun `resume clears background suspension`() {
        val m = mrm()
        m.setBackgroundSuspended(true)
        m.setBackgroundSuspended(false)
        assertFalse(m.backgroundSuspended)
    }
}

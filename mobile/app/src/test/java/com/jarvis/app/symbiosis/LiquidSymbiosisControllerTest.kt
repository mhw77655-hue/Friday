package com.jarvis.app.symbiosis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidSymbiosisControllerTest {
    private class MemoryStore(var value: SymbiosisProfile = SymbiosisProfile()) : SymbiosisProfileStore {
        override fun load() = value
        override fun save(profile: SymbiosisProfile) { value = profile }
    }

    private fun condition(
        ram: Long = 4096,
        storage: Long = 20_000,
        thermal: Int = 0,
        battery: Int = 90,
        foreground: Boolean = true,
        powerSave: Boolean = false
    ) = HostCondition(ram, 6144, storage, battery, false, powerSave, thermal, foreground, true)

    private val models = listOf(
        ModelCandidate("small", 1300, 900, 1),
        ModelCandidate("large", 3500, 2200, 2)
    )

    @Test fun choosesBestEligibleModelWhenHostHasHeadroom() {
        val controller = LiquidSymbiosisController(MemoryStore(), { models }, 8)
        val plan = controller.plan(condition(), CognitiveWorkload.CHAT)
        assertEquals("large", plan.modelId)
        assertTrue(plan.keepModelResident)
        assertFalse(plan.deferWork)
    }

    @Test fun fallsBackToSmallModelWhenMemoryIsTight() {
        val controller = LiquidSymbiosisController(MemoryStore(), { models }, 8)
        val plan = controller.plan(condition(ram = 1500), CognitiveWorkload.CHAT)
        assertEquals("small", plan.modelId)
        assertEquals(2048, plan.contextTokens)
        assertEquals(1, plan.cpuThreads)
    }

    @Test fun unloadsAndDefersWhenMemoryIsCritical() {
        val controller = LiquidSymbiosisController(MemoryStore(), { models }, 8)
        val plan = controller.plan(condition(ram = 500), CognitiveWorkload.REASONING)
        assertNull(plan.modelId)
        assertTrue(plan.deferWork)
        assertEquals(0, plan.contextTokens)
    }

    @Test fun thermalPressureDefersCompute() {
        val controller = LiquidSymbiosisController(MemoryStore(), { models }, 8)
        val plan = controller.plan(condition(thermal = 3), CognitiveWorkload.CODING)
        assertNull(plan.modelId)
        assertTrue(plan.deferWork)
        assertFalse(plan.allowPerception)
    }

    @Test fun successfulExperienceIsPersistedAndUpdatesLearningProfile() {
        val store = MemoryStore()
        val controller = LiquidSymbiosisController(store, { models }, 8)
        controller.observe(condition(), CognitiveWorkload.CHAT,
            ExperienceSample(1200, 120, successful = true))
        assertEquals(1L, store.value.sampleCount)
        assertTrue(store.value.latencyEmaMs > 0)
    }
}

package ai.friday.assistant.model.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResidentModelBootstrapPolicyTest {
    private val reserve = 128L * 1024L * 1024L

    @Test
    fun onlyOffersPinnedModelsThatFitCurrentStorageAndReserve() {
        val tiny = OfflineModelCatalog.tinyFallback
        val primary = OfflineModelCatalog.resident

        val belowTiny = tiny.expectedMinimumBytes + reserve - 1L
        assertTrue(residentBootstrapCandidates(belowTiny).isEmpty())

        val tinyOnly = residentBootstrapCandidates(tiny.expectedMinimumBytes + reserve)
        assertEquals(listOf(tiny.id), tinyOnly.map { it.id })

        val both = residentBootstrapCandidates(primary.expectedMinimumBytes + reserve)
        assertEquals(listOf(primary.id, tiny.id), both.map { it.id })
        assertTrue(both.all { !it.sha256.isNullOrBlank() })
    }

    @Test
    fun alreadyInstalledPinnedModelRemainsEligibleWhenFreeStorageIsLow() {
        val primary = OfflineModelCatalog.resident
        val candidates = residentBootstrapCandidates(
            availableStorageBytes = 1L,
            installedAssetIds = setOf(primary.id),
            minimumFreeBytes = reserve
        )
        assertEquals(listOf(primary.id), candidates.map { it.id })
    }

    @Test
    fun liveStorageBudgetDoesNotDependOnTheOneTimeHostModelRecommendation() {
        // The candidate selector accepts current storage only. A stale 0-byte
        // recommendation from first boot cannot permanently block a later load.
        val tiny = OfflineModelCatalog.tinyFallback
        val candidates = residentBootstrapCandidates(tiny.expectedMinimumBytes + reserve)
        assertTrue(candidates.any { it.id == tiny.id })
    }
}

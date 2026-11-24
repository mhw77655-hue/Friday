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
        assertTrue(residentBootstrapCandidates(tiny.expectedMinimumBytes + reserve - 1L).isEmpty())
        assertEquals(
            listOf(tiny.id),
            residentBootstrapCandidates(tiny.expectedMinimumBytes + reserve).map { it.id }
        )
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
    fun laterLiveStorageBudgetIsIndependentOfTheOneTimeHostRecommendation() {
        val tiny = OfflineModelCatalog.tinyFallback
        val candidates = residentBootstrapCandidates(tiny.expectedMinimumBytes + reserve)
        assertTrue(candidates.any { it.id == tiny.id })
    }
}

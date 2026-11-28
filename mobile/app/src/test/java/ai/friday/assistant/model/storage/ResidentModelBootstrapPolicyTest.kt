package ai.friday.assistant.model.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResidentModelBootstrapPolicyTest {
    private val reserve = 128L * 1024L * 1024L

    @Test
    fun onlyOffersPinnedModelsThatFitCurrentStorageAndReserve() {
        val tiny = OfflineModelCatalog.tinyFallback
        val middle = OfflineModelCatalog.fallback
        val primary = OfflineModelCatalog.resident
        assertTrue(residentBootstrapCandidates(tiny.expectedMinimumBytes + reserve - 1L).isEmpty())
        assertEquals(
            listOf(tiny.id),
            residentBootstrapCandidates(tiny.expectedMinimumBytes + reserve).map { it.id }
        )
        val middleAndTiny = residentBootstrapCandidates(middle.expectedMinimumBytes + reserve)
        assertEquals(listOf(middle.id, tiny.id), middleAndTiny.map { it.id })
        val all = residentBootstrapCandidates(primary.expectedMinimumBytes + reserve)
        assertEquals(listOf(primary.id, middle.id, tiny.id), all.map { it.id })
        assertTrue(all.all { !it.sha256.isNullOrBlank() })
    }

    @Test
    fun alreadyInstalledPinnedModelRemainsEligibleWhenFreeStorageIsLow() {
        val middle = OfflineModelCatalog.fallback
        val candidates = residentBootstrapCandidates(
            availableStorageBytes = 1L,
            installedAssetIds = setOf(middle.id),
            minimumFreeBytes = reserve
        )
        assertEquals(listOf(middle.id), candidates.map { it.id })
    }

    @Test
    fun laterLiveStorageBudgetIsIndependentOfTheOneTimeHostRecommendation() {
        val middle = OfflineModelCatalog.fallback
        val candidates = residentBootstrapCandidates(middle.expectedMinimumBytes + reserve)
        assertTrue(candidates.any { it.id == middle.id })
        assertTrue(candidates.any { it.id == OfflineModelCatalog.tinyFallback.id })
    }
}

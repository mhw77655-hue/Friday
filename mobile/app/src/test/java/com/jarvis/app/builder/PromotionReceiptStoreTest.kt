package com.jarvis.app.builder

import com.jarvis.app.evolution.PromotionReceipt
import com.jarvis.app.evolution.RollbackPoint
import com.jarvis.app.humancore.store.FileStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicLong

class PromotionReceiptStoreTest {
    @Test
    fun `receipt status and audit time survive a process restart`() {
        val root = File.createTempFile("friday-receipts", "").apply { delete(); mkdirs() }
        try {
            val file = File(root, "promotion-receipts.json")
            val clock = AtomicLong(100L)
            val store = PromotionReceiptStore(FileStorage(root), file, clock::get)
            val receipt = PromotionReceipt(
                genomeId = "genome-42",
                rollbackPoint = RollbackPoint(
                    previousGenomeId = null,
                    previousSystemId = null,
                    timestampMs = 90L,
                    previousCapabilityGenomeIds = emptyMap()
                ),
                systemId = "organism-genome-42",
                promotedAtMs = 95L
            )

            store.save(receipt)
            assertEquals(100L, store.get("genome-42")?.updatedAtMs)

            clock.set(200L)
            store.markRolledBack("genome-42", "regression observed")

            val restored = PromotionReceiptStore(FileStorage(root), file, { 300L })
            val record = restored.get("genome-42")
            assertNotNull(record)
            assertEquals(PromotionReceiptStore.Status.ROLLED_BACK, record?.status)
            assertEquals("regression observed", record?.reason)
            assertEquals(200L, record?.updatedAtMs)
        } finally {
            root.deleteRecursively()
        }
    }
}

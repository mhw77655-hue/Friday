package com.jarvis.app.builder

import com.jarvis.app.evolution.PromotionReceipt
import com.jarvis.app.evolution.RollbackPoint
import com.jarvis.app.humancore.store.FileStorage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Durable rollback receipts for capabilities promoted by Friday Builder. */
// Timestamps are supplied by the single Android composition-root clock.
class PromotionReceiptStore(
    private val fileStorage: FileStorage,
    private val file: File,
    private val nowMs: () -> Long,
    private val maxRecords: Int = 128
) {
    private val lock = Any()
    private val records = LinkedHashMap<String, StoredPromotionReceipt>()

    init {
        load()
    }

    fun save(receipt: PromotionReceipt) = synchronized(lock) {
        records[receipt.genomeId] = StoredPromotionReceipt(
            receipt = receipt,
            status = Status.PROMOTED,
            reason = null,
            updatedAtMs = nowMs()
        )
        trim()
        persist()
    }

    fun get(receiptId: String): StoredPromotionReceipt? = synchronized(lock) {
        records[receiptId]
    }

    fun list(limit: Int = 32): List<StoredPromotionReceipt> = synchronized(lock) {
        records.values.sortedByDescending { it.updatedAtMs }.take(limit.coerceIn(1, maxRecords))
    }

    fun markRolledBack(receiptId: String, reason: String) = synchronized(lock) {
        val current = records[receiptId] ?: return
        records[receiptId] = current.copy(
            status = Status.ROLLED_BACK,
            reason = reason.take(1024),
            updatedAtMs = System.currentTimeMillis()
        )
        persist()
    }

    private fun load() {
        synchronized(lock) {
            if (!file.isFile || file.length() !in 1..1_048_576L) return@synchronized
            runCatching {
            val array = JSONArray(file.readText())
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val receiptJson = item.getJSONObject("receipt")
                val pointJson = receiptJson.optJSONObject("rollbackPoint")
                val point = pointJson?.let {
                    val previous = it.optJSONObject("previousCapabilityGenomeIds") ?: JSONObject()
                    val mapping = linkedMapOf<String, String>()
                    val keys = previous.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        mapping[key] = previous.getString(key)
                    }
                    RollbackPoint(
                        previousGenomeId = it.optString("previousGenomeId").takeIf(String::isNotBlank),
                        previousSystemId = it.optString("previousSystemId").takeIf(String::isNotBlank),
                        timestampMs = it.optLong("timestampMs"),
                        previousCapabilityGenomeIds = mapping
                    )
                }
                val receipt = PromotionReceipt(
                    genomeId = receiptJson.getString("genomeId"),
                    rollbackPoint = point,
                    systemId = receiptJson.getString("systemId"),
                    promotedAtMs = receiptJson.optLong("promotedAtMs")
                )
                records[receipt.genomeId] = StoredPromotionReceipt(
                    receipt = receipt,
                    status = runCatching { Status.valueOf(item.optString("status")) }.getOrDefault(Status.PROMOTED),
                    reason = item.optString("reason").takeIf(String::isNotBlank),
                    updatedAtMs = item.optLong("updatedAtMs", receipt.promotedAtMs)
                )
            }
                trim()
            }
        }
    }

    private fun persist() {
        file.parentFile?.mkdirs()
        val array = JSONArray()
        records.values.sortedBy { it.updatedAtMs }.forEach { stored ->
            val receipt = stored.receipt
            val point = receipt.rollbackPoint?.let {
                JSONObject().apply {
                    put("previousGenomeId", it.previousGenomeId)
                    put("previousSystemId", it.previousSystemId)
                    put("timestampMs", it.timestampMs)
                    put("previousCapabilityGenomeIds", JSONObject(it.previousCapabilityGenomeIds))
                }
            }
            array.put(
                JSONObject().apply {
                    put("status", stored.status.name)
                    put("reason", stored.reason)
                    put("updatedAtMs", stored.updatedAtMs)
                    put("receipt", JSONObject().apply {
                        put("genomeId", receipt.genomeId)
                        put("systemId", receipt.systemId)
                        put("promotedAtMs", receipt.promotedAtMs)
                        if (point != null) put("rollbackPoint", point)
                    })
                }
            )
        }
        fileStorage.write(file, array.toString())
    }

    private fun trim() {
        if (records.size <= maxRecords) return
        val keep = records.values.sortedByDescending { it.updatedAtMs }.take(maxRecords)
            .associateBy { it.receipt.genomeId }
        records.clear()
        records.putAll(keep)
    }

    enum class Status { PROMOTED, ROLLED_BACK }

    data class StoredPromotionReceipt(
        val receipt: PromotionReceipt,
        val status: Status,
        val reason: String?,
        val updatedAtMs: Long
    )
}

package ai.friday.assistant.github.reporting

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONObject

/**
 * Durable, private inbox for REA evidence that Friday has not yet assimilated.
 * Packets are evidence only; this store never applies their contents to memory or code.
 */
class ReaAssimilationPacketStore(context: Context) {
    private val appContext = context.applicationContext

    data class StoredPacket(
        val investigationId: String,
        val packetId: String,
        val targetLocator: String,
        val findingCount: Int,
        val evidenceCount: Int,
        val filePath: String
    )

    fun save(investigationId: String, json: String): StoredPacket {
        validateInvestigationId(investigationId)
        val packet = validatePacket(json)
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > MAX_PACKET_BYTES) {
            throw IOException("REA assimilation packet exceeds the local storage limit.")
        }

        val directory = File(appContext.filesDir, PACKET_DIRECTORY)
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Friday could not create the private REA packet directory.")
        }
        val destination = File(directory, investigationId + ".json")
        val temporary = File(directory, investigationId + ".tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (failure: Exception) {
            temporary.delete()
            throw IOException("Friday could not persist the REA assimilation packet.", failure)
        }

        return StoredPacket(
            investigationId = investigationId,
            packetId = packet.getString("packet_id"),
            targetLocator = packet.getJSONObject("target").getString("locator"),
            findingCount = packet.optJSONArray("findings")?.length() ?: 0,
            evidenceCount = packet.getJSONArray("evidence_records").length(),
            filePath = destination.absolutePath
        )
    }

    fun read(investigationId: String): String? {
        validateInvestigationId(investigationId)
        val file = packetFile(investigationId)
        if (!file.isFile) return null
        if (file.length() > MAX_PACKET_BYTES.toLong()) {
            throw IOException("Stored REA assimilation packet exceeds the local read limit.")
        }
        val json = file.readText(StandardCharsets.UTF_8)
        validatePacket(json)
        return json
    }

    fun exists(investigationId: String): Boolean {
        validateInvestigationId(investigationId)
        return packetFile(investigationId).isFile
    }

    private fun packetFile(investigationId: String) =
        File(File(appContext.filesDir, PACKET_DIRECTORY), investigationId + ".json")

    private fun validateInvestigationId(value: String) {
        require(value.matches(Regex("[A-Za-z0-9._-]{8,80}"))) {
            "REA investigation id is malformed."
        }
    }

    companion object {
        private const val PACKET_DIRECTORY = "rea-assimilation/packets"
        private const val MAX_PACKET_BYTES = 16 * 1024 * 1024
        private const val PACKET_SCHEMA = "friday.rea-assimilation/v1"

        /** Shared validation used before both network acceptance and local persistence. */
        internal fun validatePacket(json: String): JSONObject {
            require(json.toByteArray(StandardCharsets.UTF_8).size <= MAX_PACKET_BYTES) {
                "REA assimilation packet exceeds the size limit."
            }
            val packet = runCatching { JSONObject(json) }
                .getOrElse { throw IOException("REA assimilation packet is not valid JSON.", it) }
            require(packet.optString("schema") == PACKET_SCHEMA) {
                "REA assimilation packet schema is unsupported."
            }
            require(packet.optString("packet_id").matches(Regex("rea_packet_[0-9a-f]{32}"))) {
                "REA assimilation packet id is invalid."
            }

            val target = packet.optJSONObject("target")
                ?: throw IOException("REA assimilation packet has no target identity.")
            require(target.optString("kind") == "website") {
                "This ingestion path only accepts the reviewed Neomas website target."
            }
            val locator = target.optString("locator")
            val uri = runCatching { URI(locator) }
                .getOrElse { throw IOException("REA target locator is malformed.", it) }
            require(uri.scheme == "https" && uri.host in setOf("neomas.app", "www.neomas.app") &&
                uri.userInfo == null && uri.port == -1
            ) { "REA target is outside the approved Neomas HTTPS origin." }

            val trust = packet.optJSONObject("trust")
                ?: throw IOException("REA assimilation packet is missing its trust boundary.")
            require(trust.optString("source_content") == "untrusted" &&
                !trust.optBoolean("model_or_tool_output_is_authority", true) &&
                !trust.optBoolean("executable_instructions", true)
            ) { "REA packet does not preserve Friday's untrusted-evidence boundary." }

            val assimilation = packet.optJSONObject("assimilation")
                ?: throw IOException("REA assimilation packet is missing its assimilation state.")
            require(!assimilation.optBoolean("execution_allowed", true)) {
                "REA evidence cannot authorize execution."
            }

            val records = packet.optJSONArray("evidence_records")
            val refs = packet.optJSONArray("evidence_refs")
            val rawResults = packet.optJSONArray("raw_results")
            require(records != null && records.length() > 0 &&
                refs != null && refs.length() > 0 &&
                rawResults != null && rawResults.length() > 0
            ) { "REA packet is missing its original evidence records or provenance references." }

            return packet
        }
    }
}

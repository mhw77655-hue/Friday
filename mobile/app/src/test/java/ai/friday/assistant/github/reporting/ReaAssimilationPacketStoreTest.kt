package ai.friday.assistant.github.reporting

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaAssimilationPacketStoreTest {
    private fun validPacket(): JSONObject {
        val evidenceId = "ev_" + "a".repeat(64)
        return JSONObject()
            .put("schema", "friday.rea-assimilation/v1")
            .put("packet_id", "rea_packet_" + "b".repeat(32))
            .put("target", JSONObject()
                .put("kind", "website")
                .put("locator", "https://neomas.app/")
            )
            .put("trust", JSONObject()
                .put("source_content", "untrusted")
                .put("model_or_tool_output_is_authority", false)
                .put("executable_instructions", false)
            )
            .put("assimilation", JSONObject()
                .put("state", "pending_review")
                .put("execution_allowed", false)
            )
            .put("evidence_records", JSONArray().put(JSONObject().put("evidence_id", evidenceId)))
            .put("evidence_refs", JSONArray().put(evidenceId))
            .put("findings", JSONArray())
            .put("capability_candidates", JSONArray().put(JSONObject()
                .put("candidate_id", "friday_memory_resurface_review")
                .put("execution_allowed", false)))
            .put("adaptation_plan", JSONObject()
                .put("owner_review_required", true)
                .put("execution_allowed", false)
                .put("proposal_ids", JSONArray().put("friday_memory_resurface_review")))
            .put("raw_results", JSONArray().put(JSONObject().put("tool", "inspect_web_page")))
    }

    @Test fun acceptsBoundedUntrustedNeomasPacket() {
        val packet = ReaAssimilationPacketStore.validatePacket(validPacket().toString())
        assertEquals("friday.rea-assimilation/v1", packet.getString("schema"))
        assertEquals("rea_packet_" + "b".repeat(32), packet.getString("packet_id"))
        assertFalse(packet.getJSONObject("assimilation").getBoolean("execution_allowed"))
        assertEquals(1, packet.getJSONArray("capability_candidates").length())
        assertFalse(packet.getJSONObject("adaptation_plan").getBoolean("execution_allowed"))
    }

    @Test fun rejectsExecutionAuthority() {
        val packet = validPacket()
        packet.put("assimilation", JSONObject().put("execution_allowed", true))
        assertTrue(runCatching {
            ReaAssimilationPacketStore.validatePacket(packet.toString())
        }.isFailure)
    }

    @Test fun rejectsModelOutputPromotedToAuthority() {
        val packet = validPacket()
        packet.put("trust", JSONObject()
            .put("source_content", "untrusted")
            .put("model_or_tool_output_is_authority", true)
            .put("executable_instructions", false)
        )
        assertTrue(runCatching {
            ReaAssimilationPacketStore.validatePacket(packet.toString())
        }.isFailure)
    }

    @Test fun rejectsNonNeomasOriginsAndNonHttpsTargets() {
        val otherHost = validPacket()
        otherHost.put("target", JSONObject().put("kind", "website").put("locator", "https://example.com/"))
        val insecure = validPacket()
        insecure.put("target", JSONObject().put("kind", "website").put("locator", "http://neomas.app/"))
        assertTrue(runCatching { ReaAssimilationPacketStore.validatePacket(otherHost.toString()) }.isFailure)
        assertTrue(runCatching { ReaAssimilationPacketStore.validatePacket(insecure.toString()) }.isFailure)
    }

    @Test fun rejectsPacketsWithoutCanonicalEvidence() {
        val packet = validPacket()
        packet.put("evidence_records", JSONArray())
        assertTrue(runCatching {
            ReaAssimilationPacketStore.validatePacket(packet.toString())
        }.isFailure)
    }
}

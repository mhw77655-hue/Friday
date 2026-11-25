package ai.friday.assistant.approvals

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalApprovalQueueJsonTest {
    @Test
    fun nestedNullValuesArePreservedInsteadOfThrowing() {
        val json = jsonApprovalValue(
            mapOf("empty" to null, "list" to listOf("value", null))
        ) as JSONObject
        assertEquals(JSONObject.NULL, json.get("empty"))
        assertEquals("value", json.getJSONArray("list").get(0))
        assertEquals(JSONObject.NULL, json.getJSONArray("list").get(1))
    }
}

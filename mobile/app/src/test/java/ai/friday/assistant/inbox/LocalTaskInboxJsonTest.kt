package ai.friday.assistant.inbox

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalTaskInboxJsonTest {
    @Test
    fun nestedNullValuesArePreservedInsteadOfThrowing() {
        val json = jsonValue(
            mapOf("empty" to null, "list" to listOf("value", null))
        ) as JSONObject
        assertEquals(JSONObject.NULL, json.get("empty"))
        assertEquals("value", json.getJSONArray("list").get(0))
        assertEquals(JSONObject.NULL, json.getJSONArray("list").get(1))
    }
}

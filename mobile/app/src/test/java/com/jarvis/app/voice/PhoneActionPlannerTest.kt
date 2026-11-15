package com.jarvis.app.voice

import com.jarvis.app.model.GenerateRequest
import com.jarvis.app.model.GenerateResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneActionPlannerTest {
    @Test fun `plans a visible screen action using Friday model output`() = runBlocking {
        var calls = 0
        val planner = PhoneActionPlanner { _: GenerateRequest ->
            calls++
            GenerateResult(content = """{"kind":"action","type":"tap_text","target":"Send"}""")
        }

        val result = planner.plan(
            "Friday, tap Send",
            "package=com.example.chat\nnode=Send;clickable=true;editable=false;focused=false;scrollable=false"
        )

        assertEquals(1, calls)
        assertEquals(
            PhoneActionPlanner.Result.Action(PhoneControlCommand.TapText("Send")),
            result
        )
    }

    @Test fun `does not invent a screen target absent from accessibility evidence`() = runBlocking {
        val planner = PhoneActionPlanner { _: GenerateRequest ->
            GenerateResult(content = """{"kind":"action","type":"tap_text","target":"Transfer money"}""")
        }

        val result = planner.plan("Tap Transfer money", "package=com.example.chat\nnode=Inbox;clickable=true")

        assertTrue(result is PhoneActionPlanner.Result.Unavailable)
    }

    @Test fun `text entry requires a verified focused editable field`() = runBlocking {
        val planner = PhoneActionPlanner { _: GenerateRequest ->
            GenerateResult(content = """{"kind":"action","type":"type_text","text":"hello"}""")
        }

        val blocked = planner.plan("Type hello", "package=com.example.chat\nnode=Message;editable=true;focused=false")
        val allowed = planner.plan("Type hello", "package=com.example.chat\nnode=Message;editable=true;focused=true")

        assertTrue(blocked is PhoneActionPlanner.Result.Unavailable)
        assertEquals(PhoneActionPlanner.Result.Action(PhoneControlCommand.TypeText("hello")), allowed)
    }

    @Test fun `ordinary conversation does not invoke the device planner`() = runBlocking {
        var calls = 0
        val planner = PhoneActionPlanner { _: GenerateRequest ->
            calls++
            GenerateResult(content = """{"kind":"conversation"}""")
        }

        val result = planner.plan("What do you think about this idea?", "package=com.example.chat")

        assertEquals(0, calls)
        assertEquals(PhoneActionPlanner.Result.NotPhoneAction, result)
    }

    @Test fun `device intent detector catches natural system and app requests`() {
        assertTrue(PhoneActionPlanner.isLikelyDeviceRequest("Could you lower the brightness?"))
        assertTrue(PhoneActionPlanner.isLikelyDeviceRequest("Please open WhatsApp"))
        assertTrue(PhoneActionPlanner.isLikelyDeviceRequest("Tap the send button"))
        assertFalse(PhoneActionPlanner.isLikelyDeviceRequest("Tell me what you think"))
    }
}

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

    @Test fun `does not accept a substring as proof of a visible tap target`() = runBlocking {
        val planner = PhoneActionPlanner { _: GenerateRequest ->
            GenerateResult(content = """{"kind":"action","type":"tap_text","target":"Send"}""")
        }

        val result = planner.plan(
            "Tap Send",
            "package=com.example.chat\nnode=Send message;clickable=true;editable=false;focused=false"
        )

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

    @Test fun `screen context is never sent when Friday's local model is unavailable`() = runBlocking {
        var calls = 0
        val planner = PhoneActionPlanner(
            complete = { _: GenerateRequest ->
                calls++
                GenerateResult(content = """{"kind":"action","type":"tap_text","target":"Send"}""")
            },
            localModelReady = { false }
        )

        val result = planner.plan("Tap Send", "private screen content")

        assertEquals(0, calls)
        assertTrue(result is PhoneActionPlanner.Result.Unavailable)
        assertTrue((result as PhoneActionPlanner.Result.Unavailable).reason.contains("No screen context was sent"))
    }

    @Test fun `supported natural phone skills work without loading a model`() = runBlocking {
        var calls = 0
        val planner = PhoneActionPlanner(
            localModelReady = { false },
            complete = { _: GenerateRequest ->
                calls++
                GenerateResult(content = """{"kind":"conversation"}""")
            }
        )

        val result = planner.plan("Could you please make it louder?", "")

        assertEquals(0, calls)
        assertEquals(
            PhoneActionPlanner.Result.Action(PhoneControlCommand.VolumeUp),
            result
        )
    }

    @Test fun `ambiguous local phone skill asks before acting`() = runBlocking {
        var calls = 0
        val planner = PhoneActionPlanner(
            localModelReady = { false },
            complete = { _: GenerateRequest ->
                calls++
                GenerateResult(content = """{"kind":"action","type":"volume_up"}""")
            }
        )

        val result = planner.plan("Make it louder or quieter", "")

        assertEquals(0, calls)
        assertTrue(result is PhoneActionPlanner.Result.Clarification)
        assertTrue((result as PhoneActionPlanner.Result.Clarification).question.contains("increase the volume"))
    }

    @Test fun `natural navigation phrases resolve through the local skill registry`() = runBlocking {
        var calls = 0
        val planner = PhoneActionPlanner(
            localModelReady = { false },
            complete = { _: GenerateRequest ->
                calls++
                GenerateResult(content = """{"kind":"conversation"}""")
            }
        )

        assertTrue(planner.shouldHandle("Take me to the home screen"))
        val result = planner.plan("Take me to the home screen", "")

        assertEquals(0, calls)
        assertEquals(PhoneActionPlanner.Result.Action(PhoneControlCommand.Home), result)
    }

    @Test fun `local phone skill registry can be extended without changing the planner`() = runBlocking {
        var calls = 0
        val customSkills = LocalPhoneActionSkillRegistry(
            listOf(
                PhoneActionSkill(
                    id = "custom.accessibility",
                    aliases = listOf("open accessibility panel"),
                    command = PhoneControlCommand.OpenSettings
                )
            )
        )
        val planner = PhoneActionPlanner(
            localModelReady = { false },
            localSkills = customSkills,
            complete = { _: GenerateRequest ->
                calls++
                GenerateResult(content = """{"kind":"conversation"}""")
            }
        )

        assertTrue(planner.shouldHandle("Could you open the accessibility panel?"))
        val result = planner.plan("Could you open the accessibility panel?", "")

        assertEquals(0, calls)
        assertEquals(
            PhoneActionPlanner.Result.Action(PhoneControlCommand.OpenSettings),
            result
        )
    }

    @Test fun `unsupported device request is not faked when no model-free skill exists`() = runBlocking {
        var calls = 0
        val planner = PhoneActionPlanner(
            localModelReady = { false },
            complete = { _: GenerateRequest ->
                calls++
                GenerateResult(content = """{"kind":"action"}""")
            }
        )

        val result = planner.plan("Turn on airplane mode", "")

        assertEquals(0, calls)
        assertTrue(result is PhoneActionPlanner.Result.Unavailable)
        assertTrue((result as PhoneActionPlanner.Result.Unavailable).reason.contains("Nothing was changed"))
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
        assertTrue(PhoneActionPlanner.isLikelyDeviceRequest("Could you please make it louder?"))
        assertFalse(PhoneActionPlanner.isLikelyDeviceRequest("Tell me what you think"))
    }
}

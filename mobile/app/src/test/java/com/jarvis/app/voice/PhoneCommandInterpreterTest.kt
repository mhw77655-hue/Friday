package com.jarvis.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class PhoneCommandInterpreterTest {
    @Test fun `recognizes global navigation commands`() {
        assertEquals(PhoneControlCommand.Back, PhoneCommandInterpreter.parse("go back"))
        assertEquals(PhoneControlCommand.Home, PhoneCommandInterpreter.parse("go home"))
        assertEquals(PhoneControlCommand.Notifications, PhoneCommandInterpreter.parse("show notifications"))
        assertEquals(PhoneControlCommand.QuickSettings, PhoneCommandInterpreter.parse("open quick settings"))
    }

    @Test fun `recognizes app and semantic screen actions`() {
        assertEquals(PhoneControlCommand.OpenApp("WhatsApp"), PhoneCommandInterpreter.parse("open WhatsApp"))
        assertEquals(PhoneControlCommand.TapText("Settings"), PhoneCommandInterpreter.parse("tap Settings"))
        assertEquals(PhoneControlCommand.TypeText("hello world"), PhoneCommandInterpreter.parse("type hello world"))
        assertEquals(PhoneControlCommand.ScrollForward, PhoneCommandInterpreter.parse("scroll down"))
    }

    @Test fun `recognizes common device-control voice commands`() {
        assertEquals(PhoneControlCommand.OpenSettings, PhoneCommandInterpreter.parse("open settings"))
        assertEquals(PhoneControlCommand.PlayMedia, PhoneCommandInterpreter.parse("play music"))
        assertEquals(PhoneControlCommand.PauseMedia, PhoneCommandInterpreter.parse("pause music"))
        assertEquals(PhoneControlCommand.NextTrack, PhoneCommandInterpreter.parse("next track"))
        assertEquals(PhoneControlCommand.PreviousTrack, PhoneCommandInterpreter.parse("previous song"))
        assertEquals(PhoneControlCommand.VolumeUp, PhoneCommandInterpreter.parse("increase volume"))
        assertEquals(PhoneControlCommand.VolumeDown, PhoneCommandInterpreter.parse("quieter"))
        assertEquals(PhoneControlCommand.SetMediaVolume(30), PhoneCommandInterpreter.parse("set media volume to 30 percent"))
        assertEquals(PhoneControlCommand.SetBrightness(45), PhoneCommandInterpreter.parse("set brightness to 45%"))
        assertNull(PhoneCommandInterpreter.parse("set brightness to 0 percent"))
        assertNull(PhoneCommandInterpreter.parse("set media volume to 101 percent"))
        assertEquals(PhoneControlCommand.OpenInternetPanel, PhoneCommandInterpreter.parse("open wifi settings"))
        assertEquals(PhoneControlCommand.OpenInternetPanel, PhoneCommandInterpreter.parse("turn off wifi"))
    }

    @Test fun `sensitive screen interactions are explicitly confirmation gated`() {
        assertTrue(PhoneCommandInterpreter.requiresExplicitConfirmation(
            PhoneCommandInterpreter.parse("tap Delete")!!
        ))
        assertTrue(PhoneCommandInterpreter.requiresExplicitConfirmation(
            PhoneCommandInterpreter.parse("type my password 1234")!!
        ))
        assertFalse(PhoneCommandInterpreter.requiresExplicitConfirmation(
            PhoneCommandInterpreter.parse("type hello")!!
        ))
        assertFalse(PhoneCommandInterpreter.requiresExplicitConfirmation(
            PhoneCommandInterpreter.parse("tap Settings")!!
        ))
        assertFalse(PhoneCommandInterpreter.requiresExplicitConfirmation(
            PhoneCommandInterpreter.parse("go home")!!
        ))
        assertEquals("high", PhoneCommandInterpreter.risk(PhoneCommandInterpreter.parse("tap Send")!!))
        assertEquals("medium", PhoneCommandInterpreter.risk(PhoneCommandInterpreter.parse("tap Settings")!!))
    }

    @Test fun `recognizes natural polite voice phrasing`() {
        assertEquals(PhoneControlCommand.Back, PhoneCommandInterpreter.parse("Hey Friday, could you take me back please?"))
        assertEquals(PhoneControlCommand.Home, PhoneCommandInterpreter.parse("Friday, please take me home."))
        assertEquals(PhoneControlCommand.OpenApp("Spotify"), PhoneCommandInterpreter.parse("Can you open Spotify please?"))
        assertEquals(PhoneControlCommand.TapText("Send"), PhoneCommandInterpreter.parse("Hey Friday, tap Send please"))
        assertEquals(PhoneControlCommand.TypeText("hello there"), PhoneCommandInterpreter.parse("Could you type hello there please?"))
    }

    @Test fun `compound app requests are deferred to the contextual Friday planner`() {
        val request = "open WhatsApp and send a message"
        assertNull(PhoneCommandInterpreter.parse(request))
        assertTrue(PhoneActionPlanner.isLikelyDeviceRequest(request))
    }

    @Test fun `does not guess complex or ambiguous commands`() {
        assertNull(PhoneCommandInterpreter.parse("send the message to my friend"))
        assertNull(PhoneCommandInterpreter.parse("do something"))
        assertNull(PhoneCommandInterpreter.parse(""))
    }
}

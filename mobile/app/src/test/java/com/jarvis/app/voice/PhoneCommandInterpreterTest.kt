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
        assertEquals(PhoneControlCommand.OpenInternetPanel, PhoneCommandInterpreter.parse("open wifi settings"))
        assertEquals(PhoneControlCommand.OpenInternetPanel, PhoneCommandInterpreter.parse("turn off wifi"))
    }

    @Test fun `sensitive screen interactions are explicitly confirmation gated`() {
        assertTrue(PhoneCommandInterpreter.requiresExplicitConfirmation(
            PhoneCommandInterpreter.parse("tap Delete")!!
        ))
        assertTrue(PhoneCommandInterpreter.requiresExplicitConfirmation(
            PhoneCommandInterpreter.parse("type hello")!!
        ))
        assertFalse(PhoneCommandInterpreter.requiresExplicitConfirmation(
            PhoneCommandInterpreter.parse("go home")!!
        ))
    }

    @Test fun `does not guess complex or ambiguous commands`() {
        assertNull(PhoneCommandInterpreter.parse("send the message to my friend"))
        assertNull(PhoneCommandInterpreter.parse("do something"))
        assertNull(PhoneCommandInterpreter.parse(""))
    }
}

package com.jarvis.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test fun `does not guess complex or ambiguous commands`() {
        assertNull(PhoneCommandInterpreter.parse("send the message to my friend"))
        assertNull(PhoneCommandInterpreter.parse("do something"))
        assertNull(PhoneCommandInterpreter.parse(""))
    }
}

package com.jarvis.app.vf

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnitType
import com.jarvis.app.vf.typography.TypographyEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TypographyEngine: line-heights must be absolute `sp`, never relative `em`.
 *
 * Historical bug: `DisplayXl` used `lineHeight = 56.em` at a 48sp font, which
 * resolved to ~2688sp — blowing up the wordmark layout. These tests pin the
 * line-heights to the 20_DESIGN_TOKENS.md table (display-xl 56px, display 40px,
 * heading 28px, label 16px, body 20px, caption 16px, data-mono 16px).
 */
class TypographyEngineTest {

    private fun assertAbsoluteLineHeight(style: TextStyle, name: String) {
        assertTrue(
            "$name lineHeight must be an absolute unit (sp), not em",
            style.lineHeight.type == TextUnitType.Sp
        )
    }

    @Test
    fun `all voice line-heights are sp, not em`() {
        assertAbsoluteLineHeight(TypographyEngine.DisplayXl, "DisplayXl")
        assertAbsoluteLineHeight(TypographyEngine.Display, "Display")
        assertAbsoluteLineHeight(TypographyEngine.Heading, "Heading")
        assertAbsoluteLineHeight(TypographyEngine.Body, "Body")
        assertAbsoluteLineHeight(TypographyEngine.Label, "Label")
        assertAbsoluteLineHeight(TypographyEngine.Caption, "Caption")
        assertAbsoluteLineHeight(TypographyEngine.DataMono, "DataMono")
    }

    @Test
    fun `line-height values match the design-token table`() {
        assertEquals(56f, TypographyEngine.DisplayXl.lineHeight.value, 0.01f)
        assertEquals(40f, TypographyEngine.Display.lineHeight.value, 0.01f)
        assertEquals(28f, TypographyEngine.Heading.lineHeight.value, 0.01f)
        assertEquals(20f, TypographyEngine.Body.lineHeight.value, 0.01f)
        assertEquals(16f, TypographyEngine.Label.lineHeight.value, 0.01f)
        assertEquals(16f, TypographyEngine.Caption.lineHeight.value, 0.01f)
        assertEquals(16f, TypographyEngine.DataMono.lineHeight.value, 0.01f)
    }
}

package com.jarvis.app.launchsurvival

import com.jarvis.app.body.LanguageRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LAUNCH-SURVIVAL AC1(a) and AC2: reproduce CRASH A before touching production
 * code, then pin the open-resolution contract.
 *
 * The phone crashed at `StreamingTts.synthesizeSegment(StreamingTts.kt:285)`
 * with `IllegalArgumentException: No enum constant
 * com.jarvis.app.body.LanguageRouter.Language.EN`. The segment's language is a
 * raw tag ("en", "ar"), but `Language.valueOf(...)` expects the ENUM name
 * ("ENGLISH"). Any tag that is not exactly an enum constant threw, and the
 * uncaught exception on the IO worker killed the process.
 *
 * These tests drive the REAL resolver the production speech path uses. On the
 * pre-fix code the resolver does not exist and `valueOf` is called directly,
 * so the suite fails by compile/absence of the seam; after the fix the same
 * suite must pass with a voice choice for every tag, and unknown tags must be
 * recorded as unknown rather than thrown.
 */
class LanguageTagResolutionTest {

    @Test
    fun `every external language tag resolves without throwing and ends with a voice choice`() {
        val tags = listOf("EN", "en", "en-US", "ar-EG", "", "xx-YY")

        for (tag in tags) {
            val reading = LanguageRouter.resolveLanguageTag(tag)
            assertTrue(
                "tag '$tag' must end with a voice choice, got ${reading.language}",
                reading.language == LanguageRouter.Language.ENGLISH ||
                    reading.language == LanguageRouter.Language.EGYPTIAN_ARABIC
            )
        }
    }

    @Test
    fun `a differently-cased, empty or unknown tag is recorded as unknown and routes to default`() {
        val unknown = listOf("xx-YY", "", "  ", "klingon")

        for (tag in unknown) {
            val reading = LanguageRouter.resolveLanguageTag(tag)
            assertTrue("'$tag' must be recorded as unknown", reading.unknown)
            assertEquals(
                "'$tag' must degrade to the default English voice",
                LanguageRouter.Language.ENGLISH,
                reading.language
            )
        }
    }

    @Test
    fun `known tags are not reported unknown`() {
        val known = listOf("EN", "en", "en-US", "ar", "AR-eg")

        for (tag in known) {
            val reading = LanguageRouter.resolveLanguageTag(tag)
            assertFalse("'$tag' is a real tag, not unknown", reading.unknown)
        }
    }

    @Test
    fun `resolution never throws for any single-character or punctuation tag`() {
        val hostile = listOf("z", "-", ":", "e", "a", "!", "0", "\n", "\t", "en_US")
        for (tag in hostile) {
            LanguageRouter.resolveLanguageTag(tag)
        }
    }
}

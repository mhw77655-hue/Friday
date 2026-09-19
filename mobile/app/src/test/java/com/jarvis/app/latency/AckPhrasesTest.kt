package com.jarvis.app.latency

import org.junit.Assert.assertEquals
import org.junit.Test

class AckPhrasesTest {

    @Test
    fun `valence maps to buckets`() {
        assertEquals(AckPhrases.Bucket.POSITIVE, AckPhrases.bucketFor(0.5))
        assertEquals(AckPhrases.Bucket.NEUTRAL, AckPhrases.bucketFor(0.0))
        assertEquals(AckPhrases.Bucket.LOW, AckPhrases.bucketFor(-0.5))
        assertEquals(AckPhrases.Bucket.NEUTRAL, AckPhrases.bucketFor(null))
    }

    @Test
    fun `rotation is deterministic and cycles within a bucket`() {
        val positive = AckPhrases.pool().getValue(AckPhrases.Bucket.POSITIVE)
        assertEquals(positive[0], AckPhrases.pick(AckPhrases.Bucket.POSITIVE, 0))
        assertEquals(positive[1], AckPhrases.pick(AckPhrases.Bucket.POSITIVE, 1))
        // Wraps instead of indexing out of range.
        assertEquals(positive[0], AckPhrases.pick(AckPhrases.Bucket.POSITIVE, positive.size))
        // Negative rotations (never produced, but safe) do not crash.
        assertEquals(positive[positive.size - 1], AckPhrases.pick(AckPhrases.Bucket.POSITIVE, -1))
    }
}

package ai.friday.assistant.model.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DownloadTransferPlannerTest {
    @Test
    fun `fresh full response starts at zero and truncates any stale partial`() {
        val plan = DownloadTransferPlanner.plan(
            responseCode = 200,
            requestedOffset = 0L,
            contentRange = null,
            responseBytes = 4096L
        )

        assertFalse(plan.append)
        assertEquals(0L, plan.startOffset)
        assertEquals(4096L, plan.totalBytes)
    }

    @Test
    fun `server ignoring Range safely restarts from byte zero`() {
        val plan = DownloadTransferPlanner.plan(
            responseCode = 200,
            requestedOffset = 2_097_152L,
            contentRange = null,
            responseBytes = 8_388_608L
        )

        assertFalse(plan.append)
        assertEquals(0L, plan.startOffset)
        assertEquals(8_388_608L, plan.totalBytes)
    }

    @Test
    fun `valid partial response appends only from the exact saved offset`() {
        val plan = DownloadTransferPlanner.plan(
            responseCode = 206,
            requestedOffset = 1_048_576L,
            contentRange = "bytes 1048576-2097151/4194304",
            responseBytes = 1_048_576L
        )

        assertTrue(plan.append)
        assertEquals(1_048_576L, plan.startOffset)
        assertEquals(4_194_304L, plan.totalBytes)
    }

    @Test
    fun `unknown total remains resumable when range start and segment are valid`() {
        val plan = DownloadTransferPlanner.plan(
            responseCode = 206,
            requestedOffset = 1024L,
            contentRange = "bytes 1024-2047/*",
            responseBytes = 1024L
        )

        assertTrue(plan.append)
        assertEquals(1024L, plan.startOffset)
        assertEquals(-1L, plan.totalBytes)
    }

    @Test
    fun `partial response without a requested range is rejected`() {
        assertRejected {
            DownloadTransferPlanner.plan(206, 0L, "bytes 0-99/100", 100L)
        }
    }

    @Test
    fun `partial response starting at a different offset is rejected`() {
        assertRejected {
            DownloadTransferPlanner.plan(206, 1024L, "bytes 0-1023/4096", 1024L)
        }
    }

    @Test
    fun `partial response with mismatched Content Length is rejected`() {
        assertRejected {
            DownloadTransferPlanner.plan(206, 1024L, "bytes 1024-2047/4096", 512L)
        }
    }

    @Test
    fun `malformed Content Range is rejected before appending`() {
        assertRejected {
            DownloadTransferPlanner.plan(206, 1024L, "bytes 1024 to 2047", 1024L)
        }
    }

    private fun assertRejected(block: () -> Unit) {
        try {
            block()
            fail("An invalid server response must not be appended to a model file.")
        } catch (_: IllegalArgumentException) {
            // expected
        } catch (_: IllegalStateException) {
            // expected
        }
    }
}

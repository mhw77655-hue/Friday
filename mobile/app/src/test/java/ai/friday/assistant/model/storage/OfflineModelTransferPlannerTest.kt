package ai.friday.assistant.model.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineModelTransferPlannerTest {
    @Test fun fullResponseRestartsInsteadOfAppending() {
        val plan = DownloadTransferPlanner.plan(
            responseCode = 200,
            requestedOffset = 512,
            contentRange = null,
            responseBytes = 2048
        )
        assertFalse(plan.append)
        assertEquals(0L, plan.startOffset)
        assertEquals(2048L, plan.totalBytes)
    }

    @Test fun validPartialResponseResumesAtExactOffset() {
        val plan = DownloadTransferPlanner.plan(
            responseCode = 206,
            requestedOffset = 1024,
            contentRange = "bytes 1024-2047/4096",
            responseBytes = 1024
        )
        assertTrue(plan.append)
        assertEquals(1024L, plan.startOffset)
        assertEquals(4096L, plan.totalBytes)
    }

    @Test fun partialResponseCannotSkipOrRepeatBytes() {
        assertThrows(IllegalArgumentException::class.java) {
            DownloadTransferPlanner.plan(
                responseCode = 206,
                requestedOffset = 1024,
                contentRange = "bytes 1025-2048/4096",
                responseBytes = 1024
            )
        }
    }

    @Test fun partialResponseMustMatchItsDeclaredLength() {
        assertThrows(IllegalArgumentException::class.java) {
            DownloadTransferPlanner.plan(
                responseCode = 206,
                requestedOffset = 1024,
                contentRange = "bytes 1024-2047/4096",
                responseBytes = 512
            )
        }
    }

    @Test fun partialResponseCannotExceedRemoteObject() {
        assertThrows(IllegalArgumentException::class.java) {
            DownloadTransferPlanner.plan(
                responseCode = 206,
                requestedOffset = 1024,
                contentRange = "bytes 1024-4096/4096",
                responseBytes = 3073
            )
        }
    }

    @Test fun serverCannotReturnUnsupportedTransferStatus() {
        assertThrows(IllegalArgumentException::class.java) {
            DownloadTransferPlanner.plan(
                responseCode = 416,
                requestedOffset = 1024,
                contentRange = null,
                responseBytes = 0
            )
        }
    }
}

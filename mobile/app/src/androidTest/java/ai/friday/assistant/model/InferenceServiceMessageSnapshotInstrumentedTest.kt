package ai.friday.assistant.model

import android.os.Bundle
import android.os.Message
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InferenceServiceMessageSnapshotInstrumentedTest {

    @Test
    fun snapshotSurvivesFrameworkMessageReuseAndBundleMutation() {
        val original = Message.obtain(null, InferenceService.MSG_GENERATE).apply {
            arg1 = 41
            data = Bundle().apply {
                putLong(InferenceService.KEY_HANDLE_ID, 73L)
                putString(InferenceService.KEY_PROMPT, "original prompt")
            }
        }

        val snapshot = InferenceService.snapshotMessage(original)

        // Model the framework recycling/reusing the Message after Handler returns.
        original.what = 999
        original.arg1 = 99
        original.data.putString(InferenceService.KEY_PROMPT, "recycled prompt")

        assertEquals(InferenceService.MSG_GENERATE, snapshot.operation)
        assertEquals(41, snapshot.requestId)
        assertEquals(73L, snapshot.data.getLong(InferenceService.KEY_HANDLE_ID))
        assertEquals("original prompt", snapshot.data.getString(InferenceService.KEY_PROMPT))
        assertNotSame(original.data, snapshot.data)
    }
}

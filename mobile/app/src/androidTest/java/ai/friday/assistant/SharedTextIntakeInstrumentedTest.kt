package ai.friday.assistant

import android.Manifest
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ai.friday.assistant.latency.LatencyLayer
import ai.friday.assistant.video.VideoIntakeRepository
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Exercises the owner-controlled phone -> Friday share handoff through the
 * actual exported launcher Activity and production conversation pipeline.
 */
@RunWith(AndroidJUnit4::class)
class SharedTextIntakeInstrumentedTest {

    @Test
    fun plainTextShareReachesTheLiveConversationInsteadOfVideoIntake() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        runCatching {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.RECORD_AUDIO
            )
        }

        val marker = "FRIDAY_SHARED_TEXT_ACCEPTANCE_" + UUID.randomUUID()
        val share = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, marker)
        }

        val scenario = ActivityScenario.launch<MainActivity>(share)
        try {
            val deadline = System.currentTimeMillis() + 20_000L
            var accepted = false
            while (System.currentTimeMillis() < deadline) {
                accepted = LatencyLayer.turns.value.any { turn ->
                    !turn.fromFriday && turn.text == marker
                }
                if (accepted) break
                Thread.sleep(50L)
            }

            assertTrue(
                "A plain-text Android share must enter Friday's live conversation pipeline.",
                accepted
            )
            assertTrue(
                "Ordinary shared text must not be saved as a video-library item.",
                VideoIntakeRepository(context).entries().none { it.title.contains(marker) }
            )
        } finally {
            scenario.close()
        }
    }
}

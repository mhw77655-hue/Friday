package ai.friday.assistant.github.reporting

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FridayAgentTaskRelayConsentStoreInstrumentedTest {
    @Test
    fun relayConsentDefaultsOffAndTrustedActorConfigurationPersists() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences(
            "friday_agent_task_relay_consent",
            Context.MODE_PRIVATE
        )
        preferences.edit().clear().commit()
        try {
            val store = FridayAgentTaskRelayConsentStore(context)
            assertFalse(store.isEnabled())
            assertEquals(setOf("mhw77655-hue"), store.trustedActors())

            store.setTrustedActors(setOf("mhw77655-hue", "friday-ci"))
            store.setEnabled(true)

            val reopened = FridayAgentTaskRelayConsentStore(context)
            assertTrue(reopened.isEnabled())
            assertEquals(setOf("mhw77655-hue", "friday-ci"), reopened.trustedActors())

            reopened.setEnabled(false)
            assertFalse(reopened.isEnabled())
        } finally {
            preferences.edit().clear().commit()
        }
    }
}

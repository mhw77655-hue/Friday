package ai.friday.assistant.diagnostics

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FridayInspectionRelayStorePrivacyInstrumentedTest {
    @Test
    fun pairingDoesNotEnableExternalInspectionAndOwnerCanPauseIt() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("friday_live_inspection", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            val store = FridayInspectionRelayStore(context)
            assertFalse("new installs must start with external inspection off", store.isEnabled())

            store.saveEnrollment("https://example.invalid")
            assertTrue("pairing metadata may be retained", store.isEnrolled())
            assertFalse("pairing must not silently enable a remote connection", store.isEnabled())

            store.setEnabled(true)
            assertTrue("only the explicit owner action enables the session", store.isEnabled())

            FridayInspectionRelayStore(context).setEnabled(false)
            assertFalse("pause must persist across store instances", FridayInspectionRelayStore(context).isEnabled())
            assertTrue("pausing does not require deleting the pairing", FridayInspectionRelayStore(context).isEnrolled())
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test
    fun privacyMigrationTurnsOffPreviouslyEnabledPairedInstall() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("friday_live_inspection", Context.MODE_PRIVATE)
        preferences.edit().clear()
            .putString("server_url", "https://example.invalid")
            .putBoolean("enrolled", true)
            .putBoolean("enabled", true)
            .commit()
        try {
            val migrated = FridayInspectionRelayStore(context)
            assertTrue("existing pairing is preserved", migrated.isEnrolled())
            assertFalse("old always-on preference is not silently carried forward", migrated.isEnabled())
        } finally {
            preferences.edit().clear().commit()
        }
    }
}

package ai.friday.assistant.diagnostics

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the same diagnostics composition used by the Settings live-test
 * export, on a real Android application process rather than a JVM fake.
 */
@RunWith(AndroidJUnit4::class)
class PhoneActionDiagnosticsInstrumentedTest {
    @Test
    fun liveTestReportContainsDeviceStateAndActionTimeline() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PhoneActionDiagnostics.install(context)
        PhoneActionDiagnostics.clear()

        try {
            PhoneActionDiagnostics.record(
                action = "OpenSettings",
                route = "accessibility",
                outcome = "success_reported",
                durationMs = 17,
                parentTraceId = "instrumented-parent-trace"
            )

            val report = PhoneActionDiagnostics.exportText(context)

            assertTrue(report.contains("FRIDAY LIVE TEST REPORT"))
            assertTrue(report.contains("app_version:"))
            assertTrue(report.contains("android_api: ${Build.VERSION.SDK_INT}"))
            assertTrue(report.contains("device:"))
            assertTrue(report.contains("android_release:"))
            assertTrue(report.contains("time_window:"))
            assertTrue(report.contains("DEVICE CAPABILITIES AT EXPORT"))
            assertTrue(report.contains("microphone_permission:"))
            assertTrue(report.contains("accessibility_service_enabled:"))
            assertTrue(report.contains("accessibility_service_connected:"))
            assertTrue(report.contains("write_settings_permission:"))
            assertTrue(report.contains("notification_listener_enabled:"))
            assertTrue(report.contains("shizuku_status:"))
            assertTrue(report.contains("LAST RECORDED CRASH"))
            assertTrue(report.contains("action=OpenSettings"))
            assertTrue(report.contains("route=accessibility"))
            assertTrue(report.contains("outcome=success_reported"))
            assertTrue(report.contains("parent_trace=instrumented-parent-trace"))
            assertTrue(report.contains("MACHINE_READABLE_JSON"))
        } finally {
            PhoneActionDiagnostics.clear()
        }
    }
}

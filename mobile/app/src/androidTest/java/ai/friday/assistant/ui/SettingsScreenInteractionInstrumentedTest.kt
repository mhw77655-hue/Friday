package ai.friday.assistant.ui

import ai.friday.assistant.ui.screens.SettingsScreen
import ai.friday.assistant.ui.theme.FridayTheme
import ai.friday.assistant.diagnostics.PhoneActionDiagnostics
import ai.friday.assistant.voice.JevApiKeyStore
import ai.friday.assistant.github.reporting.GitHubCredentialStore
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression coverage for the Settings surface that failed on the owner's
 * first physical install. These checks run the real Compose screen and verify
 * its primary controls expose executable click semantics, not just labels.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenInteractionInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun primarySettingsActionsExposeClickSemantics() {
        compose.setContent {
            FridayTheme {
                SettingsScreen()
            }
        }

        compose.waitForIdle()
        compose.onNodeWithText("Friday runtime and capabilities").performScrollTo().performClick()
        compose.onNodeWithText("Personalization and voice").performScrollTo().performClick()
        compose.onNodeWithText("Friday updates").performScrollTo().performClick()
        compose.onNodeWithText("Check for Friday updates").assertHasClickAction()
        compose.onNodeWithText("Open battery optimization settings").assertHasClickAction()
        compose.onNodeWithText("Remove key").assertHasClickAction()
    }

    @Test
    fun independentJevStoreInstancesCannotLeaveUndecryptableCredentialState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("jev_secure", Context.MODE_PRIVATE)
        val observer = JevApiKeyStore(context)
        observer.clear()

        repeat(30) { iteration ->
            val start = java.util.concurrent.CountDownLatch(1)
            val workers = java.util.concurrent.Executors.newFixedThreadPool(2)
            try {
                val save = workers.submit {
                    start.await()
                    JevApiKeyStore(context).save("friday-concurrent-fixture-$iteration")
                }
                val clear = workers.submit {
                    start.await()
                    JevApiKeyStore(context).clear()
                }
                start.countDown()
                save.get(10, java.util.concurrent.TimeUnit.SECONDS)
                clear.get(10, java.util.concurrent.TimeUnit.SECONDS)

                val ciphertextPresent = preferences.contains("api_ciphertext") ||
                    preferences.contains("api_iv")
                val readable = observer.read()
                assertTrue(
                    "A persisted encrypted credential must remain decryptable after concurrent save/clear; iteration=$iteration",
                    !ciphertextPresent || readable != null
                )
                assertTrue(
                    "Only a complete fixture credential may be readable; iteration=$iteration",
                    readable == null || readable == "friday-concurrent-fixture-$iteration"
                )
            } finally {
                workers.shutdownNow()
            }
            observer.clear()
        }
    }

    @Test
    fun independentGitHubStoreInstancesCannotLeaveUndecryptableCredentialState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("friday_github_secure", Context.MODE_PRIVATE)
        val observer = GitHubCredentialStore(context)
        observer.clear()

        repeat(30) { iteration ->
            val start = java.util.concurrent.CountDownLatch(1)
            val workers = java.util.concurrent.Executors.newFixedThreadPool(2)
            try {
                val token = "github_pat_friday_concurrency_fixture_$iteration"
                val save = workers.submit {
                    start.await()
                    GitHubCredentialStore(context).save(token)
                }
                val clear = workers.submit {
                    start.await()
                    GitHubCredentialStore(context).clear()
                }
                start.countDown()
                save.get(10, java.util.concurrent.TimeUnit.SECONDS)
                clear.get(10, java.util.concurrent.TimeUnit.SECONDS)

                val ciphertextPresent = preferences.contains("token_ciphertext") ||
                    preferences.contains("token_iv")
                val readable = observer.read()
                assertTrue(
                    "Persisted GitHub ciphertext must remain decryptable after concurrent save/clear; iteration=$iteration",
                    !ciphertextPresent || readable != null
                )
                assertTrue(
                    "Only the complete fixture token may be readable; iteration=$iteration",
                    readable == null || readable == token
                )
            } finally {
                workers.shutdownNow()
            }
            observer.clear()
        }
    }

    @Test
    fun clearLocalDiagnosticJournalCompletesAndReportsItsOutcome() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PhoneActionDiagnostics.install(context)
        PhoneActionDiagnostics.record(
            action = "SettingsJournalClearFixture",
            route = "instrumented_test",
            outcome = "success_reported",
            durationMs = 1
        )
        compose.waitUntil(5_000) { PhoneActionDiagnostics.eventCount() > 0 }

        compose.setContent {
            FridayTheme {
                SettingsScreen()
            }
        }

        compose.waitForIdle()
        compose.onNodeWithText("GitHub and advanced development").performScrollTo().performClick()
        compose.onNodeWithText("GitHub repository and device reporting").performScrollTo().performClick()
        // This control is far below the initial viewport in Settings. Scroll it
        // into the visible viewport before injecting the tap; otherwise the
        // test can target a clipped semantics node without exercising the UI.
        compose.onNodeWithText("Clear local journal").performScrollTo()
        compose.waitForIdle()
        compose.onNodeWithText("Clear local journal").assertHasClickAction().performClick()
        // The scroll action can leave the semantics node partially clipped on this
        // long Settings surface. Verify its action semantics and invoke it; the
        // durable terminal outcome below proves whether the real handler ran.
        // performClick synchronizes with Compose idleness; the IO clear can finish
        // before this call returns. Assert the durable terminal outcome below,
        // not a transient progress label that may correctly disappear quickly.
        compose.waitUntil(9_000) {
            compose.onAllNodesWithText("Local action journal cleared.", substring = true).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Could not clear the local journal.", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onRoot().printToLog("FRIDAY_JOURNAL_CLEAR_RESULT")
        assertTrue(
            "Settings must publish a successful journal-clear result.",
            compose.onAllNodesWithText("Local action journal cleared.", substring = true).fetchSemanticsNodes().isNotEmpty()
        )
        compose.waitUntil(9_000) { PhoneActionDiagnostics.eventCount() == 0 }
        val remaining = PhoneActionDiagnostics.eventCount()
        assertTrue("Clear action must empty the persisted journal; remaining=$remaining", remaining == 0)
        compose.onNodeWithText("Clear local journal").assertHasClickAction()
    }

    @Test
    fun keepFridayAliveTogglePersistsTheOwnerSetting() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // This test exercises the authorized owner-toggle path, not Android notification denial.
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val grant = instrumentation.uiAutomation.executeShellCommand(
            "pm grant --user 0 ${context.packageName} android.permission.POST_NOTIFICATIONS"
        )
        val grantOutput = android.os.ParcelFileDescriptor.AutoCloseInputStream(grant)
            .bufferedReader()
            .use { it.readText() }
        val notificationPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        )
        assertTrue(
            "The test fixture must grant POST_NOTIFICATIONS before exercising the owner toggle. " +
                "shellOutput=${grantOutput.take(300)} permissionResult=$notificationPermission",
            notificationPermission == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
        val prefs = context.getSharedPreferences("friday_runtime", Context.MODE_PRIVATE)
        // Stop any foreground service left by an earlier instrumentation case,
        // then synchronously reset only the owner-controlled preference before
        // Settings reads its initial snapshot.
        context.stopService(
            android.content.Intent(
                context,
                ai.friday.assistant.runtime.FridayRuntimeService::class.java
            )
        )
        prefs.edit()
            .putBoolean("keep_alive_enabled", false)
            .putBoolean("microphone_service_active", false)
            .remove("last_start_failure")
            .commit()
        assertTrue(
            "The runtime toggle fixture must start disabled.",
            !ai.friday.assistant.runtime.FridayRuntimeController.isEnabled(context)
        )

        compose.setContent {
            FridayTheme {
                SettingsScreen()
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Friday runtime and capabilities").performScrollTo().performClick()
        compose.onNodeWithTag("friday_runtime_toggle").performScrollTo()
        compose.waitForIdle()
        compose.onNodeWithTag("friday_runtime_toggle")
            .assertIsDisplayed()
            .assertIsOff()
            .performClick()
        val enabledPersisted = runCatching {
            compose.waitUntil(10_000) { prefs.getBoolean("keep_alive_enabled", false) }
        }.isSuccess
        assertTrue(
            "Enabling Keep Friday alive must persist the owner setting even if Android blocks service startup. " +
                "notificationPermission=${androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)} " +
                "controllerEnabled=${ai.friday.assistant.runtime.FridayRuntimeController.isEnabled(context)} " +
                "lastStartFailure=${ai.friday.assistant.runtime.FridayRuntimeController.lastStartFailure(context)}",
            enabledPersisted && prefs.getBoolean("keep_alive_enabled", false)
        )

        // Re-query after recomposition and assert the actual switch state before
        // toggling back. This prevents a stale semantics handle from making the
        // second click a no-op on slower/emulator-backed Compose test runners.
        compose.onNodeWithTag("friday_runtime_toggle")
            .assertIsOn()
            .performClick()
        // The second transition crosses the foreground-service stop path. On
        // a slow emulator, the preference commit and Compose semantics update
        // are not guaranteed to be observable in the same frame as performClick.
        // Wait for the durable owner preference first, then for the rendered
        // switch state; never turn a scheduling delay into a false UI failure.
        compose.waitUntil(10_000) { !prefs.getBoolean("keep_alive_enabled", true) }
        compose.waitUntil(5_000) {
            runCatching {
                compose.onNodeWithTag("friday_runtime_toggle").assertIsOff()
                true
            }.getOrDefault(false)
        }
        compose.onNodeWithTag("friday_runtime_toggle").assertIsOff()
        assertTrue(
            "Disabling Keep Friday alive must persist the owner setting.",
            !prefs.getBoolean("keep_alive_enabled", true)
        )
    }

    @Test
    fun shizukuConnectionCheckIsAnExecutableAction() {
        compose.setContent {
            FridayTheme {
                SettingsScreen()
            }
        }

        compose.waitForIdle()
        compose.onNodeWithText("Friday runtime and capabilities").performScrollTo().performClick()
        compose.onNodeWithText("Check Shizuku connection").performScrollTo()
        compose.onNodeWithText("Check Shizuku connection").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Shizuku is not running. Friday remains operational without it.", substring = true)
                .fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Shizuku is running. Friday refreshed its binder availability.", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Shizuku connection check failed:", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
        }
        val reported = compose.onAllNodesWithText("Shizuku is not running.", substring = true)
            .fetchSemanticsNodes().isNotEmpty() ||
            compose.onAllNodesWithText("Shizuku is running.", substring = true)
                .fetchSemanticsNodes().isNotEmpty() ||
            compose.onAllNodesWithText("Shizuku connection check failed:", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        assertTrue("Shizuku check must publish an explicit outcome.", reported)
    }

    @Test
    fun removeCredentialControlExecutesWithoutCrashingSettings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = JevApiKeyStore(context)
        store.save("friday-instrumented-removal-fixture")
        assertTrue("Credential fixture must be readable before removal.", store.read() == "friday-instrumented-removal-fixture")

        compose.setContent {
            FridayTheme {
                SettingsScreen()
            }
        }

        compose.waitForIdle()
        // Jev controls live inside the collapsed personalization group.
        compose.onNodeWithText("Personalization and voice").performScrollTo().performClick()
        compose.onNodeWithText("Remove key").performScrollTo()
        compose.waitForIdle()
        compose.onNodeWithText("Remove key").assertIsDisplayed().performClick()
        // The removal may complete before performClick returns. Verify the
        // terminal UI state and encrypted-store contents below instead of
        // requiring a transient progress label to remain on screen.
        compose.waitUntil(9_000) {
            compose.onAllNodesWithText(
                "Jev credential removed. Friday continues with local deterministic command handling.",
                substring = true
            ).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Jev credential could not be removed:", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onRoot().printToLog("FRIDAY_JEV_REMOVE_RESULT")
        assertTrue(
            "Settings must publish a successful credential-removal result.",
            compose.onAllNodesWithText(
                "Jev credential removed. Friday continues with local deterministic command handling.",
                substring = true
            ).fetchSemanticsNodes().isNotEmpty()
        )
        compose.waitUntil(9_000) { store.read() == null }

        assertTrue("Remove key must erase the encrypted credential.", store.read() == null)
        compose.onNodeWithText("Remove key").assertHasClickAction()
    }

    @Test
    fun settingsActionsBeyondExportExposeClickSemantics() {
        compose.setContent {
            FridayTheme {
                SettingsScreen()
            }
        }

        compose.waitForIdle()
        compose.onNodeWithText("Friday runtime and capabilities").performScrollTo().performClick()
        compose.onNodeWithText("Personalization and voice").performScrollTo().performClick()
        compose.onNodeWithText("GitHub and advanced development").performScrollTo().performClick()
        compose.onNodeWithText("GitHub repository and device reporting").performScrollTo().performClick()
        compose.onNodeWithText("Friday updates").performScrollTo().performClick()
        compose.onNodeWithText("Check for Friday updates").assertHasClickAction()
        compose.onNodeWithText("Open battery optimization settings").assertHasClickAction()
        compose.onNodeWithText("Create fine-grained GitHub token").assertHasClickAction()
        compose.onNodeWithText("Clear local journal").assertHasClickAction()
        compose.onNodeWithText("Remove key").assertHasClickAction()
    }
}

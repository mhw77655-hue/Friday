package ai.friday.assistant.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONArray
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class FridayUpdateStateInstrumentedTest {
    @Test
    fun verifiedStagedUpdateSurvivesStateStoreRecreation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = FridayUpdateStateStore(context)
        store.clearStaged()
        store.clearPendingHealthCheck()
        store.clearFailedRecoveryEvidence()
        val staging = File(context.noBackupFilesDir, "friday-update-staging").apply { mkdirs() }
        val apk = File(staging, "instrumented-update.apk").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val release = FridayUpdateClient.Release(
            packageId = context.packageName,
            versionCode = 42L,
            versionName = "1.0.42",
            sourceCommit = "a".repeat(40),
            certificateSha256 = "B".repeat(64),
            apkSha256 = "c".repeat(64),
            apkUrl = "https://example.invalid/friday.apk"
        )

        try {
            store.saveStaged(FridayUpdateClient.Result.Staged(release, apk))
            val restored = FridayUpdateStateStore(context).loadStaged()
            assertNotNull("Staged update metadata must survive store recreation.", restored)
            assertEquals(42L, restored!!.release.versionCode)
            assertEquals("1.0.42", restored.release.versionName)
            assertEquals(apk.canonicalPath, restored.apk.canonicalPath)
        } finally {
            store.clearStaged()
            store.clearPendingHealthCheck()
            store.clearFailedRecoveryEvidence()
        }
        assertFalse("Clearing staged state also removes the staged APK.", apk.exists())
    }

    @Test
    fun installerRejectsApkOutsidePrivateStagingDirectory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val outside = File(context.cacheDir, "untrusted-friday.apk").apply {
            writeBytes(byteArrayOf(9, 8, 7, 6))
        }
        val staged = FridayUpdateClient.Result.Staged(
            FridayUpdateClient.Release(
                packageId = context.packageName,
                versionCode = 43L,
                versionName = "1.0.43",
                sourceCommit = "b".repeat(40),
                certificateSha256 = "C".repeat(64),
                apkSha256 = "d".repeat(64),
                apkUrl = "https://example.invalid/friday.apk"
            ),
            outside
        )
        try {
            val result = FridayPackageInstaller(context).install(staged)
            assertTrue(
                "Only Friday's private staged artifact may reach PackageInstaller.",
                result is FridayPackageInstaller.Result.Rejected
            )
        } finally {
            outside.delete()
        }
    }
    @Test
    fun releaseAssetResolverAcceptsGitHubDisplayLabelAndRequiresHttps() {
        val assets = JSONArray().put(
            org.json.JSONObject()
                .put("name", "app-release.apk")
                .put("label", "friday.apk")
                .put("browser_download_url", "https://github.com/example/friday.apk")
        )
        assertEquals(
            "https://github.com/example/friday.apk",
            FridayUpdateClient.findReleaseAssetUrl(assets, "friday.apk")
        )

        val insecure = JSONArray().put(
            org.json.JSONObject()
                .put("name", "friday.apk")
                .put("browser_download_url", "http://example.invalid/friday.apk")
        )
        assertEquals(null, FridayUpdateClient.findReleaseAssetUrl(insecure, "friday.apk"))
    }

    @Test
    fun failedNativePostInstallVerificationArchivesEvidenceForForwardRecovery() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            android.content.pm.PackageManager.PackageInfoFlags.of(
                android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES.toLong()
            )
        )
        val store = FridayUpdateStateStore(context)
        store.clearStaged()
        store.clearPendingHealthCheck()
        store.clearFailedRecoveryEvidence()
        val staging = File(context.noBackupFilesDir, "friday-update-staging").apply { mkdirs() }
        val apk = File(staging, "failed-native-update.apk").apply {
            writeBytes(byteArrayOf(1, 3, 3, 7))
        }
        val deliberatelyWrongRelease = FridayUpdateClient.Release(
            packageId = context.packageName,
            versionCode = info.longVersionCode + 1L,
            versionName = "deliberately-wrong-version",
            sourceCommit = "d".repeat(40),
            certificateSha256 = "E".repeat(64),
            apkSha256 = "f".repeat(64),
            apkUrl = "https://example.invalid/friday.apk"
        )
        try {
            store.saveStaged(FridayUpdateClient.Result.Staged(deliberatelyWrongRelease, apk))

            val first = completeNativeInstallVerification(store)

            assertTrue(first.contains("Post-update health check failed"))
            assertTrue(
                "The failed APK must be preserved as immutable recovery evidence.",
                store.loadFailedArtifacts().any { it.apk?.canonicalPath == apk.canonicalPath }
            )
            assertTrue(
                "A failed release must remain on disk after first-launch verification.",
                apk.exists()
            )
            assertTrue(
                "A later up-to-date poll must not erase failed-release evidence.",
                !store.clearStagedUnlessRecoveryRequired()
            )
            assertTrue(
                "Routine cleanup must not delete the failed APK.",
                apk.exists()
            )
            assertTrue(
                "The failure outcome remains durable after the one-shot pending marker is archived.",
                store.lastStatus().startsWith("Post-update health check failed:")
            )
        } finally {
            store.clearStaged()
            store.clearPendingHealthCheck()
            store.clearFailedRecoveryEvidence()
        }
    }

    @Test
    fun newerReleaseCanStageWithoutReplacingFailedRecoveryEvidence() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            android.content.pm.PackageManager.PackageInfoFlags.of(
                android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES.toLong()
            )
        )
        val store = FridayUpdateStateStore(context)
        store.clearStaged()
        store.clearPendingHealthCheck()
        store.clearFailedRecoveryEvidence()
        val staging = File(context.noBackupFilesDir, "friday-update-staging").apply { mkdirs() }
        val failedApk = File(staging, "failed-recovery-update.apk").apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val sameVersionApk = File(staging, "same-version-candidate.apk").apply {
            writeBytes(byteArrayOf(7, 7, 7))
        }
        val newerApk = File(staging, "newer-candidate.apk").apply {
            writeBytes(byteArrayOf(4, 5, 6))
        }
        val failedRelease = FridayUpdateClient.Release(
            packageId = context.packageName,
            versionCode = info.longVersionCode + 1L,
            versionName = "failed-update",
            sourceCommit = "a".repeat(40),
            certificateSha256 = "B".repeat(64),
            apkSha256 = "c".repeat(64),
            apkUrl = "https://example.invalid/failed.apk"
        )
        val newerRelease = FridayUpdateClient.Release(
            packageId = context.packageName,
            versionCode = info.longVersionCode + 2L,
            versionName = "newer-candidate",
            sourceCommit = "d".repeat(40),
            certificateSha256 = "E".repeat(64),
            apkSha256 = "f".repeat(64),
            apkUrl = "https://example.invalid/newer.apk"
        )
        try {
            store.saveStaged(FridayUpdateClient.Result.Staged(failedRelease, failedApk))
            assertTrue(store.recordPendingHealthCheck(failedRelease))

            // The helper archives the failed APK. The same failed version is
            // rejected, while a strictly newer signed release may recover.
            val sameVersionRelease = failedRelease.copy(apkUrl = "https://example.invalid/retry.apk")
            assertFalse(
                "The same failed release must not be offered repeatedly as its own repair.",
                store.saveStagedUnlessRecoveryRequired(
                    FridayUpdateClient.Result.Staged(sameVersionRelease, sameVersionApk)
                )
            )
            sameVersionApk.delete()
            assertFalse(
                "The failed version cannot be recorded for another install attempt.",
                store.recordPendingHealthCheck(failedRelease)
            )
            val accepted = store.saveStagedUnlessRecoveryRequired(
                FridayUpdateClient.Result.Staged(newerRelease, newerApk)
            )

            assertTrue(
                "A strictly newer signed release must be stageable as forward recovery.",
                accepted
            )
            assertEquals(
                "The newer release becomes the active install candidate.",
                newerApk.canonicalPath,
                store.loadStaged()?.apk?.canonicalPath
            )
            assertTrue(
                "The original failed APK remains separately available as evidence.",
                store.loadFailedArtifacts().any { it.apk?.canonicalPath == failedApk.canonicalPath }
            )
            assertTrue("The original failed APK must remain on disk.", failedApk.exists())
            assertTrue("The newer recovery candidate must remain on disk.", newerApk.exists())
            assertTrue(
                "The previous failed-release record remains until the repair passes health verification.",
                store.hasFailedRecoveryEvidence()
            )
        } finally {
            store.clearStaged()
            store.clearPendingHealthCheck()
            store.clearFailedRecoveryEvidence()
            newerApk.delete()
            sameVersionApk.delete()
        }
    }

    @Test
    fun damagedFailureLedgerStillBlocksTheFailedVersionButAllowsANewerRepair() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            android.content.pm.PackageManager.PackageInfoFlags.of(
                android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES.toLong()
            )
        )
        val store = FridayUpdateStateStore(context)
        store.clearStaged()
        store.clearPendingHealthCheck()
        store.clearFailedRecoveryEvidence()
        val failedVersion = info.longVersionCode + 1L
        val prefs = context.getSharedPreferences("friday_update_state", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("failed_artifacts_json", "{damaged")
            .putLong("highest_failed_version_code", failedVersion)
            .commit()
        val staging = File(context.noBackupFilesDir, "friday-update-staging").apply { mkdirs() }
        val sameVersionApk = File(staging, "damaged-ledger-same-version.apk").apply {
            writeBytes(byteArrayOf(1, 1, 1))
        }
        val newerApk = File(staging, "damaged-ledger-newer.apk").apply {
            writeBytes(byteArrayOf(2, 2, 2))
        }
        fun release(version: Long, name: String) = FridayUpdateClient.Release(
            packageId = context.packageName,
            versionCode = version,
            versionName = name,
            sourceCommit = "a".repeat(40),
            certificateSha256 = "B".repeat(64),
            apkSha256 = "c".repeat(64),
            apkUrl = "https://example.invalid/$name.apk"
        )
        try {
            assertTrue(
                "A damaged but non-empty failed-release ledger must fail closed.",
                store.hasFailedRecoveryEvidence()
            )
            assertFalse(
                "A damaged ledger must still block retrying the failed version.",
                store.saveStagedUnlessRecoveryRequired(
                    FridayUpdateClient.Result.Staged(release(failedVersion, "failed"), sameVersionApk)
                )
            )
            assertTrue(
                "A higher-version signed repair may still be staged despite damaged detailed evidence.",
                store.saveStagedUnlessRecoveryRequired(
                    FridayUpdateClient.Result.Staged(release(failedVersion + 1L, "repair"), newerApk)
                )
            )
            assertEquals(newerApk.canonicalPath, store.loadStaged()?.apk?.canonicalPath)
        } finally {
            store.clearStaged()
            store.clearPendingHealthCheck()
            store.clearFailedRecoveryEvidence()
            sameVersionApk.delete()
            newerApk.delete()
        }
    }

    @Test
    fun postInstallHealthCheckConfirmsCurrentVersionSourceAndSigner() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val flags = android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            android.content.pm.PackageManager.PackageInfoFlags.of(flags.toLong())
        )
        val signer = info.signingInfo!!.apkContentsSigners.single()
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(signer.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .uppercase()
        val release = FridayUpdateClient.Release(
            packageId = context.packageName,
            versionCode = info.longVersionCode,
            versionName = info.versionName!!,
            sourceCommit = ai.friday.assistant.BuildConfig.SOURCE_REVISION,
            certificateSha256 = fingerprint,
            apkSha256 = "0".repeat(64),
            apkUrl = "https://example.invalid/friday.apk"
        )
        val store = FridayUpdateStateStore(context)
        store.clearStaged()
        store.clearPendingHealthCheck()
        store.clearFailedRecoveryEvidence()
        store.recordPendingHealthCheck(release)

        val status = store.verifyPendingHealthCheck()

        assertTrue(status.orEmpty().contains("Post-update health check passed"))
        assertTrue(store.lastStatus().contains("Post-update health check passed"))
        assertFalse("A healthy release leaves no failed-recovery evidence.", store.hasFailedRecoveryEvidence())
    }

}

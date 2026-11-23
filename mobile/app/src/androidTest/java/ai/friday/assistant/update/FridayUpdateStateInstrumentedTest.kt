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
    fun failedPostInstallHealthCheckRetainsRecoveryMarker() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            android.content.pm.PackageManager.PackageInfoFlags.of(
                android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES.toLong()
            )
        )
        val store = FridayUpdateStateStore(context)
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
            store.recordPendingHealthCheck(deliberatelyWrongRelease)

            val first = store.verifyPendingHealthCheck()
            val second = store.verifyPendingHealthCheck()

            assertTrue(first.orEmpty().contains("Post-update health check failed"))
            assertTrue(
                "A failed update must retain its recovery marker instead of erasing the failure on first launch.",
                second.orEmpty().contains("Post-update health check failed")
            )
        } finally {
            store.clearPendingHealthCheck()
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
        store.recordPendingHealthCheck(release)

        val status = store.verifyPendingHealthCheck()

        assertTrue(status.orEmpty().contains("Post-update health check passed"))
        assertTrue(store.lastStatus().contains("Post-update health check passed"))
    }

}

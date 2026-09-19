package com.jarvis.app.identity.owner

import com.jarvis.app.humancore.HumanCore
import com.jarvis.app.humancore.HumanCoreGraph
import com.jarvis.app.humancore.protocol.OwnerBinding
import com.jarvis.app.humancore.store.FileStorage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PEOPLE-VOICE-FACE-MEMORY-OWNER-BINDING — the strict owner chain.
 *
 * AC1 (audited, asserted here): before this story the Human Core had NO owner
 * identity / owner relationship-ID / biometric chain — only JARVIS's own
 * identity (IdentityKernel, admin-only revisions) and a single relationship
 * record with no ID. The new chain is [OwnerBiometricBinder] →
 * [OwnerBindingPort] → the real Human Core relationship store, gated solely on
 * the Android `BiometricPrompt` authorization result.
 *
 * AC2: the typed API consumes a real Android biometric authorization result
 * ([BiometricAuthResult.Success], produced on-device from
 * `BiometricPrompt.AuthenticationResult` by
 * [PlatformBiometricPromptResultMapper]) and binds it to the real
 * [OwnerBinding.OWNER_RELATIONSHIP_ID].
 *
 * AC3: owner authority holds independent of which physical device is used —
 * the binding keys on owner identity + relationship id, never on device
 * possession.
 *
 * AC4: NO voice/face embedding recognizer, model, or download is introduced
 * (negative grep over the shipped sources).
 */
class OwnerBiometricBindingTest {

    private class Device(
        dir: File,
        private val clockMs: () -> Long
    ) : OwnerBindingPort {
        val graph: HumanCoreGraph = HumanCoreGraph(
            storage = FileStorage(dir),
            clock = clockMs
        ).also { it.registry.loadAll() }

        override fun bindOwner(binding: OwnerBinding): Boolean = graph.bindOwner(binding)
        override fun ownerBinding(): OwnerBinding? = graph.ownerBinding()
    }

    private var nextTs = 1_700_000_000_000L

    private fun clock() = { nextTs++ }

    private fun freshDir(name: String): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "owner-bind-$name-${System.nanoTime()}")
        dir.mkdirs()
        return dir
    }

    private fun success(ts: Long = nextTs) =
        BiometricAuthResult.Success(
            authenticators = BiometricType.BIOMETRIC_STRONG,
            authenticatedAtEpochMs = ts
        )

    // ── AC2: typed API consumes the auth result and binds the real owner relationship ──

    @Test
    fun `biometric success binds the real owner relationship`() {
        val binder = OwnerBiometricBinder(Device(freshDir("a"), clock()))
        val outcome = binder.bind(success())

        assertEquals(OwnerBindingOutcome.Bound::class.java, outcome.javaClass)
        outcome as OwnerBindingOutcome.Bound
        assertEquals(OwnerBinding.OWNER_RELATIONSHIP_ID, outcome.binding.relationshipId)
        assertEquals("Venon", outcome.binding.ownerKey)
        assertEquals("biometric-strong", outcome.binding.authenticator)
        assertTrue("bound owner relationship is exposed by the binder", binder.isOwnerBound())
    }

    @Test
    fun `error and cancelled results never bind the owner relationship`() {
        val binder = OwnerBiometricBinder(Device(freshDir("err"), clock()))

        val cancelled = binder.bind(BiometricAuthResult.Cancelled)
        assertEquals(OwnerBindingOutcome.Cancelled::class.java, cancelled.javaClass)
        assertNull("cancelled never writes an owner binding", binder.currentBinding())

        val error = binder.bind(BiometricAuthResult.Error(errorCode = 13, errorMessage = "hardware unavailable"))
        assertEquals(OwnerBindingOutcome.NotAuthorized::class.java, error.javaClass)
        error as OwnerBindingOutcome.NotAuthorized
        assertEquals(13, error.errorCode)
        assertNull("failed authorization never writes an owner binding", binder.currentBinding())
        assertFalse(binder.isOwnerBound())
    }

    @Test
    fun `same owner re-authenticating refreshes the binding`() {
        val binder = OwnerBiometricBinder(Device(freshDir("reauth"), clock()))
        binder.bind(success())
        val second = binder.bind(success(nextTs + 1))

        assertEquals(OwnerBindingOutcome.ReAuthenticated::class.java, second.javaClass)
        second as OwnerBindingOutcome.ReAuthenticated
        assertEquals("Venon", second.binding.ownerKey)
        assertNotNull(binder.currentBinding())
    }

    @Test
    fun `a different owner identity is refused by the single-owner relationship`() {
        val device = Device(freshDir("conflict"), clock())
        val binder = OwnerBiometricBinder(device, ownerKey = "Venon")
        binder.bind(success())

        val impostor = OwnerBiometricBinder(device, ownerKey = "Mallory")
        val outcome = impostor.bind(success())
        assertEquals(OwnerBindingOutcome.Conflict::class.java, outcome.javaClass)
        outcome as OwnerBindingOutcome.Conflict
        assertEquals("incumbent binding survives the refused attempt", "Venon", outcome.existingOwner?.ownerKey)
        assertEquals(
            "the stored relationship is still bound to the real owner",
            "Venon", impostor.currentBinding()?.ownerKey
        )
    }

    @Test
    fun `owner binding persists into the real HumanCore relationship store`() {
        val dir = freshDir("durable")
        var ts = nextTs
        val device = Device(dir) { ++ts }

        OwnerBiometricBinder(device).bind(success())
        assertNotNull(device.graph.ownerBinding())

        // A brand-new graph over the SAME persisted data (a fresh process/device
        // that has received the relationship data) sees the owner binding.
        val reloaded = HumanCoreGraph(storage = FileStorage(dir), clock = { ++ts })
        reloaded.registry.loadAll()
        val bound = reloaded.ownerBinding()
        assertNotNull("binding is durable persisted data, not in-memory state", bound)
        assertEquals("owner", bound?.relationshipId)
        assertEquals("Venon", bound?.ownerKey)
    }

    @Test
    fun `production port binds through the real HumanCore singleton facade`() {
        if (!HumanCore.isInitialized()) {
            HumanCore.init(storage = FileStorage(freshDir("hc-port")))
        }
        val port = HumanCoreOwnerBindingPort()
        val outcome = OwnerBiometricBinder(port).bind(success())

        // The HumanCore singleton is process-global: another test class in the
        // same fork (e.g. PhaseBDisconnectedSubsystemWiringTest) may already have
        // bound the real owner through the same facade, in which case bind()
        // legitimately reports ReAuthenticated (same-owner refresh), not Bound.
        // Accept either — the proof is that the real singleton facade reports the
        // owner bound with the real relationship id, not the fresh-vs-refresh label.
        assertTrue(
            "production bind reaches the real facade (fresh bind or same-owner refresh)",
            outcome is OwnerBindingOutcome.Bound || outcome is OwnerBindingOutcome.ReAuthenticated
        )
        assertTrue("real HumanCore facade reports the owner bound", HumanCore.isOwnerBound())
        assertEquals("owner", HumanCore.ownerBinding()?.relationshipId)
    }

    // ── AC3: authority is the biometric result, not device possession ──

    @Test
    fun `owner authority binds on any freshly-initialized device with no shared state`() {
        // Two independent graphs with separate storage = two physical devices
        // that have never exchanged data. The biometric result alone is the
        // authority signal on BOTH.
        val deviceA = Device(freshDir("dev-a"), clock())
        val deviceB = Device(freshDir("dev-b"), clock())

        val a = OwnerBiometricBinder(deviceA).bind(success())
        assertEquals(OwnerBindingOutcome.Bound::class.java, a.javaClass)

        val b = OwnerBiometricBinder(deviceB).bind(success())
        assertEquals(
            "a biometric result binds the owner on device B with zero cross-device state",
            OwnerBindingOutcome.Bound::class.java, b.javaClass
        )
        assertEquals("owner", deviceA.graph.ownerBinding()?.relationshipId)
        assertEquals("owner", deviceB.graph.ownerBinding()?.relationshipId)

        // A failed authorization on device B changes nothing.
        val bErr = OwnerBiometricBinder(deviceB).bind(BiometricAuthResult.Error(4, "no match"))
        assertEquals(OwnerBindingOutcome.NotAuthorized::class.java, bErr.javaClass)
    }

    @Test
    fun `the authority signal carries no device identity and no biometric payload`() {
        // The Success model is declared to be exactly (authenticator class,
        // timestamp): everything that could tie ownership to a physical device
        // (deviceId, serial, androidId, IMEI) or to raw biometrics is absent
        // by construction. Asserted against the shipped source.
        val src = File("src/main/java/com/jarvis/app/identity/owner/BiometricAuthResult.kt").readText()
        val successFields = src.substringAfter("data class Success(").substringBefore(") : BiometricAuthResult()")
        assertTrue("carries authenticator class", successFields.contains("authenticators"))
        assertTrue("carries a timestamp", successFields.contains("authenticatedAtEpochMs"))
        for (forbidden in listOf("deviceId", "serial", "androidId", "imei", "Imei", "fingerprint", "faceTemplate")) {
            assertFalse("no $forbidden in the authority model", successFields.contains(forbidden))
        }
    }

    // ── AC4: no voice/face embedding recognizer, download, or other-person logic ──

    @Test
    fun `no voice or face embedding model recognizer or download is introduced`() {
        val shipped = listOf(
            "src/main/java/com/jarvis/app/identity/owner/BiometricType.kt",
            "src/main/java/com/jarvis/app/identity/owner/BiometricAuthResult.kt",
            "src/main/java/com/jarvis/app/identity/owner/PlatformBiometricPromptResultMapper.kt",
            "src/main/java/com/jarvis/app/identity/owner/OwnerBiometricBinder.kt",
            "src/main/java/com/jarvis/app/identity/owner/OwnerBindingPort.kt",
            "src/main/java/com/jarvis/app/identity/owner/HumanCoreOwnerBindingPort.kt",
            "src/main/java/com/jarvis/app/humancore/protocol/OwnerBinding.kt",
            "src/main/java/com/jarvis/app/humancore/store/RelationshipStore.kt"
        )
        val banned = listOf("embedding", "Embedding", "Recognizer", "recognizer", "download", "Download", "SpeechRecognizer")
        val hits = mutableListOf<String>()
        for (path in shipped) {
            val text = File(path).readText()
            for (token in banned) {
                if (text.contains(token)) hits.add("$path:$token")
            }
        }
        assertTrue("banned other-person recognition tokens found: $hits", hits.isEmpty())
    }
}
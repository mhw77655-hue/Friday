package com.jarvis.app.buildtwin

/**
 * BUILD-TWIN-ARM64-VERIFICATION — the twin definition.
 *
 * A [BuildTwinSpec] describes the throwaway ARM64-native, CPU-only, RAM-capped
 * (6-8GB) cloud instance that mirrors the Realme 9 Pro 5G's real runtime
 * constraints (arm64-v8a ABI, CPU-only inference, ~8GB ceiling). The spec is
 * validated at construction so a looser environment can never slip through:
 * a pass on the twin has to be a real predictor of a pass on the device.
 *
 * The real provisioning that produces such an instance is
 * `.ralph/buildtwin/provision.sh` (AWS Graviton allow-list, recorded in
 * `.ralph/buildtwin/state.json`); teardown.sh terminates it — no always-on
 * instance. This spec is the Kotlin-side contract those scripts satisfy.
 */
data class BuildTwinSpec(
    /** Twin address, e.g. the AWS public DNS/IP, or "127.0.0.1" for a local twin. */
    val host: String,
    /** Must be "arm64" — ARM64-native, never x86-with-emulation. */
    val arch: String = "arm64",
    /** Must be true — CPU-only, mirroring the device (the "g" Graviton family has no GPU). */
    val cpuOnly: Boolean = true,
    /** RAM ceiling in GiB, must be the 6-8 device-mirroring band. */
    val ramCapGb: Int = 8,
    /** Twin-side voiceforge health path (voiceforge/voiceforge_server.py). */
    val healthPath: String = "/health",
    /** Twin-side voiceforge port (server default 8765). */
    val healthPort: Int = 8765,
    /**
     * Optional pending GGUF conversion/quantization step run ON THE TWIN
     * FIRST (e.g. an `ssh ubuntu@<twin> bash /opt/buildtwin/gguf_convert.sh`
     * command). Only a healthy:true health response AND an exit-0 step
     * authorizes copying the artifact back to the phone.
     */
    val ggufStepCommand: String? = null
) {
    init {
        require(host.isNotBlank()) { "BuildTwinSpec.host must not be blank" }
        require(arch == "arm64") {
            "BuildTwinSpec.arch must be 'arm64' (ARM64-native, no x86 emulation); got '$arch'"
        }
        require(cpuOnly) {
            "BuildTwinSpec.cpuOnly must be true (CPU-only twin mirrors the device); got $cpuOnly"
        }
        require(ramCapGb in 6..8) {
            "BuildTwinSpec.ramCapGb must be in the 6-8 device-mirroring band; got $ramCapGb"
        }
        require(healthPort in 1..65535) { "BuildTwinSpec.healthPort out of range: $healthPort" }
    }

    /** Human/ops conveniences for driving scripts + diagnostics. */
    val healthUrl: String get() = "http://$host:$healthPort$healthPath"
}
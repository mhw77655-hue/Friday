package com.jarvis.app.sandbox

import com.jarvis.app.selfrepair.SourcePatch

/**
 * Gate 2 — explicit stub for cloud VM stress testing.
 *
 * NOT wired into the active default path. Exists only as the documented
 * extension point for when a provisioned cloud VM is available.
 *
 * TODO: What this needs to become real:
 *  - A provisioned cloud VM (e.g. AWS EC2, GCP Compute Engine) with:
 *    - A way to ship the candidate + a clone of the affected module to it
 *    - SSH/API access for file transfer and execution
 *    - Execution environment matching the production runtime
 *  - A result channel back from the VM (e.g. S3 + polling, or webhook)
 *  - Timeout + cost controls (cloud VMs bill by the second)
 *  - Artifact collection (logs, metrics, screenshots) from the VM run
 *
 * When cloud infra is available, this implementation should:
 *  1. Package the patched source + adversarial input set into a transferable
 *     artifact (tarball or container image)
 *  2. Ship it to the provisioned VM
 *  3. Execute the stress test there (real process isolation, real I/O)
 *  4. Collect results and return [SandboxExecutor.StressResult]
 *
 * This keeps the interface stable: callers don't change when the backend
 * switches from local to cloud.
 *
 * NOT instantiated in the live [com.jarvis.app.JarvisEngine].
 */
class CloudVmSandboxExecutor : SandboxExecutor {

    override suspend fun stressTest(
        patch: SourcePatch,
        candidateDescription: String
    ): SandboxExecutor.StressResult {
        throw NotImplementedError(
            "CloudVmSandboxExecutor.stressTest() is not yet implemented. " +
                "Requires: (1) a provisioned cloud VM with SSH/API access, " +
                "(2) a way to ship the candidate + a clone of the affected module " +
                "to it, (3) execution there in a matching runtime environment, " +
                "(4) a result channel back (S3+polling or webhook). " +
                "See TODO in CloudVmSandboxExecutor.kt for full requirements."
        )
    }
}

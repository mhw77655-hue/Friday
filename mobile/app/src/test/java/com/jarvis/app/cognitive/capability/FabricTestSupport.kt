package com.jarvis.app.cognitive.capability

import com.jarvis.app.cognitive.CognitiveEngine.CognitiveEvent
import com.jarvis.app.cognitive.execution.ActionRequest
import com.jarvis.app.cognitive.execution.ExecutionResult
import com.jarvis.app.cognitive.immune.ImmuneSystem
import com.jarvis.app.failure.BackoffPolicy
import com.jarvis.app.failure.CircuitBreaker
import com.jarvis.app.failure.FailureSurface

/** A controllable fake capability — never touches a real tool. */
class FakeCapability(
    override val descriptor: CapabilityDescriptor,
    private var behavior: suspend (ActionRequest) -> ExecutionResult = { ExecutionResult.ok() }
) : Capability {
    var calls = 0
    override suspend fun invoke(request: ActionRequest): ExecutionResult {
        calls++
        return behavior(request)
    }

    fun failWith(result: ExecutionResult) {
        behavior = { result }
    }
}

/** A shared immune system with a controllable clock + tiny recovery budget. */
fun testImmune(now: () -> Long = { 0L }, emit: (CognitiveEvent) -> Unit = {}): ImmuneSystem = ImmuneSystem(
    surface = FailureSurface(nowMs = now),
    backoffPolicy = BackoffPolicy(maxAttempts = 1, baseDelayMs = 0, jitterMs = 0),
    breakerFactory = { key -> CircuitBreaker(name = key, openMs = 30_000, nowMs = now) },
    emit = emit
)

fun descriptor(
    id: String,
    operation: String = "compute",
    category: String = "COMPUTE",
    deps: Set<String> = emptySet(),
    permissions: Set<CapabilityPermission> = emptySet(),
    enabled: Boolean = true,
    availability: Availability = Availability.ONLINE,
    profile: CapabilityResourceProfile = CapabilityResourceProfile()
): CapabilityDescriptor = CapabilityDescriptor(
    id = id,
    name = id,
    category = category,
    supportedOperations = setOf(operation),
    dependencies = deps,
    requiredPermissions = permissions,
    enabled = enabled,
    availability = availability,
    resourceProfile = profile
)

fun request(
    planId: String = "p1",
    stepId: String = "sg1:s1",
    action: String = "compute"
): ActionRequest = ActionRequest(planId = planId, stepId = stepId, action = action)

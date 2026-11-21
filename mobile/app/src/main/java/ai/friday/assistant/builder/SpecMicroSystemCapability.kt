package ai.friday.assistant.builder

import ai.friday.assistant.cognitive.capability.Capability
import ai.friday.assistant.cognitive.capability.CapabilityDescriptor
import ai.friday.assistant.cognitive.execution.ActionFailure
import ai.friday.assistant.cognitive.execution.ActionRequest
import ai.friday.assistant.cognitive.execution.ExecutionResult
import ai.friday.assistant.microsystem.CallableMicroSystem
import ai.friday.assistant.microsystem.MicroSystemContract

/**
 * Bridges a promoted Friday-generated micro-system into the same CapabilityFabric
 * used by the primary CognitiveEngine. The builder registry remains the lifecycle
 * owner; this adapter exposes the callable tool through Friday's cognitive action
 * boundary without creating a second implementation or model.
 */
class SpecMicroSystemCapability(
    private val system: MicroSystemContract
) : Capability {
    private val callable = system as? CallableMicroSystem

    override val descriptor: CapabilityDescriptor = CapabilityDescriptor(
        id = system.id,
        name = system.capabilities.sorted().joinToString(", "),
        description = "Friday-generated, validated capability backed by ${system.id}",
        category = "generated_tool",
        supportedOperations = system.capabilities,
        enabled = true
    )

    override suspend fun invoke(request: ActionRequest): ExecutionResult {
        if (request.action !in descriptor.supportedOperations) {
            return ExecutionResult.fail(ActionFailure.Unsupported(request.action))
        }
        val target = callable ?: return ExecutionResult.fail(
            ActionFailure.Error("NOT_CALLABLE", "Promoted micro-system has no callable interface.")
        )
        val inputs = request.context.mapValues { (_, raw) -> parseScalar(raw) }
        val result = target.call(inputs)
        if (!result.success) {
            return ExecutionResult.fail(
                ActionFailure.Error(
                    code = "GENERATED_TOOL_FAILED",
                    message = result.error ?: "Generated capability failed.",
                    recoverable = false
                ),
                durationMs = result.latencyMs
            )
        }
        val output = when (val data = result.data) {
            is Map<*, *> -> data.entries.associate { it.key.toString() to (it.value?.toString() ?: "") }
            null -> emptyMap()
            else -> mapOf("result" to data.toString())
        }
        return ExecutionResult.ok(
            output = output,
            durationMs = result.latencyMs,
            evidence = mapOf("microSystemId" to system.id, "genomeId" to system.genome.id)
        )
    }

    private fun parseScalar(raw: String): Any = when {
        raw.equals("true", ignoreCase = true) -> true
        raw.equals("false", ignoreCase = true) -> false
        raw.toLongOrNull() != null -> raw.toLong()
        raw.toDoubleOrNull() != null -> raw.toDouble()
        else -> raw
    }
}

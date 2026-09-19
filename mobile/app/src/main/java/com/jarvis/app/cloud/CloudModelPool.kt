package com.jarvis.app.cloud

/**
 * A single member of the cloud model pool. Mirrors one free zero-signup model
 * entry in ralph.sh's MODEL_POOL array.
 */
interface CloudProvider {
    val id: String

    /** Generate a reasoning response for [prompt]. Returns a [CloudResult]. */
    fun generate(prompt: String): CloudResult
}

sealed class CloudResult {
    data class Success(
        val text: String,
        val providerId: String
    ) : CloudResult()

    data class Failure(
        val providerId: String,
        val reason: String,
        val rateLimited: Boolean
    ) : CloudResult()
}

/**
 * Typed request the cloud-reasoning path accepts. Note it carries reasoning
 * ONLY — there is no capability/action field, so the cloud can never select
 * which capability to execute (architectural law).
 */
data class CloudReasoningRequest(
    val prompt: String,
    val taskDescription: String = ""
)
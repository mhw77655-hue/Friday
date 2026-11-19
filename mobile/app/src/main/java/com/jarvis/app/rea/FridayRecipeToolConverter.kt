package com.jarvis.app.rea

import com.jarvis.app.builder.ToolSpecification
import com.jarvis.app.evolution.Behavior

/**
 * Converts an AI-reviewed recipe into Friday's restricted synth-language
 * contract. Only pure adapters/transformers can enter the on-device Builder.
 * Android/API integrations require a repository-level implementation.
 */
object FridayRecipeToolConverter {
    fun toToolSpecification(
        packageName: String,
        recipe: FridayCompatibilityRecipe
    ): ToolSpecification? {
        if (recipe.integrationKind !in setOf(
                FridayIntegrationKind.CAPABILITY_ADAPTER,
                FridayIntegrationKind.DATA_TRANSFORMER
            )
        ) return null

        val packageSlug = packageName.lowercase().replace('.', '_')
            .replace(Regex("[^a-z0-9_]"), "_").take(36)
        val recipeSlug = recipe.title.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_").trim('_').take(24)
        val capability = "rea_" + packageSlug + "_" + recipeSlug
        val description = buildString {
            append(recipe.transferablePrinciple)
            append("\nImplementation plan: ")
            append(recipe.implementationPlan.joinToString("; "))
            append("\nEvidence/unknowns: ")
            append(recipe.evidenceAndUnknowns.joinToString("; "))
            append("\nRisk: ")
            append(recipe.risk)
        }.take(3_000)

        return ToolSpecification(
            capability = capability,
            description = description,
            behavior = Behavior.Custom(capability = capability, port = "input", type = "String"),
            constraints = buildList {
                add("Derived from APK package " + packageName + "; use only transferable behaviour, never copied proprietary implementation.")
                add("Pure transformation only. No filesystem, network, shell, reflection, permission or Android service access.")
                add("Preserve evidence labels and represent unknown behaviour explicitly.")
                addAll(recipe.implementationPlan.take(6))
                addAll(recipe.permissionRequirements.take(4).map { "Permission requirement for a future host integration: " + it })
            }.take(12),
            maxMemoryMb = 64,
            maxCpuPercent = 10.0,
            researchFirst = true,
            requireVerifiedResearch = false
        )
    }
}

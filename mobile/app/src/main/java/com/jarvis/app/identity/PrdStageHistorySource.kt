package com.jarvis.app.identity

import org.json.JSONObject
import java.io.File

/**
 * Production [StageHistorySource] that reads REAL prd.json states (the
 * authoritative per-stage spec files) and reports which user stories have
 * actually closed (`passes: true`). The stage label is derived from each
 * file's `branchName` (e.g. `ralph/stage-03-...` -> `stage-03`), not from
 * asserted prose.
 *
 * Designed to accept multiple prd.json paths so "history across stages" holds
 * as soon as per-stage PRD files exist alongside the current one.
 */
class PrdStageHistorySource(
    private val prdPaths: List<String>
) : StageHistorySource {

    override fun closedMilestones(): List<StageMilestone> {
        return prdPaths
            .mapNotNull { path -> readFile(path) }
            .flatMap { parse(it) }
    }

    private fun readFile(path: String): String? {
        val file = File(path)
        return if (file.exists() && file.isFile) file.readText() else null
    }

    private fun parse(content: String): List<StageMilestone> {
        return try {
            val root = JSONObject(content)
            val branch = root.optString("branchName")
            val stage = stageLabel(branch)
            val stories = root.optJSONArray("userStories") ?: return emptyList()
            val out = mutableListOf<StageMilestone>()
            for (i in 0 until stories.length()) {
                val s = stories.getJSONObject(i)
                out.add(
                    StageMilestone(
                        stage = stage,
                        storyId = s.optString("id"),
                        title = s.optString("title"),
                        closed = s.optBoolean("passes", false)
                    )
                )
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun stageLabel(branch: String): String =
        Regex("stage-\\d+", RegexOption.IGNORE_CASE)
            .find(branch)
            ?.value
            ?: "current"
}

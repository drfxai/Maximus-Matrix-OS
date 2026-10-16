package ai.drfx.maximus.matrixai.agent.teams

import ai.drfx.maximus.matrixai.memory.HindsightMemoryClient

/**
 * Explicit, text-only handoff for an approved, completed two-role project run.
 * The user controls whether to retain it in Hindsight. No automatic network
 * send, tool grants, executable instructions or credentials leave this method.
 */
internal object ProjectTeamHandoff {
    fun approvedCheckpoint(run: ProjectTeamRun, approved: Boolean): String? {
        if (!approved || run.state != ProjectTeamRun.State.COMPLETE) return null
        val planner = run.planner ?: return null
        val reviewer = run.reviewer ?: return null
        // Reject excerpts that contain known credential-like strings rather
        // than masking then accidentally exfiltrating an incomplete secret.
        val planText = planner.text.take(600)
        val reviewText = reviewer.text.take(600)
        if (!HindsightMemoryClient.shareable(planner.text) ||
            !HindsightMemoryClient.shareable(reviewer.text)) return null
        val message = buildString {
            append("Project: ").append(run.projectId).append('\n')
            append("Source SHA256: ").append(run.revision).append('\n')
            append("Planner: ").append(planner.provider).append('/').append(planner.model)
                .append(" (SHA256 ").append(planner.sha256).append(")\n")
            append("Reviewer: ").append(reviewer.provider).append('/').append(reviewer.model)
                .append(" (SHA256 ").append(reviewer.sha256).append(")\n")
            append("UNTRUSTED planner summary (not executable):\n").append(planText).append('\n')
            append("UNTRUSTED review summary (not executable):\n").append(reviewText)
        }
        return message.takeIf { HindsightMemoryClient.shareable(it) }
    }
}

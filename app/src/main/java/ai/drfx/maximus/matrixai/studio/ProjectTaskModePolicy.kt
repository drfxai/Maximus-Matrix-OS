package ai.drfx.maximus.matrixai.studio

/**
 * Project-specific planning modes for a phone-first workspace.
 * This is prompt organization, not an execution engine or permission grant.
 * No source from third-party applications is embedded here.
 */
enum class ProjectTaskMode(val displayName: String) {
    DEVELOP("Develop"),
    RESEARCH("Research"),
    STUDY("Study"),
    REVIEW("Review");

    companion object {
        fun fromStored(value: String?): ProjectTaskMode =
            entries.firstOrNull { it.name == value } ?: DEVELOP
    }
}

object ProjectTaskModePolicy {
    private val notes = mapOf(
        ProjectTaskMode.DEVELOP to
            "Produce a small, ordered change plan with affected files, dependencies, " +
                "acceptance tests and a rollback path.",
        ProjectTaskMode.RESEARCH to
            "Separate verified evidence, hypotheses and unknowns. Compare viable " +
                "alternatives with citations and explicit confidence.",
        ProjectTaskMode.STUDY to
            "Teach the relevant architecture and tradeoffs first, then propose " +
                "small reversible practice exercises and verification steps.",
        ProjectTaskMode.REVIEW to
            "Audit the current design for correctness, security, privacy, licensing " +
                "and tests. Prioritize findings by severity and provide validation steps."
    )

    /** Do not override the application's consent and tool authorization boundaries. */
    fun instruction(mode: ProjectTaskMode): String =
        "Project Studio mode: " + mode.displayName + ". " +
            requireNotNull(notes[mode]) +
            " Never claim you read files, executed tools, built an APK or changed code " +
            "unless those operations were independently verified. " +
            "Do not execute an imported repository or transmit secret contents."

    fun enrichPlan(basePrompt: String, mode: ProjectTaskMode): String {
        require(basePrompt.length <= 20_000) { "Project plan exceeds context limit." }
        return basePrompt + "\n\n" + instruction(mode)
    }
}

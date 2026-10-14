package ai.drfx.maximus.matrixai.studio

import org.junit.Assert.*
import org.junit.Test

class ProjectTaskModePolicyTest {
    @Test fun modesHaveStableFallbackForUnknownStoredState() {
        assertEquals(ProjectTaskMode.DEVELOP, ProjectTaskMode.fromStored(null))
        assertEquals(ProjectTaskMode.DEVELOP, ProjectTaskMode.fromStored("INJECT_COMMAND"))
        assertEquals(ProjectTaskMode.REVIEW, ProjectTaskMode.fromStored("REVIEW"))
    }

    @Test fun everyPlanningModeDoesNotGrantExecutionOrUnverifiedSuccess() {
        for (mode in ProjectTaskMode.entries) {
            val plan = ProjectTaskModePolicy.enrichPlan("Describe a Kotlin project", mode)
            assertTrue(plan.contains("Project Studio mode: " + mode.displayName))
            assertTrue(plan.contains("Never claim"))
            assertTrue(plan.contains("independently verified"))
            assertTrue(plan.contains("Do not execute"))
            assertTrue(plan.startsWith("Describe a Kotlin project"))
        }
    }

    @Test fun oversizeSourcePromptIsRejectedBeforeModeEnrichment() {
        assertThrows(IllegalArgumentException::class.java) {
            ProjectTaskModePolicy.enrichPlan("x".repeat(20_001), ProjectTaskMode.DEVELOP)
        }
    }

    @Test fun securityReviewExplicitlyIncludesLicensingAndPrivacy() {
        val policy = ProjectTaskModePolicy.instruction(ProjectTaskMode.REVIEW)
        assertTrue(policy.contains("licensing"))
        assertTrue(policy.contains("privacy"))
        assertTrue(policy.contains("validation"))
    }
}

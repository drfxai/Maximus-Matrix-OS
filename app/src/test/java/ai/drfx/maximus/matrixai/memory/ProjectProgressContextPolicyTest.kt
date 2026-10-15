package ai.drfx.maximus.matrixai.memory

import org.junit.Assert.*
import org.junit.Test

class ProjectProgressContextPolicyTest {
    private fun progress(revision: Int): String =
        "Model: model-$revision. Task: continue revision $revision. Proposed result: " +
            ("Revision $revision progress. ").repeat(45) + "Unverified until files/tests are checked."

    @Test fun newestPersistedJournalEntrySurvivesBothActualPromptBudgets() {
        // rememberProgress appends entries and keeps its last four, oldest first.
        val journal = (1..4).map(::progress)
        val fullContext = ProjectProgressContextPolicy.newestFirst(journal)
        assertEquals(2100, fullContext.length)
        assertTrue(fullContext.startsWith(journal.last()))
        assertFalse(fullContext.contains("Task: continue revision 1"))
        assertFalse(fullContext.contains("Task: continue revision 2"))

        // SharedProjectMemory applies this additional budget to recentProgress.
        val handoff = fullContext.take(950)
        assertEquals(950, handoff.length)
        assertTrue(handoff.startsWith("Model: model-4. Task: continue revision 4."))
        assertFalse(handoff.contains("Task: continue revision 3"))
        assertEquals(handoff, ProjectProgressContextPolicy.newestFirst(journal, 950))
    }

    @Test fun aNewlyAppendedModelResultBecomesTheFirstHandoffEntry() {
        val firstModel = listOf("Revision 1: inspect parser", "Revision 2: reproduce defect")
        assertEquals("Revision 2: reproduce defect\nRevision 1: inspect parser",
            ProjectProgressContextPolicy.newestFirst(firstModel))
        val afterProviderSwitch = firstModel + "Revision 3: fix parser; next run tests"
        assertTrue(ProjectProgressContextPolicy.newestFirst(afterProviderSwitch)
            .startsWith("Revision 3: fix parser; next run tests\nRevision 2:"))
        assertEquals(listOf("Revision 1: inspect parser", "Revision 2: reproduce defect"), firstModel)
    }

    @Test fun emptyOrFilteredJournalCreatesNoInventedMemory() {
        assertEquals("", ProjectProgressContextPolicy.newestFirst(emptyList()))
        assertEquals("", ProjectProgressContextPolicy.newestFirst(listOf("", " ", "\n")))
        assertEquals("latest eligible", ProjectProgressContextPolicy.newestFirst(listOf("", "latest eligible", " ")))
        assertEquals("", ProjectProgressContextPolicy.newestFirst(listOf("latest eligible"), 0))
    }

    @Test fun truncationDoesNotAppendEmptySeparatorsOrExceedBudget() {
        assertEquals("new", ProjectProgressContextPolicy.newestFirst(listOf("old", "new"), 4))
        assertEquals("new\no", ProjectProgressContextPolicy.newestFirst(listOf("old", "new"), 5))
        assertEquals("new", ProjectProgressContextPolicy.newestFirst(listOf("old", "new"), 3))
        assertThrows(IllegalArgumentException::class.java) {
            ProjectProgressContextPolicy.newestFirst(listOf("new"), -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProjectProgressContextPolicy.newestFirst(listOf("new"), 2101)
        }
    }
}

package ai.drfx.maximus.matrixai.studio.phonehost

import org.junit.Assert.*
import org.junit.Test

class PhoneLinuxCommandTest {
    @Test fun preservesTheReviewedCommandWithoutHostInterpolation() {
        val command = "printf '%s' '\$(uname)'\npwd"
        assertEquals(command, PhoneLinuxCommand.validate(command))
    }
    @Test fun rejectsInvalidAndOversizedCommands() {
        for (input in listOf("", " \n", "a\u0000b", "a\u001bb", "a".repeat(4097))) {
            assertThrows(IllegalArgumentException::class.java) { PhoneLinuxCommand.validate(input) }
        }
    }
    @Test fun singleLongLineCannotExceedTheOutputBudget() {
        var result = ""
        repeat(1000) { result = PhoneLinuxCommand.tail(result, "x".repeat(2048)) }
        assertEquals(PhoneLinuxCommand.MAX_OUTPUT, result.length)
        assertTrue(PhoneLinuxCommand.tail(result, "final").endsWith("final"))
    }
}

package mishmash.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Checks semantic feedback factories and defensive result validation.
 */
class CommandResultTest {
    @Test
    void factories_validFeedback_assignExplicitStatuses() {
        assertEquals(CommandResult.Status.DONE, CommandResult.done("done").status());
        assertEquals(CommandResult.Status.NOTE, CommandResult.note("note").status());
        assertEquals(CommandResult.Status.WARNING, CommandResult.warning("warning").status());
        assertEquals(CommandResult.Status.INFO, CommandResult.info("info").status());
        assertEquals(CommandResult.Status.HELP, CommandResult.help("help").status());
    }

    @Test
    void constructor_missingValuesRejectsAndBlankFeedbackRemainsRecoverableAtApplicationBoundary() {
        assertThrows(NullPointerException.class, () -> new CommandResult(null, CommandResult.Status.INFO));
        assertThrows(NullPointerException.class, () -> new CommandResult("feedback", null));
        assertEquals("  \t", new CommandResult("  \t", CommandResult.Status.INFO).feedback());
    }
}

package mishmash.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Attributes;
import org.jline.terminal.Attributes.ControlChar;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.impl.DumbTerminal;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import mishmash.testutil.LogCapture;

//@@author Rixte
/**
 * Checks editor interruption, confirmation safety, literal input, and terminal cleanup without native access.
 */
class JLineConsoleInputTest {
    @Test
    void readAnswer_percentInRecipeName_remainsLiteralInThePrompt() throws IOException {
        ScriptedReader script = new ScriptedReader("no");
        try (JLineConsoleInput input = new JLineConsoleInput(createTerminal(), script.reader)) {
            assertEquals(Optional.of("no"), input.readAnswer("Delete 100%N Soup?"));
            assertEquals(List.of("Delete 100%%N Soup? "), script.prompts);
        }
    }

    @Test
    void readCommand_realEditor_completesCommandsAndPrefixesWithoutAddingASpaceBeforeTheValue() throws IOException {
        try (JLineConsoleInput input = new JLineConsoleInput(createEditingTerminal("check-sh\tca\t1500\n"))) {
            assertEquals(Optional.of("check-shortfall cal/1500"), input.readCommand("Command?"));
        }
    }

    @TestFactory
    Stream<DynamicTest> readCommand_realEditor_completesNewMealCommandsAndPrefixes() {
        return Stream.of(
                new String[]{"record-m\t1 da\t2026-03-02 s\t2\n", "record-meal 1 date/2026-03-02 s/2"},
                new String[]{"edit-m\t1 s\t2\n", "edit-meal 1 s/2"},
                new String[]{"delete-m\t1\n", "delete-meal 1"},
                new String[]{"view-m\tda\t2026-03-02\n", "view-meals date/2026-03-02"},
                new String[]{"replace-m\td\t1 m\t2 r\t3\n", "replace-meal d/1 m/2 r/3"})
                .map(example -> DynamicTest.dynamicTest(example[1], () -> {
                    try (JLineConsoleInput input = new JLineConsoleInput(createEditingTerminal(example[0]))) {
                        assertEquals(Optional.of(example[1]), input.readCommand("Command?"));
                    }
                }));
    }

    @Test
    void readCommand_realHistory_recallsCommandsInsteadOfNamesAndConfirmationAnswers() throws IOException {
        // Ctrl+P recalls history independently of a terminal's platform-specific Up-arrow escape sequence.
        try (JLineConsoleInput input = new JLineConsoleInput(
                createEditingTerminal("Tester\nview-plan\nyes\n\u0010\n"))) {
            assertEquals(Optional.of("Tester"), input.readAnswer("Name?"));
            assertEquals(Optional.of("view-plan"), input.readCommand("Command?"));
            assertEquals(Optional.of("yes"), input.readAnswer("Reset?"));
            assertEquals(Optional.of("view-plan"), input.readCommand("Command?"));
        }
    }

    @Test
    void readCommand_realEditor_correctsTextBeforeSubmittingIt() throws IOException {
        try (JLineConsoleInput input = new JLineConsoleInput(createEditingTerminal("view-plax\u007Fn\n"))) {
            assertEquals(Optional.of("view-plan"), input.readCommand("Command?"));
        }
    }

    @TestFactory
    Stream<DynamicTest> readCommand_navigationKeys_editsAtTheCursorBeforeSubmission() {
        // xterm sends application-mode cursor keys after JLine enables the terminal's keypad mode.
        return Stream.of(
                new String[]{"Left and Right", "view-pa\u001BODl\u001BOCn\n"},
                new String[]{"Home and End", "pla\u001BOHview-\u001BOFn\n"},
                new String[]{"Delete", "view-plaxn\u001BOD\u001BOD\u001B[3~\n"})
                .map(example -> DynamicTest.dynamicTest(example[0], () -> {
                    try (JLineConsoleInput input = new JLineConsoleInput(createEditingTerminal(example[1]))) {
                        assertEquals(Optional.of("view-plan"), input.readCommand("Command?"));
                    }
                }));
    }

    @Test
    void readCommand_upThenDown_restoresTheUnsubmittedDraft() throws IOException {
        String keys = "view-plan\ncheck-shortfall p/100\u001BOA\u001BOB\n";
        try (JLineConsoleInput input = new JLineConsoleInput(createEditingTerminal(keys))) {
            assertEquals(Optional.of("view-plan"), input.readCommand("Command?"));
            assertEquals(Optional.of("check-shortfall p/100"), input.readCommand("Command?"));
        }
    }

    @Test
    void readAnswer_tab_doesNotCompleteACommandAndNextCommandCanCompleteAgain() throws IOException {
        try (JLineConsoleInput input = new JLineConsoleInput(createEditingTerminal("check-sh\t\ncheck-sh\t\n"))) {
            // In answer mode Tab is ignored instead of expanding a command or offering prefixes.
            assertEquals(Optional.of("check-sh"), input.readAnswer("Name?"));
            assertEquals(Optional.of("check-shortfall "), input.readCommand("Command?"));
        }
    }

    @Test
    void readCommand_newSession_doesNotRecallAnotherSessionsHistory() throws IOException {
        try (JLineConsoleInput first = new JLineConsoleInput(createEditingTerminal("view-plan\n"))) {
            assertEquals(Optional.of("view-plan"), first.readCommand("Command?"));
        }
        try (JLineConsoleInput second = new JLineConsoleInput(createEditingTerminal("\u001BOAbye\n"))) {
            assertEquals(Optional.of("bye"), second.readCommand("Command?"));
        }
    }

    @Test
    void readCommand_repeatedInterruptsThenEof_neverSubmitsPartialCommands() throws IOException {
        ScriptedReader script = new ScriptedReader(new UserInterruptException("reset-plan"),
                new UserInterruptException("delete-recipe 1"), new EndOfFileException());
        try (JLineConsoleInput input = new JLineConsoleInput(createTerminal(), script.reader)) {
            assertEquals(Optional.empty(), input.readCommand("Command?"));
            assertEquals(List.of("Command? ", "Command? ", "Command? "), script.prompts);
        }
    }

    @Test
    void readCommand_realCtrlC_discardsPartialCommandAndReadsTheNextOne() throws IOException {
        try (JLineConsoleInput input = new JLineConsoleInput(createEditingTerminal("reset-plan\u0003view-plan\n"))) {
            assertEquals(Optional.of("view-plan"), input.readCommand("Command?"));
        }
    }

    @Test
    void readAnswer_realCtrlC_discardsPartialAgreementAndAllowsTheNextCommand() throws IOException {
        try (JLineConsoleInput input = new JLineConsoleInput(createEditingTerminal("yes\u0003bye\n"))) {
            assertEquals(Optional.of(""), input.readAnswer("Delete?"));
            assertEquals(Optional.of("bye"), input.readCommand("Command?"));
        }
    }

    @Test
    void readAnswer_realCtrlD_cancelsWithoutConsumingTheNextCommand() throws IOException {
        try (JLineConsoleInput input = new JLineConsoleInput(createEditingTerminal("\u0004bye\n"))) {
            assertEquals(Optional.empty(), input.readAnswer("Delete?"));
            assertEquals(Optional.of("bye"), input.readCommand("Command?"));
        }
    }

    @Test
    void readCommand_interruptThenCommand_repromptsWithoutSubmittingThePartialCommand() throws IOException {
        ScriptedReader script = new ScriptedReader(new UserInterruptException("reset-plan"), "view-plan");
        try (JLineConsoleInput input = new JLineConsoleInput(createTerminal(), script.reader)) {
            assertEquals(Optional.of("view-plan"), input.readCommand("mishmash >"));
            assertEquals(List.of("mishmash > ", "mishmash > "), script.prompts);
            assertEquals(false, script.variables.get(LineReader.DISABLE_HISTORY));
            assertEquals(false, script.variables.get(LineReader.DISABLE_COMPLETION));
        }
    }

    @Test
    void readAnswer_interrupt_returnsBlankCancellationAndExcludesAnswersFromHistory() throws IOException {
        ScriptedReader script = new ScriptedReader(new UserInterruptException("yes"), "bye");
        try (JLineConsoleInput input = new JLineConsoleInput(createTerminal(), script.reader)) {
            assertEquals(Optional.of(""), input.readAnswer("Reset? (y/n)"));
            assertEquals(true, script.variables.get(LineReader.DISABLE_HISTORY));
            assertEquals(true, script.variables.get(LineReader.DISABLE_COMPLETION));
            assertEquals(Optional.of("bye"), input.readCommand("mishmash >"));
            assertEquals(false, script.variables.get(LineReader.DISABLE_HISTORY));
        }
    }

    @Test
    void readLine_endOfInput_remainsDistinctFromABlankAnswer() throws IOException {
        ScriptedReader script = new ScriptedReader("", new EndOfFileException(), new EndOfFileException());
        try (JLineConsoleInput input = new JLineConsoleInput(createTerminal(), script.reader)) {
            assertEquals(Optional.of(""), input.readAnswer("Name?"));
            assertEquals(Optional.empty(), input.readAnswer("Confirm?"));
            assertEquals(Optional.empty(), input.readCommand("mishmash >"));
        }
    }

    @Test
    void readCommand_unexpectedReaderFailure_propagatesTheOriginalCause() throws IOException {
        IllegalStateException failure = new IllegalStateException("Reader unavailable");
        ScriptedReader script = new ScriptedReader(failure);
        try (JLineConsoleInput input = new JLineConsoleInput(createTerminal(), script.reader)) {
            assertSame(failure, assertThrows(IllegalStateException.class, () -> input.readCommand("Command?")));
        }
    }

    @Test
    void readLine_realEditor_preservesQuotesBackslashesAndExclamationMarks() throws IOException {
        String literal = "add-recipe n/A's \\ Soup!!";
        try (JLineConsoleInput input = new JLineConsoleInput(new DumbTerminal(
                new ByteArrayInputStream((literal + "\n").getBytes(StandardCharsets.UTF_8)),
                new ByteArrayOutputStream()))) {
            assertEquals(Optional.of(literal), input.readCommand("mishmash >"));
        }
    }

    @Test
    void close_failedTerminalCleanup_logsTheOriginalCause() throws IOException {
        IOException failure = new IOException("Cannot restore terminal");
        Terminal terminal = new DumbTerminal(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream()) {
            @Override
            protected void doClose() throws IOException {
                super.doClose();
                throw failure;
            }
        };
        try (LogCapture logs = new LogCapture(JLineConsoleInput.class)) {
            new JLineConsoleInput(terminal, new ScriptedReader().reader).close();
            assertEquals(1, logs.getRecords().size());
            assertSame(failure, logs.getRecords().getFirst().getThrown());
        }
    }

    @Test
    void constructor_missingDependencies_rejectsBeforeReading() throws IOException {
        assertThrows(NullPointerException.class, () -> new JLineConsoleInput(null));
        try (Terminal terminal = createTerminal()) {
            assertThrows(NullPointerException.class, () -> new JLineConsoleInput(terminal, null));
        }
    }

    /**
     * Returns a non-native terminal whose streams contain no input and collect no visible output.
     */
    private Terminal createTerminal() throws IOException {
        return new DumbTerminal(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());
    }

    /**
     * Simulates a terminal with editing capabilities while keeping input and output entirely in memory.
     */
    private Terminal createEditingTerminal(String keys) throws IOException {
        Terminal terminal = new DumbTerminal("test", "xterm-256color",
                new ByteArrayInputStream(keys.getBytes(StandardCharsets.UTF_8)), new ByteArrayOutputStream(),
                StandardCharsets.UTF_8);
        // DumbTerminal omits these bindings; a normal terminal declares Ctrl+C and Ctrl+D explicitly.
        Attributes attributes = terminal.getAttributes();
        attributes.setControlChar(ControlChar.VINTR, 3);
        attributes.setControlChar(ControlChar.VEOF, 4);
        terminal.setAttributes(attributes);
        terminal.setSize(new Size(80, 24));
        return terminal;
    }

    /**
     * Supplies deterministic editor responses and captures configuration changes at each prompt.
     */
    private static class ScriptedReader {
        private final Map<String, Object> variables = new HashMap<>();
        private final List<String> prompts = new ArrayList<>();
        private final LineReader reader;

        /**
         * Creates a small LineReader substitute for the two operations used by the input adapter.
         */
        ScriptedReader(Object... responses) {
            Deque<Object> pending = new ArrayDeque<>(List.of(responses));
            reader = (LineReader) Proxy.newProxyInstance(LineReader.class.getClassLoader(),
                    new Class<?>[]{LineReader.class}, (proxy, method, arguments) -> {
                        if (method.getName().equals("setVariable")) {
                            variables.put((String) arguments[0], arguments[1]);
                            return null;
                        }
                        if (method.getName().equals("readLine")) {
                            prompts.add((String) arguments[0]);
                            Object response = pending.removeFirst();
                            if (response instanceof RuntimeException failure) {
                                throw failure;
                            }
                            return response;
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
        }
    }
}
//@@author

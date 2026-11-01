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

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.impl.DumbTerminal;
import org.junit.jupiter.api.Test;

import mishmash.testutil.LogCapture;

//@@author Rixte
/**
 * Checks editor interruption, confirmation safety, literal input, and terminal cleanup without native access.
 */
class JLineConsoleInputTest {
    @Test
    void readAnswer_percentInRecipeName_remainsLiteralInThePrompt() throws IOException {
        ScriptedReader script = new ScriptedReader("no");
        try (JLineConsoleInput input = new JLineConsoleInput(terminal(), script.reader)) {
            assertEquals(Optional.of("no"), input.readAnswer("Delete 100%N Soup?"));
            assertEquals(List.of("Delete 100%%N Soup? "), script.prompts);
        }
    }

    @Test
    void readCommand_realEditor_completesCommandsAndPrefixesWithoutAddingASpaceBeforeTheValue() throws IOException {
        try (JLineConsoleInput input = new JLineConsoleInput(editingTerminal("check-sh\tca\t1500\n"))) {
            assertEquals(Optional.of("check-shortfall cal/1500"), input.readCommand("Command?"));
        }
    }

    @Test
    void readCommand_realHistory_recallsCommandsInsteadOfNamesAndConfirmationAnswers() throws IOException {
        // Ctrl+P recalls history independently of a terminal's platform-specific Up-arrow escape sequence.
        try (JLineConsoleInput input = new JLineConsoleInput(editingTerminal("Tester\nview-plan\nyes\n\u0010\n"))) {
            assertEquals(Optional.of("Tester"), input.readAnswer("Name?"));
            assertEquals(Optional.of("view-plan"), input.readCommand("Command?"));
            assertEquals(Optional.of("yes"), input.readAnswer("Reset?"));
            assertEquals(Optional.of("view-plan"), input.readCommand("Command?"));
        }
    }

    @Test
    void readCommand_realEditor_correctsTextBeforeSubmittingIt() throws IOException {
        try (JLineConsoleInput input = new JLineConsoleInput(editingTerminal("view-plax\u007Fn\n"))) {
            assertEquals(Optional.of("view-plan"), input.readCommand("Command?"));
        }
    }

    @Test
    void readCommand_interruptThenCommand_repromptsWithoutSubmittingThePartialCommand() throws IOException {
        ScriptedReader script = new ScriptedReader(new UserInterruptException("reset-plan"), "view-plan");
        try (JLineConsoleInput input = new JLineConsoleInput(terminal(), script.reader)) {
            assertEquals(Optional.of("view-plan"), input.readCommand("mishmash >"));
            assertEquals(List.of("mishmash > ", "mishmash > "), script.prompts);
            assertEquals(false, script.variables.get(LineReader.DISABLE_HISTORY));
            assertEquals(false, script.variables.get(LineReader.DISABLE_COMPLETION));
        }
    }

    @Test
    void readAnswer_interrupt_returnsBlankCancellationAndExcludesAnswersFromHistory() throws IOException {
        ScriptedReader script = new ScriptedReader(new UserInterruptException("yes"), "bye");
        try (JLineConsoleInput input = new JLineConsoleInput(terminal(), script.reader)) {
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
        try (JLineConsoleInput input = new JLineConsoleInput(terminal(), script.reader)) {
            assertEquals(Optional.of(""), input.readAnswer("Name?"));
            assertEquals(Optional.empty(), input.readAnswer("Confirm?"));
            assertEquals(Optional.empty(), input.readCommand("mishmash >"));
        }
    }

    @Test
    void readCommand_unexpectedReaderFailure_propagatesTheOriginalCause() throws IOException {
        IllegalStateException failure = new IllegalStateException("Reader unavailable");
        ScriptedReader script = new ScriptedReader(failure);
        try (JLineConsoleInput input = new JLineConsoleInput(terminal(), script.reader)) {
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
        try (Terminal terminal = terminal()) {
            assertThrows(NullPointerException.class, () -> new JLineConsoleInput(terminal, null));
        }
    }

    /**
     * Returns a non-native terminal whose streams contain no input and collect no visible output.
     */
    private Terminal terminal() throws IOException {
        return new DumbTerminal(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());
    }

    /**
     * Simulates a terminal with editing capabilities while keeping input and output entirely in memory.
     */
    private Terminal editingTerminal(String keys) throws IOException {
        Terminal terminal = new DumbTerminal("test", "xterm-256color",
                new ByteArrayInputStream(keys.getBytes(StandardCharsets.UTF_8)), new ByteArrayOutputStream(),
                StandardCharsets.UTF_8);
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

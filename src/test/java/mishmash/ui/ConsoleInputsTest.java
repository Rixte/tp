package mishmash.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.jline.terminal.Terminal;
import org.jline.terminal.impl.DumbTerminal;
import org.junit.jupiter.api.Test;

import mishmash.testutil.LogCapture;

//@@author Rixte
/**
 * Checks terminal selection and safe fallback without requiring a native console in automated tests.
 */
class ConsoleInputsTest {
    @Test
    void supportsEditing_limitedOrRedirectedConsole_usesPlainInput() {
        assertTrue(ConsoleInputs.supportsEditing(true, null));
        assertTrue(ConsoleInputs.supportsEditing(true, "xterm-256color"));
        assertFalse(ConsoleInputs.supportsEditing(false, null));
        assertFalse(ConsoleInputs.supportsEditing(false, "xterm-256color"));
        assertFalse(ConsoleInputs.supportsEditing(true, "dumb"));
        assertFalse(ConsoleInputs.supportsEditing(true, "dumb-color"));
    }

    @Test
    void open_redirectedInput_skipsNativeTerminalAndPreservesLines() {
        ByteArrayInputStream stream = stream("Name\nbye\n");
        try (PrintStream output = new PrintStream(new ByteArrayOutputStream());
                ConsoleInput input = ConsoleInputs.open(false, stream, output, () -> {
                    throw new AssertionError("Plain input must not initialize native terminal access");
                })) {
            assertInstanceOf(ScannerConsoleInput.class, input);
            assertEquals(Optional.of("Name"), input.readAnswer("Name?"));
            assertEquals(Optional.of("bye"), input.readCommand("Command?"));
        }
    }

    @Test
    void open_interactiveTerminal_selectsJLineAndReadsTheSameStream() throws IOException {
        Terminal terminal = new DumbTerminal(stream("help\n"), new ByteArrayOutputStream());
        try (ConsoleInput input = ConsoleInputs.open(true, stream("unused"), System.out, () -> terminal)) {
            assertInstanceOf(JLineConsoleInput.class, input);
            assertEquals(Optional.of("help"), input.readCommand("Command?"));
        }
    }

    @Test
    void open_nativeInitializationFailure_fallsBackWithoutConsumingInput() {
        IOException failure = new IOException("No native console");
        try (LogCapture logs = new LogCapture(ConsoleInputs.class);
                ConsoleInput input = ConsoleInputs.open(true, stream("help\n"), System.out, () -> {
                    throw failure;
                })) {
            assertInstanceOf(ScannerConsoleInput.class, input);
            assertEquals(Optional.of("help"), input.readCommand("Command?"));
            assertSame(failure, logs.getRecords().getFirst().getThrown());
        }
    }

    @Test
    void open_editorInitializationFailure_closesTerminalBeforeFallingBack() {
        AtomicInteger closeCount = new AtomicInteger();
        IllegalStateException failure = new IllegalStateException("Unsupported terminal");
        Terminal terminal = brokenTerminal(failure, closeCount, null);
        try (LogCapture logs = new LogCapture(ConsoleInputs.class);
                ConsoleInput input = ConsoleInputs.open(true, stream("bye\n"), System.out, () -> terminal)) {
            assertEquals(1, closeCount.get());
            assertInstanceOf(ScannerConsoleInput.class, input);
            assertSame(failure, logs.getRecords().getFirst().getThrown());
            assertEquals(Optional.of("bye"), input.readCommand("Command?"));
        }
    }

    @Test
    void open_cleanupAlsoFails_retainsOriginalFailureAndSuppressedCleanupCause() {
        IllegalStateException failure = new IllegalStateException("Editor unavailable");
        IOException closeFailure = new IOException("Close failed");
        Terminal terminal = brokenTerminal(failure, new AtomicInteger(), closeFailure);
        try (LogCapture logs = new LogCapture(ConsoleInputs.class);
                ConsoleInput input = ConsoleInputs.open(true, stream(""), System.out, () -> terminal)) {
            assertInstanceOf(ScannerConsoleInput.class, input);
            assertSame(failure, logs.getRecords().getFirst().getThrown());
            assertEquals(1, failure.getSuppressed().length);
            assertSame(closeFailure, failure.getSuppressed()[0]);
            assertTrue(input.readAnswer("Name?").isEmpty());
        }
    }

    @Test
    void open_unexpectedInitializationFailure_closesTerminalAndPropagatesTheCause() {
        IllegalArgumentException failure = new IllegalArgumentException("Invalid editor configuration");
        AtomicInteger closeCount = new AtomicInteger();
        Terminal terminal = brokenTerminal(failure, closeCount, null);

        assertSame(failure, assertThrows(IllegalArgumentException.class,
                () -> ConsoleInputs.open(true, stream(""), System.out, () -> terminal)));
        assertEquals(1, closeCount.get());
    }

    /**
     * Encodes deterministic input without using or changing the real process console.
     */
    private ByteArrayInputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Simulates a terminal that fails as soon as the editor inspects its capabilities.
     */
    private Terminal brokenTerminal(RuntimeException failure, AtomicInteger closeCount, IOException closeFailure) {
        return (Terminal) Proxy.newProxyInstance(Terminal.class.getClassLoader(), new Class<?>[]{Terminal.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("close")) {
                        closeCount.incrementAndGet();
                        if (closeFailure != null) {
                            throw closeFailure;
                        }
                        return null;
                    }
                    throw failure;
                });
    }
}
//@@author

package mishmash.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Scanner;

import org.junit.jupiter.api.Test;

//@@author Rixte
/**
 * Checks plain-console prompts, blank lines, EOF, and ownership of the wrapped scanner.
 */
class ScannerConsoleInputTest {
    @Test
    void readAnswer_blankAndLiteralInput_preservesTextAndDistinguishesEof() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);
                ScannerConsoleInput input = new ScannerConsoleInput(new Scanner("\n  A's \\ Soup!!  \n"), output)) {
            assertEquals(Optional.of(""), input.readAnswer("Name?"));
            assertEquals(Optional.of("  A's \\ Soup!!  "), input.readCommand("Command?"));
            assertEquals(Optional.empty(), input.readAnswer("Last?"));
            assertEquals(String.join(System.lineSeparator(), "Name?", "Command?", "Last?", ""),
                    bytes.toString(StandardCharsets.UTF_8));
            assertFalse(output.checkError());
        }
    }

    @Test
    void close_ownedScanner_closesInputWithoutClosingOutput() {
        Scanner scanner = new Scanner("bye\n");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream output = new PrintStream(bytes)) {
            new ScannerConsoleInput(scanner, output).close();

            assertThrows(IllegalStateException.class, scanner::hasNextLine);
            output.print("still open");
            assertEquals("still open", bytes.toString(StandardCharsets.UTF_8));
        }
    }

    @Test
    void constructor_missingDependencies_reportsTheMissingArgument() {
        assertEquals("scanner", assertThrows(NullPointerException.class,
                () -> new ScannerConsoleInput(null, System.out)).getMessage());
        try (Scanner scanner = new Scanner("")) {
            assertEquals("output", assertThrows(NullPointerException.class,
                    () -> new ScannerConsoleInput(scanner, null)).getMessage());
        }
    }
}
//@@author

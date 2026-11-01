package mishmash.ui;

import java.io.PrintStream;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;

//@@author Rixte
/**
 * Preserves ordinary line-based input for IDE consoles, redirected streams, and existing tests.
 */
public final class ScannerConsoleInput implements ConsoleInput {
    private final Scanner scanner;
    private final PrintStream output;

    /**
     * Wraps supplied streams without taking over their lifetime until close is explicitly called.
     *
     * @param scanner The source of complete input lines.
     * @param output The destination for prompts.
     */
    public ScannerConsoleInput(Scanner scanner, PrintStream output) {
        this.scanner = Objects.requireNonNull(scanner, "scanner");
        this.output = Objects.requireNonNull(output, "output");
    }

    @Override
    public Optional<String> readAnswer(String prompt) {
        output.println(prompt);
        output.flush();
        return scanner.hasNextLine() ? Optional.of(scanner.nextLine()) : Optional.empty();
    }

    @Override
    public void close() {
        scanner.close();
    }
}
//@@author

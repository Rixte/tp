package mishmash.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

//@@author Rixte
/**
 * Selects interactive editing for a real console and preserves plain input everywhere else.
 */
public final class ConsoleInputs {
    private static final Logger LOGGER = Logger.getLogger(ConsoleInputs.class.getName());

    private ConsoleInputs() {
    }

    /**
     * Opens input for this process, falling back if its terminal cannot provide interactive editing.
     *
     * @return An input adapter whose resources must be closed by the application launcher.
     */
    public static ConsoleInput open() {
        boolean isInteractive = supportsEditing(System.console() != null, System.getenv("TERM"));
        return open(isInteractive, System.in, System.out,
                () -> TerminalBuilder.builder().system(true).dumb(false).provider("jni").build());
    }

    /**
     * Selects an adapter using explicit environment information and a replaceable terminal factory.
     */
    static ConsoleInput open(boolean isInteractive, InputStream input, PrintStream output, TerminalFactory factory) {
        if (isInteractive) {
            try {
                return createInteractive(factory);
            } catch (IOException | IllegalStateException e) {
                LOGGER.log(Level.FINE, "Interactive editing unavailable; using plain console input", e);
            }
        }
        return new ScannerConsoleInput(new Scanner(input), output);
    }

    /**
     * Respects explicitly limited terminals even when Java exposes a console for their input.
     */
    static boolean supportsEditing(boolean hasConsole, String terminalType) {
        return hasConsole && (terminalType == null || !terminalType.startsWith(Terminal.TYPE_DUMB));
    }

    /**
     * Releases a newly opened terminal if its editor cannot be initialized.
     */
    private static ConsoleInput createInteractive(TerminalFactory factory) throws IOException {
        Terminal terminal = factory.open();
        try {
            return new JLineConsoleInput(terminal);
        } catch (RuntimeException e) {
            try {
                terminal.close();
            } catch (IOException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
    }

    /**
     * Opens a terminal, allowing initialization failures to be reproduced without native console access.
     */
    @FunctionalInterface
    interface TerminalFactory {
        /**
         * Returns a newly owned terminal.
         *
         * @return The terminal to use for editing.
         * @throws IOException If a suitable terminal cannot be opened.
         */
        Terminal open() throws IOException;
    }
}
//@@author

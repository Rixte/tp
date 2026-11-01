package mishmash.ui;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;

//@@author Rixte
/**
 * Adds terminal line editing, Tab completion, and session-only command history.
 * Names and confirmation answers are excluded from history and completion.
 */
public final class JLineConsoleInput implements ConsoleInput {
    private static final Logger LOGGER = Logger.getLogger(JLineConsoleInput.class.getName());

    private final Terminal terminal;
    private final LineReader reader;

    /**
     * Creates an editor for an already opened terminal, which this adapter owns.
     *
     * @param terminal The interactive terminal to restore and close at session end.
     */
    public JLineConsoleInput(Terminal terminal) {
        this(terminal, createReader(Objects.requireNonNull(terminal, "terminal")));
    }

    /**
     * Accepts a supplied editor so interruption and EOF handling can be tested independently of a real terminal.
     */
    JLineConsoleInput(Terminal terminal, LineReader reader) {
        this.terminal = Objects.requireNonNull(terminal, "terminal");
        this.reader = Objects.requireNonNull(reader, "reader");
    }

    @Override
    public Optional<String> readAnswer(String prompt) {
        return readLine(prompt, false);
    }

    @Override
    public Optional<String> readCommand(String prompt) {
        return readLine(prompt, true);
    }

    /**
     * Reads one line; Ctrl+C clears a command or returns a blank answer to cancel confirmation safely.
     */
    private Optional<String> readLine(String prompt, boolean isCommand) {
        reader.setVariable(LineReader.DISABLE_HISTORY, !isCommand);
        reader.setVariable(LineReader.DISABLE_COMPLETION, !isCommand);
        while (true) {
            try {
                // JLine uses percent escapes in prompts; application questions must stay literal.
                return Optional.of(reader.readLine(prompt.replace("%", "%%") + " "));
            } catch (UserInterruptException e) {
                if (!isCommand) {
                    return Optional.of("");
                }
            } catch (EndOfFileException e) {
                return Optional.empty();
            }
        }
    }

    /**
     * Configures editing without interpreting quotes, escapes, or history expansion as new command syntax.
     */
    private static LineReader createReader(Terminal terminal) {
        DefaultParser parser = new DefaultParser().quoteChars(new char[0]).escapeChars(new char[0]);
        LineReader reader = LineReaderBuilder.builder().terminal(terminal).appName("MishMash")
                .parser(parser).completer(new CommandCompleter()).build();
        reader.setOpt(LineReader.Option.DISABLE_EVENT_EXPANSION);
        return reader;
    }

    @Override
    public void close() {
        try {
            terminal.close();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not close the interactive terminal cleanly", e);
        }
    }
}
//@@author

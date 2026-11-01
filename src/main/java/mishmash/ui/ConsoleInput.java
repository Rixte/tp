package mishmash.ui;

import java.util.Optional;

//@@author Rixte
/**
 * Reads complete lines while keeping terminal editing separate from command execution.
 * An empty Optional means end of input; a present empty string means a submitted blank line.
 */
public interface ConsoleInput extends AutoCloseable {
    /**
     * Reads a name or confirmation answer without adding it to command history.
     *
     * @param prompt The question to display before reading.
     * @return The submitted answer, or empty when input ends.
     */
    Optional<String> readAnswer(String prompt);

    /**
     * Reads a command, enabling editing and completion when the console supports them.
     *
     * @param prompt The command prompt to display.
     * @return The submitted command, or empty when input ends.
     */
    default Optional<String> readCommand(String prompt) {
        return readAnswer(prompt);
    }

    /**
     * Releases resources owned by this input adapter after the application session ends.
     */
    @Override
    void close();
}
//@@author

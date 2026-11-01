package mishmash.exception;

import java.util.Optional;

/**
 * Reports an input error that can be displayed to the user.
 */
public class MishMashException extends Exception {
    /**
     * Optional command syntax, stored independently of the explanation and original cause.
     */
    private final String usage;

    /**
     * Creates an exception with a descriptive input error.
     *
     * @param message The explanation to display.
     */
    public MishMashException(String message) {
        this(message, null, null);
    }

    /**
     * Creates an input error while retaining its original cause for diagnostics.
     *
     * @param message The explanation to display.
     * @param cause The original validation failure.
     */
    public MishMashException(String message, Throwable cause) {
        this(message, null, cause);
    }

    /**
     * Creates an input error with separate command guidance and an optional original cause.
     *
     * @param message The explanation without usage instructions, or null to use a generic explanation.
     * @param usage The command guidance to display, or null to use the application's general help.
     * @param cause The original failure, or null if no underlying exception exists.
     * @throws IllegalArgumentException If supplied usage is blank.
     */
    public MishMashException(String message, String usage, Throwable cause) {
        // Throwable.getMessage() may be null when wrapping a validation failure.
        super(message == null ? "Command could not be completed." : message, cause);
        if (usage != null && usage.isBlank()) {
            throw new IllegalArgumentException("Command usage must not be blank.");
        }
        this.usage = usage;
    }

    /**
     * Returns the explanation independently of command guidance.
     */
    public String getExplanation() {
        return super.getMessage();
    }

    /**
     * Returns specific command guidance, or empty when the application should display general help.
     */
    public Optional<String> getUsage() {
        return Optional.ofNullable(usage);
    }

    /**
     * Returns the explanation followed by supplied usage, preserving the existing display-message contract.
     */
    @Override
    public String getMessage() {
        return usage == null ? getExplanation() : getExplanation() + System.lineSeparator() + usage;
    }
}


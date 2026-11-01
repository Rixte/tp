package mishmash.command;

import java.util.Objects;

/**
 * Carries command feedback together with its explicit presentation status.
 *
 * @param feedback The nonblank user-facing message.
 * @param status The semantic outcome used by the console formatter.
 */
public record CommandResult(String feedback, Status status) {
    /** Describes how command feedback should be presented without inspecting its text or command class. */
    public enum Status {
        /** Marks a completed state-changing action. */
        DONE,
        /** Marks an expected no-op or empty state. */
        NOTE,
        /** Marks a completed action that needs the user's attention. */
        WARNING,
        /** Marks ordinary read-only information. */
        INFO,
        /** Marks the complete help manual. */
        HELP
    }

    /**
     * Validates a command result at its construction boundary.
     *
     * @throws NullPointerException If feedback or status is null.
     * @throws IllegalArgumentException If feedback is blank.
     */
    public CommandResult {
        Objects.requireNonNull(feedback, "feedback");
        Objects.requireNonNull(status, "status");
        if (feedback.isBlank()) {
            throw new IllegalArgumentException("Command feedback must not be blank.");
        }
    }

    /** Returns a successful state-changing result. */
    public static CommandResult done(String feedback) {
        return new CommandResult(feedback, Status.DONE);
    }

    /** Returns an expected no-op or empty-state result. */
    public static CommandResult note(String feedback) {
        return new CommandResult(feedback, Status.NOTE);
    }

    /** Returns a completed result that needs attention. */
    public static CommandResult warning(String feedback) {
        return new CommandResult(feedback, Status.WARNING);
    }

    /** Returns ordinary read-only information. */
    public static CommandResult info(String feedback) {
        return new CommandResult(feedback, Status.INFO);
    }

    /** Returns the application help manual. */
    public static CommandResult help(String feedback) {
        return new CommandResult(feedback, Status.HELP);
    }
}

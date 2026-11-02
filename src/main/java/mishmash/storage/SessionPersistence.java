package mishmash.storage;

import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import mishmash.command.CommandResult;
import mishmash.model.ApplicationState;

/**
 * Coordinates autosave and retries without letting disk failures discard in-memory changes.
 */
public final class SessionPersistence {
    private static final Logger LOGGER = Logger.getLogger(SessionPersistence.class.getName());
    private final ApplicationState state;
    private final SessionStorage storage;
    private boolean isDirty;

    /**
     * Creates an autosave coordinator borrowing the state and storage owned by the launcher.
     */
    public SessionPersistence(ApplicationState state, SessionStorage storage) {
        this.state = Objects.requireNonNull(state, "state");
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    /**
     * Saves completed mutations and turns save failures into explicit warning feedback.
     */
    public CommandResult afterCommand(CommandResult result) {
        if (result.status() == CommandResult.Status.DONE || result.status() == CommandResult.Status.WARNING) {
            isDirty = true;
            Optional<String> warning = flush();
            if (warning.isPresent()) {
                return CommandResult.warning(result.feedback() + System.lineSeparator() + warning.get());
            }
        }
        return result;
    }

    /**
     * Retries unsaved changes, returning a warning only if data still cannot be saved.
     */
    public Optional<String> flush() {
        if (!isDirty) {
            return Optional.empty();
        }
        try {
            storage.save(state);
            isDirty = false;
            return Optional.empty();
        } catch (StorageException e) {
            LOGGER.log(Level.WARNING, "Application changes remain unsaved", e);
            return Optional.of("Your changes are kept in memory but could not be saved. "
                    + "MishMash will retry after the next change and before exit. "
                    + "Check that the data folder is writable and has free space.");
        }
    }
}

package mishmash.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import mishmash.command.CommandResult;
import mishmash.model.ApplicationState;

/** Verifies failed saves retain state and retry without treating no-ops as mutations. */
class SessionPersistenceTest {
    @Test
    void afterCommand_failedSaveWarnsAndNextMutationRetries() {
        ApplicationState state = new ApplicationState();
        FailingStorage storage = new FailingStorage();
        SessionPersistence persistence = new SessionPersistence(state, storage);
        CommandResult result = persistence.afterCommand(CommandResult.done("Added."));
        assertEquals(CommandResult.Status.WARNING, result.status());
        assertTrue(result.feedback().contains("could not be saved"));
        assertSame(state, storage.lastState);
        persistence.afterCommand(CommandResult.note("No change."));
        persistence.afterCommand(CommandResult.info("Information."));
        assertEquals(1, storage.attempts);
        storage.shouldFail = false;
        assertEquals(CommandResult.Status.DONE,
                persistence.afterCommand(CommandResult.done("Changed.")).status());
        persistence.flush();
        assertEquals(2, storage.attempts);
    }

    @Test
    void flush_dirtyWarningOutcomeRetriesAtShutdown() {
        FailingStorage storage = new FailingStorage();
        SessionPersistence persistence = new SessionPersistence(new ApplicationState(), storage);
        persistence.afterCommand(CommandResult.warning("Plan cleared."));
        assertTrue(persistence.flush().isPresent());
        storage.shouldFail = false;
        assertTrue(persistence.flush().isEmpty());
        assertEquals(3, storage.attempts);
    }

    /** Simulates disk failure while recording save attempts and the actual model reference. */
    private static class FailingStorage implements SessionStorage {
        private int attempts;
        private boolean shouldFail = true;
        private ApplicationState lastState;

        @Override
        public LoadResult load() {
            return LoadResult.normal(new ApplicationState());
        }

        @Override
        public void save(ApplicationState state) throws StorageException {
            attempts++;
            lastState = state;
            if (shouldFail) {
                throw new StorageException("Disk unavailable");
            }
        }

        @Override
        public void close() {
        }
    }
}

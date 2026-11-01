package mishmash.storage;

import mishmash.model.ApplicationState;

/**
 * Loads and saves complete application snapshots while owning any storage resources or locks.
 */
public interface SessionStorage extends AutoCloseable {
    /** Loads a validated snapshot or an empty state when no saved data exists. */
    LoadResult load() throws StorageException;

    /** Saves a complete validated snapshot without exposing a partially written primary file. */
    void save(ApplicationState state) throws StorageException;

    /** Releases storage resources and the process-lifetime data lock. */
    @Override
    void close() throws StorageException;
}

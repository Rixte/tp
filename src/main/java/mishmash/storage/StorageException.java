package mishmash.storage;

/**
 * Reports a recoverable or startup-blocking persistence failure without exposing filesystem details to users.
 */
public class StorageException extends Exception {
    /** Creates a storage failure with an explanation and original cause. */
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }

    /** Creates a storage failure with an explanation. */
    public StorageException(String message) {
        super(message);
    }
}

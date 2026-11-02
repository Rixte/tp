package mishmash.storage;

import java.util.Objects;
import java.util.Optional;

import mishmash.model.ApplicationState;

/**
 * Carries loaded application state and optional recovery information for the user.
 *
 * @param state The fully validated state to use.
 * @param warning A recovery warning, or empty for an ordinary load.
 */
public record LoadResult(ApplicationState state, Optional<String> warning) {
    /**
     * Validates the loaded result and copies no mutable application data.
     */
    public LoadResult {
        Objects.requireNonNull(state, "state");
        warning = Objects.requireNonNull(warning, "warning");
        warning.ifPresent(message -> {
            if (message.isBlank()) {
                throw new IllegalArgumentException("Storage warnings must not be blank.");
            }
        });
    }

    /**
     * Returns a normal load without a warning.
     */
    public static LoadResult normal(ApplicationState state) {
        return new LoadResult(state, Optional.empty());
    }

    /**
     * Returns a recovered or reset load with a user-facing warning.
     */
    public static LoadResult recovered(ApplicationState state, String warning) {
        return new LoadResult(state, Optional.of(Objects.requireNonNull(warning, "warning")));
    }
}

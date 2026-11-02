package mishmash.logic;

import java.util.Objects;

//@@author GauthamNandi
/**
 * Reports that a plan cannot be generated from the current recipes, which the user can fix.
 *
 * <p>Having its own type lets {@code generate-plan} tell this failure, which deserves advice, apart from
 * any other {@link IllegalArgumentException}, which is a bug. It extends that exception so that existing
 * callers, such as the dietary repair in {@code set-diet}, keep working unchanged.</p>
 */
public class PlanGenerationException extends IllegalArgumentException {
    /**
     * Creates the failure with the explanation shown to the user.
     *
     * @param message The explanation to display.
     * @throws NullPointerException If the message is null.
     */
    public PlanGenerationException(String message) {
        super(Objects.requireNonNull(message, "message"));
    }
}
//@@author

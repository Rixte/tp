package mishmash.parser;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import mishmash.model.Nutrient;
import mishmash.model.TargetChanges;

//@@author neil0618
/**
 * Parses the arguments of {@code set-targets}.
 *
 * <p>Each argument is {@code PREFIX/VALUE}, where the prefix is {@code cal}, {@code p}, {@code c}
 * or {@code f} and the value is a positive whole number, or {@code none} to remove that target.
 * Arguments may appear in any order, and nutrients that are not mentioned keep their saved targets.</p>
 */
public class SetTargetsParser {
    public static final String USAGE = "Format: set-targets [cal/CALORIES|none] [p/PROTEIN|none]"
            + " [c/CARBS|none] [f/FATS|none]";

    /** Word that removes a target, matching {@code food/none} and {@code diet/none}. */
    private static final String CLEAR_VALUE = "none";

    /**
     * Parses the text after the command word, e.g. {@code "cal/2000 p/150 f/none"}.
     *
     * @param arguments The arguments without the leading {@code set-targets}; null is treated as empty.
     * @return The validated changes.
     * @throws IllegalArgumentException If nothing is supplied, a prefix is unknown or repeated, or a value
     *         is neither a positive whole number nor {@code none}.
     */
    public TargetChanges parse(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            throw new IllegalArgumentException("Supply at least one daily target to set or clear.");
        }
        Map<Nutrient, Optional<Integer>> changes = new EnumMap<>(Nutrient.class);
        for (String token : arguments.trim().split("\\s+")) {
            readToken(token, changes);
        }
        return new TargetChanges(changes);
    }

    /**
     * Reads one {@code PREFIX/VALUE} token into the changes collected so far.
     */
    private static void readToken(String token, Map<Nutrient, Optional<Integer>> changes) {
        int separator = token.indexOf('/');
        Nutrient nutrient = separator < 1 ? null : findNutrient(token.substring(0, separator));
        if (nutrient == null) {
            throw new IllegalArgumentException("Unrecognised parameter: " + token);
        }
        if (changes.containsKey(nutrient)) {
            throw new IllegalArgumentException("Parameter " + nutrient.getPrefix() + "/ was given more than once.");
        }
        changes.put(nutrient, parseValue(token.substring(separator + 1), nutrient));
    }

    /**
     * Returns the nutrient whose prefix is given (case-sensitive, like every other prefix), or null.
     */
    private static Nutrient findNutrient(String prefix) {
        for (Nutrient nutrient : Nutrient.values()) {
            if (nutrient.getPrefix().equals(prefix)) {
                return nutrient;
            }
        }
        return null;
    }

    /**
     * Returns the new target, or an empty Optional if the value asks to clear it.
     */
    private static Optional<Integer> parseValue(String value, Nutrient nutrient) {
        String prefix = nutrient.getPrefix() + "/";
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Missing value after " + prefix);
        }
        if (value.equalsIgnoreCase(CLEAR_VALUE)) {
            return Optional.empty();
        }
        String error = nutrient.getLabel() + " target (" + prefix + ") must be a positive whole number"
                + " from 1 to " + Integer.MAX_VALUE + ", or 'none'.";
        if (!value.matches("[0-9]+")) {
            throw new IllegalArgumentException(error);
        }
        try {
            int target = Integer.parseInt(value);
            if (target < 1) {
                throw new IllegalArgumentException(error);
            }
            return Optional.of(target);
        } catch (NumberFormatException e) {
            // Digits only, so the only failure is a value above Integer.MAX_VALUE.
            throw new IllegalArgumentException(error, e);
        }
    }
}
//@@author

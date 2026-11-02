package mishmash.parser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mishmash.model.NutritionTargets;

//@@author Rixte
/**
 * Parses explicit daily targets for check-shortfall without modifying the targets used for generation.
 */
public class CheckShortfallParser {
    private static final String PREFIX_CALORIES = "cal";
    private static final String PREFIX_PROTEIN = "p";
    private static final String PREFIX_CARBS = "c";
    private static final String PREFIX_FATS = "f";
    private static final List<String> SUPPORTED_PREFIXES = List.of(
            PREFIX_CALORIES, PREFIX_PROTEIN, PREFIX_CARBS, PREFIX_FATS);

    /**
     * Reads one or more targets in any order; omitted nutrients are not checked.
     *
     * @param arguments Text after check-shortfall, such as {@code "cal/2000 p/100"}.
     * @return Validated positive integer targets.
     * @throws IllegalArgumentException If no targets are given, or any argument is invalid or repeated.
     */
    public NutritionTargets parse(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            throw new IllegalArgumentException("Supply at least one daily nutrition target.");
        }

        Map<String, Integer> values = new HashMap<>();
        for (String token : arguments.trim().split("\\s+")) {
            readTarget(token, values);
        }

        return new NutritionTargets(values.get(PREFIX_CALORIES), values.get(PREFIX_PROTEIN),
                values.get(PREFIX_CARBS), values.get(PREFIX_FATS));
    }

    /**
     * Reads one unique prefixed target into the request's values.
     *
     * @param token A complete argument such as {@code "cal/2000"}.
     * @param values The already validated targets, keyed by prefix without the slash.
     * @throws IllegalArgumentException If the prefix is unknown, duplicated, or has an invalid value.
     */
    private void readTarget(String token, Map<String, Integer> values) {
        int separator = token.indexOf('/');
        if (separator < 1) {
            throw new IllegalArgumentException("Unrecognised parameter: " + token);
        }

        String prefix = token.substring(0, separator);
        if (!SUPPORTED_PREFIXES.contains(prefix)) {
            throw new IllegalArgumentException("Unrecognised parameter: " + token);
        }
        if (values.containsKey(prefix)) {
            throw new IllegalArgumentException("Parameter " + prefix + "/ was given more than once.");
        }

        String value = token.substring(separator + 1);
        values.put(prefix, parseTarget(value, prefix));
    }

    /**
     * Returns a validated positive integer target.
     *
     * @param value The digit string following a target prefix.
     * @param prefix The prefix to identify in validation feedback.
     * @return The target in the range 1 to Integer.MAX_VALUE.
     * @throws IllegalArgumentException If the value is missing, malformed, non-positive, or too large.
     */
    private int parseTarget(String value, String prefix) {
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Missing value after " + prefix + "/.");
        }

        String error = "Target for " + prefix + "/ must be a positive whole number from 1 to "
                + Integer.MAX_VALUE + ".";
        if (!value.matches("[0-9]+")) {
            throw new IllegalArgumentException(error);
        }

        try {
            int target = Integer.parseInt(value);
            if (target < 1) {
                throw new IllegalArgumentException(error);
            }
            return target;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(error, e);
        }
    }
}
//@@author

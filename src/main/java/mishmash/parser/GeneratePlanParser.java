package mishmash.parser;

import java.util.HashMap;
import java.util.Map;

import mishmash.logic.MealPlanGenerator;
import mishmash.model.GeneratePlanRequest;
import mishmash.model.NutritionTargets;

/**
 * Parses the arguments of the {@code generate-plan} command.
 *
 * <p>{@value #USAGE}. Parameters are separated by spaces and may appear in any
 * order. {@code d/} is required; the nutrition targets are optional positive whole numbers.</p>
 */
public class GeneratePlanParser {
    public static final String COMMAND_WORD = "generate-plan";
    public static final String USAGE = "Format: generate-plan d/DAYS [cal/DAILY_CALORIES] [p/DAILY_PROTEIN]"
            + " [c/DAILY_CARBS] [f/DAILY_FATS]";

    private static final String PREFIX_DAYS = "d/";
    private static final String PREFIX_CALORIES = "cal/";
    private static final String PREFIX_PROTEIN = "p/";
    private static final String PREFIX_CARBS = "c/";
    private static final String PREFIX_FATS = "f/";
    private static final String[] PREFIXES = {PREFIX_DAYS, PREFIX_CALORIES, PREFIX_PROTEIN, PREFIX_CARBS, PREFIX_FATS};

    /**
     * Parses the text after the command word, e.g. {@code "d/3 cal/2000 p/120"}.
     *
     * @param args The arguments, without the leading {@code generate-plan}; null is treated as empty.
     * @return The validated request.
     * @throws IllegalArgumentException If the syntax is invalid, a value is not a valid number,
     *         or the number of days is outside the supported range.
     */
    public GeneratePlanRequest parse(String args) {
        Map<String, String> values = splitIntoValues(args);

        if (!values.containsKey(PREFIX_DAYS)) {
            throw new IllegalArgumentException("Missing number of days.");
        }
        int days = parseDays(values.get(PREFIX_DAYS));
        NutritionTargets targets = new NutritionTargets(
                parseTarget(values.get(PREFIX_CALORIES), "Daily calories"),
                parseTarget(values.get(PREFIX_PROTEIN), "Daily protein"),
                parseTarget(values.get(PREFIX_CARBS), "Daily carbs"),
                parseTarget(values.get(PREFIX_FATS), "Daily fats"));
        return new GeneratePlanRequest(days, targets);
    }

    /**
     * Splits {@code "d/3 cal/2000"} into a map from prefix to value, e.g. {@code {d/=3, cal/=2000}}.
     */
    private static Map<String, String> splitIntoValues(String args) {
        Map<String, String> values = new HashMap<>();
        if (args == null || args.isBlank()) {
            return values;
        }
        for (String token : args.trim().split("\\s+")) {
            String prefix = findPrefix(token);
            if (prefix == null) {
                throw new IllegalArgumentException("Unrecognised parameter: " + token);
            }
            if (values.containsKey(prefix)) {
                throw new IllegalArgumentException("Parameter " + prefix + " was given more than once.");
            }
            String value = token.substring(prefix.length());
            if (value.isEmpty()) {
                throw new IllegalArgumentException("Missing value after " + prefix);
            }
            values.put(prefix, value);
        }
        return values;
    }

    /**
     * Returns the prefix that the token starts with, or null if it starts with none of them.
     */
    private static String findPrefix(String token) {
        for (String prefix : PREFIXES) {
            if (token.startsWith(prefix)) {
                return prefix;
            }
        }
        return null;
    }

    /**
     * Parses the requested number of days and checks it against the generator's supported range.
     *
     * @param value The value supplied after d/.
     * @return The supported number of days.
     * @throws IllegalArgumentException If the value is not an integer in the supported range.
     */
    private static int parseDays(String value) {
        String error = "DAYS must be a whole number from " + MealPlanGenerator.MIN_DAYS
                + " to " + MealPlanGenerator.MAX_DAYS + ".";
        if (!isAllDigits(value)) {
            throw new IllegalArgumentException(error);
        }
        int days;
        try {
            days = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(error, e);
        }
        if (days < MealPlanGenerator.MIN_DAYS || days > MealPlanGenerator.MAX_DAYS) {
            throw new IllegalArgumentException(error);
        }
        return days;
    }

    /**
     * Returns the parsed target, or null if it was not supplied.
     */
    private static Integer parseTarget(String value, String label) {
        if (value == null) {
            return null;
        }
        String error = label + " must be a positive whole number from 1 to " + Integer.MAX_VALUE + ".";
        if (!isAllDigits(value)) {
            throw new IllegalArgumentException(error);
        }
        int target;
        try {
            target = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            // Only digits reach this point, so the number is valid but too large for an int.
            throw new IllegalArgumentException(label + " must not exceed " + Integer.MAX_VALUE + ".", e);
        }
        if (target <= 0) {
            throw new IllegalArgumentException(error);
        }
        return target;
    }

    /**
     * Returns whether the text is only the digits 0 to 9. {@link Integer#parseInt} alone also accepts
     * a leading sign and digits from other scripts, which the command format does not allow.
     */
    private static boolean isAllDigits(String value) {
        return value.matches("[0-9]+");
    }
}

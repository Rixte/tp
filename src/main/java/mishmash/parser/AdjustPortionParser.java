package mishmash.parser;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mishmash.command.AdjustPortionCommand;
import mishmash.model.PortionAdjustment;

/**
 * Parses the day, meal, and whole-recipe portion count for {@code adjust-portion}.
 */
public final class AdjustPortionParser {
    /** Describes every required field of a portion adjustment. */
    public static final String USAGE = AdjustPortionCommand.USAGE;

    private static final Pattern PREFIX_PATTERN = Pattern.compile("(?<!\\S)([^\\s/]+)/");

    /**
     * Parses exactly one value for each required field, in any order.
     *
     * @param arguments The text following the command word.
     * @return Validated one-based positions and a positive portion count.
     * @throws IllegalArgumentException If a field is missing, repeated, unknown, or not a positive integer.
     */
    public PortionAdjustment parse(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            throw new IllegalArgumentException("Day, meal, and portion count are required.");
        }

        Map<String, String> fields = collectFields(arguments);
        requireField(fields, "d", "Day number");
        requireField(fields, "m", "Meal number");
        requireField(fields, "portions", "Portion count");
        return new PortionAdjustment(
                parsePositiveInteger(fields.get("d"), "Day number"),
                parsePositiveInteger(fields.get("m"), "Meal number"),
                parsePositiveInteger(fields.get("portions"), "Portion count"));
    }

    /**
     * Collects prefixed values while rejecting text outside a field and duplicate prefixes.
     */
    private Map<String, String> collectFields(String arguments) {
        Matcher matcher = PREFIX_PATTERN.matcher(arguments);
        if (!matcher.find() || !arguments.substring(0, matcher.start()).isBlank()) {
            throw new IllegalArgumentException("Expected prefixed adjust-portion arguments.");
        }

        Map<String, String> fields = new HashMap<>();
        String prefix = matcher.group(1);
        int valueStart = matcher.end();
        while (matcher.find()) {
            storeField(fields, prefix, arguments.substring(valueStart, matcher.start()).trim());
            prefix = matcher.group(1);
            valueStart = matcher.end();
        }
        storeField(fields, prefix, arguments.substring(valueStart).trim());
        return fields;
    }

    /**
     * Stores one supported, nonempty value and rejects duplicate or unknown prefixes.
     */
    private void storeField(Map<String, String> fields, String prefix, String value) {
        if (!prefix.equals("d") && !prefix.equals("m") && !prefix.equals("portions")) {
            throw new IllegalArgumentException("Unknown prefix: " + prefix + "/.");
        }
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Value for " + prefix + "/ must not be empty.");
        }
        if (fields.putIfAbsent(prefix, value) != null) {
            throw new IllegalArgumentException("Prefix " + prefix + "/ must not be repeated.");
        }
    }

    /**
     * Requires one named field after all supplied fields have been collected.
     */
    private void requireField(Map<String, String> fields, String prefix, String fieldName) {
        if (!fields.containsKey(prefix)) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
    }

    /**
     * Parses a digits-only value and reports overflow independently from other invalid syntax.
     */
    private int parsePositiveInteger(String value, String fieldName) {
        if (!value.matches("[0-9]+")) {
            throw new IllegalArgumentException(fieldName + " must be a positive whole number.");
        }

        int parsedValue;
        try {
            parsedValue = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " must not exceed " + Integer.MAX_VALUE + ".", e);
        }
        if (parsedValue < 1) {
            throw new IllegalArgumentException(fieldName + " must be at least 1.");
        }
        return parsedValue;
    }
}

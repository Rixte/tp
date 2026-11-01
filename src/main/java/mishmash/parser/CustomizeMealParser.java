package mishmash.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mishmash.command.CustomizeMealCommand;
import mishmash.model.GroceryItem;
import mishmash.model.IngredientCustomization;

/**
 * Parses one planned-meal ingredient replacement without reading application state.
 */
public final class CustomizeMealParser {
    /** Describes the selected meal and repeated replacement ingredient fields. */
    public static final String USAGE = CustomizeMealCommand.USAGE;

    private static final Pattern PREFIX_PATTERN = Pattern.compile("(?<!\\S)([^\\s/]+)/");

    /**
     * Parses required day and meal positions plus a complete nonempty ingredient replacement.
     *
     * @param arguments The text following the command word.
     * @return The validated immutable customization request.
     * @throws IllegalArgumentException If syntax, positions, or an ingredient is invalid.
     */
    public IngredientCustomization parse(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            throw new IllegalArgumentException("Day, meal, and at least one ingredient are required.");
        }

        Map<String, String> fields = new HashMap<>();
        List<GroceryItem> ingredients = new ArrayList<>();
        collectFields(arguments, fields, ingredients);
        requireField(fields, "d", "Day number");
        requireField(fields, "m", "Meal number");
        if (ingredients.isEmpty()) {
            throw new IllegalArgumentException("At least one replacement i/ ingredient is required.");
        }
        return new IngredientCustomization(
                parsePositiveInteger(fields.get("d"), "Day number"),
                parsePositiveInteger(fields.get("m"), "Meal number"),
                ingredients);
    }

    /**
     * Splits prefixed values without allowing unprefixed text before the first field.
     */
    private void collectFields(String arguments, Map<String, String> fields, List<GroceryItem> ingredients) {
        Matcher matcher = PREFIX_PATTERN.matcher(arguments);
        if (!matcher.find() || !arguments.substring(0, matcher.start()).isBlank()) {
            throw new IllegalArgumentException("Expected prefixed customize-meal arguments.");
        }

        String prefix = matcher.group(1);
        int valueStart = matcher.end();
        while (matcher.find()) {
            storeField(fields, ingredients, prefix, arguments.substring(valueStart, matcher.start()).trim());
            prefix = matcher.group(1);
            valueStart = matcher.end();
        }
        storeField(fields, ingredients, prefix, arguments.substring(valueStart).trim());
    }

    /**
     * Stores one position or ingredient while rejecting unknown, blank, and repeated position fields.
     */
    private void storeField(Map<String, String> fields, List<GroceryItem> ingredients,
            String prefix, String value) {
        if (value.isBlank()) {
            throw new IllegalArgumentException("Value for " + prefix + "/ must not be blank.");
        }
        if (value.contains("/")) {
            throw new IllegalArgumentException("Names and units must not contain '/'. Separate fields with spaces.");
        }
        if (prefix.equals("i")) {
            ingredients.add(new IngredientParser().parse(value));
            return;
        }
        if (!prefix.equals("d") && !prefix.equals("m")) {
            throw new IllegalArgumentException("Unknown prefix: " + prefix + "/.");
        }
        if (fields.putIfAbsent(prefix, value) != null) {
            throw new IllegalArgumentException("Prefix " + prefix + "/ must not be repeated.");
        }
    }

    /**
     * Requires one selected position field.
     */
    private void requireField(Map<String, String> fields, String prefix, String fieldName) {
        if (!fields.containsKey(prefix)) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
    }

    /**
     * Parses a digits-only one-based position with explicit overflow reporting.
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

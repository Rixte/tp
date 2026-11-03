package mishmash.parser;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mishmash.model.MealEntry;
import mishmash.model.MealEntryChanges;

//@@author GauthamNandi
/**
 * Parses the position, {@code date/} and {@code s/} arguments shared by the meal-tracking commands.
 *
 * <p>Today's date is passed in, so tests can fix it instead of depending on the system clock.</p>
 */
public final class MealEntryParser {
    /** The date format shown to users; it sorts naturally and has no day/month ambiguity. */
    public static final String DATE_FORMAT = "YYYY-MM-DD";

    /** The earliest meal date accepted, which catches typos such as 1026 for 2026. */
    public static final LocalDate EARLIEST_DATE = LocalDate.of(1900, 1, 1);

    private static final String DATE_PREFIX = "date";
    private static final String SERVINGS_PREFIX = "s";

    // A prefix starts with a letter, so "1/2" stays a bad position while "x/3" is reported as an unknown field.
    private static final Pattern FIRST_FIELD_PATTERN = Pattern.compile("(?<!\\S)[A-Za-z][^\\s/]*/");
    private static final Pattern FIELD_PATTERN = Pattern.compile("(?<!\\S)([^\\s/]+)/");
    private static final Pattern DATE_PATTERN = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");

    private final LocalDate today;

    /**
     * Creates a parser that treats dates after the given day as being in the future.
     *
     * @param today The current date.
     * @throws NullPointerException If the date is null.
     */
    public MealEntryParser(LocalDate today) {
        this.today = Objects.requireNonNull(today, "today");
    }

    /**
     * Returns the text before the first prefixed field, which is the recipe or meal number.
     *
     * @param arguments The text after the command word.
     * @return The trimmed position text, which may be blank.
     */
    public String parsePosition(String arguments) {
        Matcher matcher = FIRST_FIELD_PATTERN.matcher(arguments);
        return (matcher.find() ? arguments.substring(0, matcher.start()) : arguments).trim();
    }

    /**
     * Parses the optional date and servings that follow the position.
     *
     * @param arguments The text after the command word, including the position.
     * @return The supplied values, with null for anything left out.
     * @throws IllegalArgumentException If a field is unknown, repeated, blank or invalid.
     */
    public MealEntryChanges parseChanges(String arguments) {
        Map<String, String> fields = collectFields(arguments);
        LocalDate date = fields.containsKey(DATE_PREFIX) ? parsePastDate(fields.get(DATE_PREFIX)) : null;
        Integer servings = fields.containsKey(SERVINGS_PREFIX) ? parseServings(fields.get(SERVINGS_PREFIX)) : null;
        return new MealEntryChanges(date, servings);
    }

    /**
     * Parses a position in the meal log, as shown by {@code view-meals}.
     *
     * @param text The position text.
     * @return The meal number, counting from 1.
     * @throws IllegalArgumentException If the text is blank or is not a positive whole number.
     */
    public int parseMealNumber(String text) {
        if (text.isBlank()) {
            throw new IllegalArgumentException("Meal number is required.");
        }
        return parsePositiveInteger(text, "Meal number", Integer.MAX_VALUE);
    }

    /**
     * Parses a calendar date. Impossible dates such as {@code 2026-02-30} are rejected.
     *
     * @param value The date text.
     * @return The parsed date.
     * @throws IllegalArgumentException If the text is not a real date in {@value #DATE_FORMAT} form.
     */
    public LocalDate parseDate(String value) {
        if (!DATE_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Date must use the format " + DATE_FORMAT + ".");
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Date " + value + " does not exist.", e);
        }
    }

    /**
     * Parses a date for a meal that has been eaten, which cannot be later than today
     * or earlier than {@link #EARLIEST_DATE}.
     */
    private LocalDate parsePastDate(String value) {
        LocalDate date = parseDate(value);
        if (date.isAfter(today)) {
            throw new IllegalArgumentException("Date must not be in the future.");
        }
        if (date.isBefore(EARLIEST_DATE)) {
            throw new IllegalArgumentException("Date must not be before " + EARLIEST_DATE + ".");
        }
        return date;
    }

    /**
     * Parses a serving count; {@link MealEntryChanges} checks that it is in range.
     */
    private int parseServings(String value) {
        return parsePositiveInteger(value, "Servings", MealEntry.MAX_SERVINGS);
    }

    /**
     * Collects the prefixed fields after the position, rejecting unknown, repeated or blank fields.
     */
    private Map<String, String> collectFields(String arguments) {
        Map<String, String> fields = new HashMap<>();
        String text = arguments.substring(parsePositionLength(arguments));
        Matcher matcher = FIELD_PATTERN.matcher(text);
        if (!matcher.find()) {
            return fields;
        }

        String prefix = matcher.group(1);
        int valueStart = matcher.end();
        while (matcher.find()) {
            storeField(fields, prefix, text.substring(valueStart, matcher.start()).trim());
            prefix = matcher.group(1);
            valueStart = matcher.end();
        }
        storeField(fields, prefix, text.substring(valueStart).trim());
        return fields;
    }

    /**
     * Returns how many leading characters belong to the position, before the first prefixed field.
     */
    private int parsePositionLength(String arguments) {
        Matcher matcher = FIRST_FIELD_PATTERN.matcher(arguments);
        return matcher.find() ? matcher.start() : arguments.length();
    }

    /**
     * Stores one supported, nonempty field that has not been supplied already.
     */
    private void storeField(Map<String, String> fields, String prefix, String value) {
        if (!prefix.equals(DATE_PREFIX) && !prefix.equals(SERVINGS_PREFIX)) {
            throw new IllegalArgumentException("Unknown field: " + prefix + "/");
        }
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Value must not be blank: " + prefix + "/");
        }
        if (fields.putIfAbsent(prefix, value) != null) {
            throw new IllegalArgumentException("Repeated field: " + prefix + "/");
        }
    }

    /**
     * Parses a digits-only value, reporting overflow separately from other invalid text.
     *
     * @param limit The largest value to name if the number is too long to fit in an int.
     */
    private int parsePositiveInteger(String value, String fieldName, int limit) {
        if (!value.matches("[0-9]+")) {
            throw new IllegalArgumentException(fieldName + " must be a single positive whole number.");
        }
        int number;
        try {
            number = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " must not exceed " + limit + ".", e);
        }
        if (number < 1) {
            throw new IllegalArgumentException(fieldName + " must be at least 1.");
        }
        return number;
    }
}
//@@author

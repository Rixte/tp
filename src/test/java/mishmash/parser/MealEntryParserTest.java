package mishmash.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import mishmash.model.MealEntryChanges;

//@@author GauthamNandi
/**
 * Checks the meal-position, date and servings syntax, using a fixed "today" so results never vary.
 */
class MealEntryParserTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 15);

    private final MealEntryParser parser = new MealEntryParser(TODAY);

    @Test
    void parsePosition_stopsAtTheFirstField() {
        assertEquals("2", parser.parsePosition(" 2 date/2026-03-01 s/3"));
        assertEquals("2", parser.parsePosition("2"));
        assertEquals("", parser.parsePosition("s/3"));
        assertEquals("1 2", parser.parsePosition("1 2"));
    }

    @Test
    void parsePosition_slashInsideNumber_staysInPositionSoItIsRejectedThere() {
        assertEquals("1/2", parser.parsePosition("1/2"));
    }

    @Test
    void parsePosition_letterPrefix_endsThePositionSoItIsReportedAsAField() {
        assertEquals("", parser.parsePosition("n/Rice"));
        assertEquals("1", parser.parsePosition("1 x/3"));
    }

    @Test
    void parseChanges_unknownPrefixAfterThePosition_namesThatField() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> parser.parseChanges("1 x/3"));

        assertEquals("Unknown field: x/", e.getMessage());
    }

    @Test
    void parseChanges_earliestSupportedDate_isAcceptedAndTheDayBeforeIsNot() {
        assertEquals(MealEntryParser.EARLIEST_DATE, parser.parseChanges("1 date/1900-01-01").date());

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> parser.parseChanges("1 date/1899-12-31"));

        assertEquals("Date must not be before 1900-01-01.", e.getMessage());
    }

    @Test
    void parseDate_anyRealDateCanStillBeViewed() {
        assertEquals(LocalDate.of(1, 1, 1), parser.parseDate("0001-01-01"));
    }

    @Test
    void parseChanges_anyOrderWithSpaces_readsBothFields() {
        assertEquals(new MealEntryChanges(LocalDate.of(2026, 3, 1), 3),
                parser.parseChanges("2 s/3 date/ 2026-03-01"));
    }

    @Test
    void parseChanges_nothingSupplied_returnsNoChange() {
        assertEquals(new MealEntryChanges(null, null), parser.parseChanges("2"));
    }

    @Test
    void parseChanges_today_isAccepted() {
        assertEquals(TODAY, parser.parseChanges("1 date/2026-03-15").date());
    }

    @Test
    void parseChanges_invalidInput_explainsTheProblem() {
        String[][] cases = {
            {"1 date/2026-03-16", "Date must not be in the future."},
            {"1 date/2026-02-30", "Date 2026-02-30 does not exist."},
            {"1 date/01-03-2026", "Date must use the format YYYY-MM-DD."},
            {"1 date/2026-3-1", "Date must use the format YYYY-MM-DD."},
            {"1 s/0", "Servings must be at least 1."},
            {"1 s/101", "Servings must be between 1 and 100."},
            {"1 s/1.5", "Servings must be a single positive whole number."},
            {"1 s/2 3", "Servings must be a single positive whole number."},
            {"1 s/99999999999", "Servings must not exceed 100."},
            {"1 s/1 s/2", "Repeated field: s/"},
            {"1 date/", "Value must not be blank: date/"},
            {"1 s/2 x/3", "Unknown field: x/"},
        };
        for (String[] testCase : cases) {
            IllegalArgumentException error =
                    assertThrows(IllegalArgumentException.class, () -> parser.parseChanges(testCase[0]));
            assertEquals(testCase[1], error.getMessage(), testCase[0]);
        }
    }

    @Test
    void parseDate_futureDate_isAllowedForViewing() {
        assertEquals(LocalDate.of(2030, 1, 1), parser.parseDate("2030-01-01"));
    }

    @Test
    void parseMealNumber_invalidInput_explainsTheProblem() {
        String[][] cases = {
            {"", "Meal number is required."},
            {"0", "Meal number must be at least 1."},
            {"x", "Meal number must be a single positive whole number."},
            {"1 2", "Meal number must be a single positive whole number."},
            {"99999999999", "Meal number must not exceed 2147483647."},
        };
        for (String[] testCase : cases) {
            IllegalArgumentException error =
                    assertThrows(IllegalArgumentException.class, () -> parser.parseMealNumber(testCase[0]));
            assertEquals(testCase[1], error.getMessage(), testCase[0]);
        }
    }
}
//@@author

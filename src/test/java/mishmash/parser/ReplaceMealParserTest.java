package mishmash.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import mishmash.model.MealReplacement;

//@@author neil0618
/**
 * Checks complete and defensive parsing of replace-meal fields.
 */
class ReplaceMealParserTest {
    private final ReplaceMealParser parser = new ReplaceMealParser();

    @Test
    void parse_validFields_acceptsAnyOrderWhitespaceAndLeadingZeros() {
        assertEquals(new MealReplacement(2, 3, 4), parser.parse("d/2 m/3 r/4"));
        assertEquals(new MealReplacement(2, 3, 4), parser.parse(" r/004  d/ 002 m/03 "));
    }

    @Test
    void parse_missingDuplicateUnknownOrUnprefixedFields_rejectsInput() {
        List<String> invalidArguments = List.of(
                "", "   ", "d/1 m/2", "d/1 r/2", "m/1 r/2",
                "d/1 d/2 m/1 r/2", "d/1 m/1 r/2 extra",
                "d/1 m/1 portions/2", "unexpected d/1 m/1 r/2", "d/ m/1 r/2");

        for (String arguments : invalidArguments) {
            assertThrows(IllegalArgumentException.class, () -> parser.parse(arguments), arguments);
        }
    }

    @Test
    void parse_nullArguments_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse(null));
    }

    @Test
    void parse_invalidRecipeNumbers_rejectsNonPositiveNonWholeAndOverflowValues() {
        List<String> invalidValues = List.of("0", "-1", "+1", "1.5", "one", "2147483648");
        for (String value : invalidValues) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> parser.parse("d/1 m/1 r/" + value), value);
            assertTrue(error.getMessage().startsWith("Recipe number"), value);
        }
    }

    @Test
    void parse_invalidDayOrMeal_namesTheOffendingField() {
        IllegalArgumentException dayError = assertThrows(IllegalArgumentException.class,
                () -> parser.parse("d/0 m/1 r/1"));
        IllegalArgumentException mealError = assertThrows(IllegalArgumentException.class,
                () -> parser.parse("d/1 m/x r/1"));

        assertTrue(dayError.getMessage().startsWith("Day number"));
        assertTrue(mealError.getMessage().startsWith("Meal number"));
    }
}
//@@author

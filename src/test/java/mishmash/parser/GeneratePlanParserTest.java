package mishmash.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import mishmash.model.GeneratePlanRequest;
import mishmash.model.NutritionTargets;

/**
 * Checks parsing of generate-plan arguments, including optional targets, boundaries, and invalid input.
 */
class GeneratePlanParserTest {
    private final GeneratePlanParser parser = new GeneratePlanParser();

    @Test
    void parse_daysOnly_noTargets() {
        GeneratePlanRequest request = parser.parse("d/3");
        assertEquals(3, request.days());
        assertFalse(request.targets().hasAnyTarget());
    }

    @Test
    void parse_allTargetsInAnyOrder_parsesEachValue() {
        GeneratePlanRequest request = parser.parse("  f/70 cal/2000   d/7 c/250 p/120 ");
        NutritionTargets targets = request.targets();
        assertEquals(7, request.days());
        assertEquals(2000, targets.getCalories());
        assertEquals(120, targets.getProtein());
        assertEquals(250, targets.getCarbs());
        assertEquals(70, targets.getFats());
    }

    @Test
    void parse_someTargets_othersAreNull() {
        NutritionTargets targets = parser.parse("d/2 p/100").targets();
        assertTrue(targets.hasAnyTarget());
        assertEquals(100, targets.getProtein());
        assertNull(targets.getCalories());
        assertNull(targets.getCarbs());
        assertNull(targets.getFats());
    }

    @Test
    void parse_boundaryDays_accepted() {
        assertEquals(1, parser.parse("d/1").days());
        assertEquals(7, parser.parse("d/7").days());
    }

    @Test
    void parse_invalidDays_throwsDaysMessage() {
        for (String args : new String[] {"d/0", "d/8", "d/-1", "d/+2", "d/abc", "d/2.5", "d/99999999999"}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parser.parse(args));
            assertEquals("DAYS must be a whole number from 1 to 7.", e.getMessage());
        }
    }

    @Test
    void parse_missingDays_throwsIllegalArgumentException() {
        for (String args : new String[] {"", "   ", "cal/2000"}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parser.parse(args));
            assertTrue(e.getMessage().startsWith("Missing number of days."));
        }
    }

    @Test
    void parse_nullArgs_throwsMissingDays() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse(null));
    }

    @Test
    void parse_unknownParameter_throwsIllegalArgumentException() {
        for (String args : new String[] {"d/3 x/5", "d/3 hello", "d/3 calories/2000"}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parser.parse(args));
            assertTrue(e.getMessage().startsWith("Unrecognised parameter"));
        }
    }

    @Test
    void parse_duplicateParameter_throwsIllegalArgumentException() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parser.parse("d/3 d/4"));
        assertTrue(e.getMessage().contains("more than once"));
    }

    @Test
    void parse_emptyValue_throwsIllegalArgumentException() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parser.parse("d/3 cal/"));
        assertTrue(e.getMessage().startsWith("Missing value after cal/"));
    }

    @Test
    void parse_invalidTarget_throwsIllegalArgumentException() {
        for (String args : new String[] {"d/3 cal/0", "d/3 p/-5", "d/3 c/abc", "d/3 f/1.5", "d/3 cal/+1700"}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parser.parse(args));
            assertTrue(e.getMessage().endsWith("must be a positive whole number from 1 to 2147483647."));
        }
    }

    //@@author GauthamNandi
    @Test
    void parse_signsAndNonAsciiDigits_rejected() {
        String arabicThree = "\u0663";
        for (String days : new String[] {"+3", "-3", arabicThree}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> parser.parse("d/" + days));
            assertTrue(e.getMessage().startsWith("DAYS must be a whole number"), e.getMessage());
        }
        for (String calories : new String[] {"+900", arabicThree}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> parser.parse("d/1 cal/" + calories));
            assertEquals("Daily calories must be a positive whole number from 1 to 2147483647.", e.getMessage());
        }
    }

    @Test
    void parse_targetTooLargeForAnInt_saysSoRatherThanThatItIsNotANumber() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> parser.parse("d/1 cal/99999999999"));

        assertEquals("Daily calories must not exceed 2147483647.", e.getMessage());
    }
    //@@author
}

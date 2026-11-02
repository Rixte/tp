package mishmash.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import mishmash.model.NutritionTargets;

//@@author neil0618
/**
 * Checks the syntax of set-targets arguments, including clearing with none and every rejected form.
 */
class SetTargetsParserTest {
    private final SetTargetsParser parser = new SetTargetsParser();
    private final NutritionTargets base = new NutritionTargets(2000, 150, 200, 70);

    private NutritionTargets apply(String arguments) {
        return parser.parse(arguments).applyTo(base);
    }

    private String errorOf(String arguments) {
        return assertThrows(IllegalArgumentException.class, () -> parser.parse(arguments)).getMessage();
    }

    @Test
    void parse_allFourTargets_replacesEach() {
        NutritionTargets result = parser.parse("cal/1800 p/120 c/180 f/60").applyTo(NutritionTargets.none());

        assertEquals(1800, result.getCalories());
        assertEquals(120, result.getProtein());
        assertEquals(180, result.getCarbs());
        assertEquals(60, result.getFats());
    }

    @Test
    void parse_oneTarget_leavesOthersAlone() {
        NutritionTargets result = apply("p/99");

        assertEquals(99, result.getProtein());
        assertEquals(2000, result.getCalories());
        assertEquals(200, result.getCarbs());
        assertEquals(70, result.getFats());
    }

    @Test
    void parse_anyOrderAndExtraSpaces() {
        NutritionTargets result = apply("   f/10    cal/1500  ");

        assertEquals(10, result.getFats());
        assertEquals(1500, result.getCalories());
    }

    @Test
    void parse_none_clearsThatTargetOnly() {
        NutritionTargets result = apply("cal/none");

        assertNull(result.getCalories());
        assertEquals(150, result.getProtein());
    }

    @Test
    void parse_noneIgnoresCase() {
        assertNull(apply("f/NONE").getFats());
        assertNull(apply("f/None").getFats());
    }

    @Test
    void parse_setAndClearTogether() {
        NutritionTargets result = apply("cal/1700 c/none");

        assertEquals(1700, result.getCalories());
        assertNull(result.getCarbs());
    }

    @Test
    void parse_leadingZerosAndLargestInt_accepted() {
        assertEquals(5, apply("p/005").getProtein());
        assertEquals(Integer.MAX_VALUE, apply("cal/" + Integer.MAX_VALUE).getCalories());
    }

    @Test
    void parse_noArguments_rejected() {
        assertTrue(errorOf("").contains("at least one"));
        assertTrue(errorOf("   ").contains("at least one"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> parser.parse(null))
                .getMessage().contains("at least one"));
    }

    @Test
    void parse_badValues_rejected() {
        for (String bad : new String[] {"cal/0", "cal/-5", "cal/abc", "cal/1.5", "cal/1e3", "cal/+5",
                "cal/2147483648", "cal/99999999999999999999", "cal/n0ne", "cal/none1"}) {
            String message = errorOf(bad);
            assertTrue(message.contains("positive whole number"), bad + " -> " + message);
        }
    }

    @Test
    void parse_errorNamesTheNutrient() {
        assertTrue(errorOf("p/x").startsWith("Protein target (p/)"));
        assertTrue(errorOf("c/x").startsWith("Carbs target (c/)"));
        assertTrue(errorOf("f/x").startsWith("Fats target (f/)"));
        assertTrue(errorOf("cal/x").startsWith("Calories target (cal/)"));
    }

    @Test
    void parse_missingValue_rejected() {
        assertEquals("Missing value after cal/", errorOf("cal/"));
        assertEquals("Missing value after f/", errorOf("p/5 f/"));
    }

    @Test
    void parse_unknownOrMalformedParameter_rejected() {
        assertEquals("Unrecognised parameter: x/5", errorOf("x/5"));
        assertEquals("Unrecognised parameter: 2000", errorOf("2000"));
        assertEquals("Unrecognised parameter: cal2000", errorOf("cal2000"));
        assertEquals("Unrecognised parameter: /5", errorOf("/5"));
        assertEquals("Unrecognised parameter: d/3", errorOf("d/3"));
        assertEquals("Unrecognised parameter: CAL/5", errorOf("CAL/5"));
    }

    @Test
    void parse_repeatedPrefix_rejected() {
        assertEquals("Parameter cal/ was given more than once.", errorOf("cal/1 cal/2"));
        assertEquals("Parameter p/ was given more than once.", errorOf("p/1 p/none"));
    }

    @Test
    void parse_oneBadTokenRejectsTheWholeCommand() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("cal/2000 p/oops"));
    }
}
//@@author

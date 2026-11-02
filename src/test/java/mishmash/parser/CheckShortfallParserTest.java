package mishmash.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import mishmash.model.NutritionTargets;

//@@author Rixte
/**
 * Exercises each target prefix, numerical boundaries, and malformed command arguments.
 */
class CheckShortfallParserTest {
    private final CheckShortfallParser parser = new CheckShortfallParser();

    @Test
    void parse_reorderedTargetsAndWhitespace_preservesValuesAndOmissions() {
        NutritionTargets targets = parser.parse(" \tf/60  p/100 cal/2000\t");

        assertEquals(2000, targets.getCalories());
        assertEquals(100, targets.getProtein());
        assertEquals(60, targets.getFats());
        assertNull(targets.getCarbs());
    }

    @Test
    void parse_minimumAndMaximumTargets_acceptsBoundariesAndLeadingZeros() {
        NutritionTargets minimum = parser.parse("cal/01 p/1 c/1 f/1");
        assertEquals(1, minimum.getCalories());
        assertEquals(1, minimum.getProtein());
        assertEquals(1, minimum.getCarbs());
        assertEquals(1, minimum.getFats());

        NutritionTargets maximum = parser.parse("cal/2147483647 p/2147483647 c/2147483647 f/2147483647");
        assertEquals(Integer.MAX_VALUE, maximum.getCalories());
        assertEquals(Integer.MAX_VALUE, maximum.getProtein());
        assertEquals(Integer.MAX_VALUE, maximum.getCarbs());
        assertEquals(Integer.MAX_VALUE, maximum.getFats());
    }

    @Test
    void parse_eachSingleTarget_setsOnlyRequestedNutrient() {
        NutritionTargets calorieTargets = parser.parse("cal/1");
        assertEquals(1, calorieTargets.getCalories());
        assertNull(calorieTargets.getProtein());
        assertNull(calorieTargets.getCarbs());
        assertNull(calorieTargets.getFats());

        NutritionTargets proteinTargets = parser.parse("p/1");
        assertNull(proteinTargets.getCalories());
        assertEquals(1, proteinTargets.getProtein());
        assertNull(proteinTargets.getCarbs());
        assertNull(proteinTargets.getFats());

        NutritionTargets carbTargets = parser.parse("c/1");
        assertNull(carbTargets.getCalories());
        assertNull(carbTargets.getProtein());
        assertEquals(1, carbTargets.getCarbs());
        assertNull(carbTargets.getFats());

        NutritionTargets fatTargets = parser.parse("f/1");
        assertNull(fatTargets.getCalories());
        assertNull(fatTargets.getProtein());
        assertNull(fatTargets.getCarbs());
        assertEquals(1, fatTargets.getFats());
    }

    @Test
    void parse_missingTargets_rejectsNullEmptyAndWhitespace() {
        for (String arguments : new String[]{null, "", " ", "\t\r\n"}) {
            assertThrows(IllegalArgumentException.class, () -> parser.parse(arguments));
        }
    }

    @TestFactory
    List<DynamicTest> parse_malformedValues_rejectsEveryNutrient() {
        List<String> invalidValues = List.of("", "0", "000", "-1", "+1", "1.5", "1e3", "NaN", "Infinity",
                "1_000", "1,000", "2000kcal", "\u0661", "2147483648", "99999999999999999999999", "1/2",
                "-0", "+0", "0.0", ".5", "2.", "0x10", "0b10", "1E3", "-2147483648",
                "\uff11", "\u0967", "1\u200b", "1\u00a0");
        List<DynamicTest> tests = new ArrayList<>();
        for (String prefix : List.of("cal/", "p/", "c/", "f/")) {
            for (String invalidValue : invalidValues) {
                String arguments = prefix + invalidValue;
                tests.add(DynamicTest.dynamicTest(arguments,
                        () -> {
                            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                                    () -> parser.parse(arguments));
                            String expected = invalidValue.isEmpty() ? "Missing value after " + prefix + "."
                                    : "Target for " + prefix
                                            + " must be a positive whole number from 1 to 2147483647.";
                            assertEquals(expected, error.getMessage());
                        }));
            }
        }
        return tests;
    }

    @TestFactory
    List<DynamicTest> parse_missingValue_identifiesPrefixInEveryPosition() {
        List<DynamicTest> tests = new ArrayList<>();
        for (String prefix : List.of("cal", "p", "c", "f")) {
            String otherTarget = prefix.equals("cal") ? "p/100" : "cal/2000";
            List<String> arguments = List.of(prefix + "/", prefix + "/   ",
                    prefix + "/ " + otherTarget, otherTarget + " " + prefix + "/",
                    prefix + "/ 100");
            for (String input : arguments) {
                tests.add(DynamicTest.dynamicTest("Missing value: [" + input + "]", () -> {
                    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                            () -> parser.parse(input));
                    assertEquals("Missing value after " + prefix + "/.", error.getMessage());
                }));
            }
        }
        return tests;
    }

    @TestFactory
    List<DynamicTest> parse_allTargetOrders_preservesNutrientMapping() {
        List<String> tokens = List.of("cal/2000", "p/100", "c/250", "f/60");
        List<DynamicTest> tests = new ArrayList<>();
        for (String first : tokens) {
            for (String second : tokens) {
                if (first.equals(second)) {
                    continue;
                }
                for (String third : tokens) {
                    if (third.equals(first) || third.equals(second)) {
                        continue;
                    }
                    List<String> remaining = new ArrayList<>(tokens);
                    remaining.removeAll(List.of(first, second, third));
                    String arguments = String.join(" ", first, second, third, remaining.getFirst());
                    tests.add(DynamicTest.dynamicTest(arguments, () -> {
                        NutritionTargets targets = parser.parse(arguments);
                        assertEquals(2000, targets.getCalories());
                        assertEquals(100, targets.getProtein());
                        assertEquals(250, targets.getCarbs());
                        assertEquals(60, targets.getFats());
                    }));
                }
            }
        }
        return tests;
    }

    @TestFactory
    List<DynamicTest> parse_nonAdjacentDuplicate_rejectsEvenWhenValuesMatch() {
        List<DynamicTest> tests = new ArrayList<>();
        for (String prefix : List.of("cal", "p", "c", "f")) {
            String otherTarget = prefix.equals("cal") ? "p/100" : "cal/2000";
            for (String repeatedValue : List.of("1", "2", "")) {
                String arguments = prefix + "/1 " + otherTarget + " " + prefix + "/" + repeatedValue;
                tests.add(DynamicTest.dynamicTest(arguments, () -> {
                    IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                            () -> parser.parse(arguments));
                    assertEquals("Parameter " + prefix + "/ was given more than once.", error.getMessage());
                }));
            }
        }
        return tests;
    }

    @Test
    void parse_unknownPrefixWithoutValue_reportsUnknownParameter() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> parser.parse("d/"));

        assertEquals("Unrecognised parameter: d/", error.getMessage());
    }

    @Test
    void parse_consecutiveCalls_doesNotRetainSuccessfulOrRejectedTargets() {
        parser.parse("cal/2000 p/100");
        assertThrows(IllegalArgumentException.class, () -> parser.parse("cal/1500 c/"));

        NutritionTargets next = parser.parse("f/60");

        assertNull(next.getCalories());
        assertNull(next.getProtein());
        assertNull(next.getCarbs());
        assertEquals(60, next.getFats());
    }

    @Test
    void parse_multilineWhitespaceAndPaddedMaximum_acceptsValidWholeValues() {
        NutritionTargets targets = parser.parse("\r\ncal/0002147483647\tp/00100\fc/00250\u000bf/00060\r\n");

        assertEquals(Integer.MAX_VALUE, targets.getCalories());
        assertEquals(100, targets.getProtein());
        assertEquals(250, targets.getCarbs());
        assertEquals(60, targets.getFats());
    }

    @Test
    void parse_duplicateAndUnexpectedArguments_rejectsWithoutKeepingPreviousValues() {
        for (String prefix : List.of("cal/", "p/", "c/", "f/")) {
            assertThrows(IllegalArgumentException.class, () -> parser.parse(prefix + "1 " + prefix + "2"));
        }
        for (String arguments : List.of("d/1", "CAL/2000", "P/100", "c/1 extra", "cal2000", "/2000",
                "cal/2000p/100", "cal/2000/p/100", "cal/ 2000", "cal/2000 trailing/1")) {
            assertThrows(IllegalArgumentException.class, () -> parser.parse(arguments), arguments);
        }
        NutritionTargets next = parser.parse("p/100");
        assertNull(next.getCalories());
        assertEquals(100, next.getProtein());
    }
}
//@@author

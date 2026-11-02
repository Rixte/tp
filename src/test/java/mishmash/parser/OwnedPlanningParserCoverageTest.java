package mishmash.parser;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Checks direct parser boundaries that do not pass through the main command dispatcher. */
class OwnedPlanningParserCoverageTest {
    @Test
    void adjust_nullUnprefixedAndEmptyFinalField_rejectsWithoutProducingRequest() {
        AdjustPortionParser parser = new AdjustPortionParser();
        assertThrows(IllegalArgumentException.class, () -> parser.parse(null));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("unprefixed"));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("d/1 m/1 portions/"));
    }

    @Test
    void customize_nullAndUnprefixedInput_rejectsWithoutProducingRequest() {
        CustomizeMealParser parser = new CustomizeMealParser();
        assertThrows(IllegalArgumentException.class, () -> parser.parse(null));
        assertThrows(IllegalArgumentException.class, () -> parser.parse("unprefixed"));
    }
}

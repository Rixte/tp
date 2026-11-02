package mishmash.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import mishmash.model.GroceryItem;

/** Checks the shared ingredient grammar directly, including invalid names and exact decimal precision. */
class IngredientParserTest {
    private final IngredientParser parser = new IngredientParser();

    @Test
    void parse_validIngredient_preservesDecimalScaleCaseAndInternalNameWhitespace() {
        GroceryItem item = parser.parse(" Brown  Rice : 12345678901234567890.00000100 Kg");
        assertEquals("Brown  Rice", item.getName());
        assertEquals(new BigDecimal("12345678901234567890.00000100"), item.getQuantity());
        assertEquals("Kg", item.getUnit());
        assertEquals(new BigDecimal("0.000000001"), parser.parse("Oil:0.000000001ml").getQuantity());
    }

    @Test
    void parse_null_rejectsMissingInput() {
        assertThrows(NullPointerException.class, () -> parser.parse(null));
    }

    @TestFactory
    Stream<DynamicTest> parse_invalidSyntax_rejectsEntireValue() {
        return Stream.of("", "Rice", "Rice:", "Rice:1", "Rice:-1g", "Rice:+1g", "Rice:.5g", "Rice:1.g",
                "Rice:1e3g", "Rice:1,000g", "Rice:1g extra", "Rice:1g:2g", "Rice:1g/ml", "Rice:NaNg")
                .map(value -> DynamicTest.dynamicTest("invalid: [" + value + "]", () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> parser.parse(value))));
    }

    @Test
    void parse_blankNamesAndZeroQuantity_reportsRelevantConstraint() {
        for (String value : new String[]{":1g", " \t :1g"}) {
            assertEquals("Ingredient name must not be blank.",
                    assertThrows(IllegalArgumentException.class, () -> parser.parse(value)).getMessage());
        }
        for (String value : new String[]{"Rice:0g", "Rice:000.000g"}) {
            assertEquals("Ingredient quantity must be greater than zero.",
                    assertThrows(IllegalArgumentException.class, () -> parser.parse(value)).getMessage());
        }
        assertEquals("Rice", parser.parse("Rice:1g").getName());
    }
}

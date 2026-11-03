package mishmash.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** Verifies ingredient value contracts independently of parsing and grocery aggregation. */
class GroceryItemTest {
    @Test
    void constructor_validValue_trimsLabelsWithoutRoundingOrChangingCase() {
        BigDecimal quantity = new BigDecimal("12345678901234567890.00000000100");
        GroceryItem item = new GroceryItem("  Brown Rice  ", quantity, "  Kg  ");
        assertEquals("Brown Rice", item.getName());
        assertEquals("Kg", item.getUnit());
        assertSame(quantity, item.getQuantity());
        assertEquals("Brown Rice: 12345678901234567890.000000001Kg", item.toString());
    }

    @Test
    void constructor_nullValues_identifiesMissingArgument() {
        assertEquals("name", assertThrows(NullPointerException.class,
                () -> new GroceryItem(null, BigDecimal.ONE, "g")).getMessage());
        assertEquals("quantity", assertThrows(NullPointerException.class,
                () -> new GroceryItem("Rice", null, "g")).getMessage());
        assertEquals("unit", assertThrows(NullPointerException.class,
                () -> new GroceryItem("Rice", BigDecimal.ONE, null)).getMessage());
    }

    @TestFactory
    Stream<DynamicTest> constructor_blankLabelsAndNegativeQuantities_rejectsInvalidValues() {
        return Stream.of(
                DynamicTest.dynamicTest("empty name", () -> assertThrows(IllegalArgumentException.class,
                        () -> new GroceryItem("", BigDecimal.ONE, "g"))),
                DynamicTest.dynamicTest("blank name", () -> assertThrows(IllegalArgumentException.class,
                        () -> new GroceryItem(" \t ", BigDecimal.ONE, "g"))),
                DynamicTest.dynamicTest("empty unit", () -> assertThrows(IllegalArgumentException.class,
                        () -> new GroceryItem("Rice", BigDecimal.ONE, ""))),
                DynamicTest.dynamicTest("blank unit", () -> assertThrows(IllegalArgumentException.class,
                        () -> new GroceryItem("Rice", BigDecimal.ONE, " \t "))),
                DynamicTest.dynamicTest("Unicode blank name", () -> assertThrows(IllegalArgumentException.class,
                        () -> new GroceryItem("\u2003\u3000", BigDecimal.ONE, "g"))),
                DynamicTest.dynamicTest("Unicode blank unit", () -> assertThrows(IllegalArgumentException.class,
                        () -> new GroceryItem("Rice", BigDecimal.ONE, "\u2003\u3000"))),
                DynamicTest.dynamicTest("negative quantity", () -> assertThrows(IllegalArgumentException.class,
                        () -> new GroceryItem("Rice", new BigDecimal("-0.00001"), "g"))));
    }

    @Test
    void toString_zeroAndExponent_printsPlainDecimalsWithoutInsignificantZeroes() {
        assertEquals("Rice: 0g", new GroceryItem("Rice", new BigDecimal("0.00"), "g").toString());
        assertEquals("Rice: 1000g", new GroceryItem("Rice", new BigDecimal("1E+3"), "g").toString());
        assertEquals("Rice: 0.0000001g", new GroceryItem("Rice", new BigDecimal("1E-7"), "g").toString());
    }
}

package mishmash.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** Checks positive boundaries and independent validation of each adjustment field. */
class PortionAdjustmentTest {
    @Test
    void constructor_positiveBounds_preservesAllValues() {
        PortionAdjustment minimum = new PortionAdjustment(1, 1, 1);
        assertEquals(1, minimum.dayNumber());
        assertEquals(1, minimum.mealNumber());
        assertEquals(1, minimum.portionCount());
        PortionAdjustment maximum = new PortionAdjustment(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, maximum.dayNumber());
        assertEquals(Integer.MAX_VALUE, maximum.mealNumber());
        assertEquals(Integer.MAX_VALUE, maximum.portionCount());
    }

    @TestFactory
    Stream<DynamicTest> constructor_eachNonpositiveField_reportsItsOwnError() {
        return Stream.of(0, -1, Integer.MIN_VALUE).flatMap(value -> Stream.of(
                DynamicTest.dynamicTest("day " + value, () -> assertEquals("Day number must be at least 1.",
                        assertThrows(IllegalArgumentException.class,
                                () -> new PortionAdjustment(value, 1, 1)).getMessage())),
                DynamicTest.dynamicTest("meal " + value, () -> assertEquals("Meal number must be at least 1.",
                        assertThrows(IllegalArgumentException.class,
                                () -> new PortionAdjustment(1, value, 1)).getMessage())),
                DynamicTest.dynamicTest("portions " + value, () -> assertEquals("Portion count must be at least 1.",
                        assertThrows(IllegalArgumentException.class,
                                () -> new PortionAdjustment(1, 1, value)).getMessage()))));
    }
}

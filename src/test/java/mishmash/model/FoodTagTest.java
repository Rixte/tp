package mishmash.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Tests command labels, aliases and malformed food-category lists directly.
 */
class FoodTagTest {
    /**
     * Exercises every canonical category spelling.
     */
    @TestFactory
    Stream<DynamicTest> parse_canonicalLabels_roundTripEveryCategory() {
        return Arrays.stream(FoodTag.values()).map(tag -> DynamicTest.dynamicTest(tag.getLabel(), () -> {
            assertEquals(tag, FoodTag.parse(tag.getLabel()));
            assertEquals(tag, FoodTag.parse("  " + tag.name() + "  "));
            assertEquals(tag, FoodTag.parse(tag.getLabel().toUpperCase(Locale.ROOT)));
        }));
    }

    /**
     * Checks each supported alias against its explicit expected category.
     */
    @TestFactory
    Stream<DynamicTest> parse_aliases_resolveToCanonicalCategories() {
        return Map.of("dairy", FoodTag.MILK, "soya", FoodTag.SOY, "sulfite", FoodTag.SULPHITE,
                "eggs", FoodTag.EGG, "peanuts", FoodTag.PEANUT, "tree-nuts", FoodTag.TREE_NUT,
                "crustaceans", FoodTag.CRUSTACEAN, "molluscs", FoodTag.MOLLUSC).entrySet().stream()
                .map(entry -> DynamicTest.dynamicTest(entry.getKey(), () ->
                        assertEquals(entry.getValue(), FoodTag.parse(" " + entry.getKey() + " "))));
    }

    /**
     * Rejects empty entries and duplicates after alias normalization.
     */
    @TestFactory
    Stream<DynamicTest> parseCsv_blankOrRepeatedEntries_rejectMalformedLists() {
        return Stream.of("", " ", ",milk", "milk,", "milk,,egg", "milk, \t,egg",
                "milk,MILK", "milk,dairy", "soy,soya", "tree-nut,tree_nuts")
                .map(input -> DynamicTest.dynamicTest("Reject: " + input, () ->
                        assertThrows(IllegalArgumentException.class, () -> FoodTag.parseCsv(input))));
    }

    @Test
    void parse_unknownCategory_explainsSupportedLabelsAndRetainsCause() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> FoodTag.parse("nuts"));
        assertTrue(error.getMessage().contains("Invalid food tag 'nuts'."));
        assertTrue(error.getMessage().contains("Valid food tags: " + FoodTag.getSupportedLabels()));
        assertTrue(error.getCause() instanceof IllegalArgumentException);
        assertThrows(IllegalArgumentException.class, () -> FoodTag.parse(" "));
        assertThrows(IllegalArgumentException.class, () -> FoodTag.parseCsv("milk,unknown"));
    }

    @Test
    void parseCsv_validAliases_returnsImmutableCanonicalSet() {
        Set<FoodTag> tags = FoodTag.parseCsv(" Dairy, tree_nuts, SOYA ");
        assertEquals(Set.of(FoodTag.MILK, FoodTag.TREE_NUT, FoodTag.SOY), tags);
        assertThrows(UnsupportedOperationException.class, () -> tags.add(FoodTag.EGG));
        assertThrows(UnsupportedOperationException.class, () -> tags.remove(FoodTag.MILK));
    }

    @Test
    void parse_nullInput_rejectsProgrammingErrors() {
        assertThrows(NullPointerException.class, () -> FoodTag.parse(null));
        assertThrows(NullPointerException.class, () -> FoodTag.parseCsv(null));
    }

    @Test
    void getSupportedLabels_listsEveryCanonicalLabelInDeclarationOrder() {
        assertEquals(Arrays.stream(FoodTag.values()).map(FoodTag::getLabel).toList(),
                Arrays.asList(FoodTag.getSupportedLabels().split(", ")));
        assertEquals("tree-nut", FoodTag.TREE_NUT.getLabel());
        assertEquals("other-animal-product", FoodTag.OTHER_ANIMAL_PRODUCT.getLabel());
    }
}

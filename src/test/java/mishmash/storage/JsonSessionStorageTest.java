package mishmash.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import mishmash.model.ApplicationState;
import mishmash.model.DayPlan;
import mishmash.model.Diet;
import mishmash.model.DietaryPreferences;
import mishmash.model.FoodInfo;
import mishmash.model.FoodTag;
import mishmash.model.GeneratePlanRequest;
import mishmash.model.GroceryItem;
import mishmash.model.MealPlan;
import mishmash.model.NutritionTargets;
import mishmash.model.PlannedMeal;
import mishmash.model.Recipe;

/** Verifies retention, recovery, locks, and validation against real temporary files. */
class JsonSessionStorageTest {
    @TempDir
    Path directory;

    @Test
    void saveAndReopen_restoresEveryRootAndIndependentRecipeSnapshots() throws Exception {
        ApplicationState original = populatedState();
        Path file = directory.resolve("mishmash.json");
        try (JsonSessionStorage storage = JsonSessionStorage.open(file)) {
            assertTrue(storage.load().warning().isEmpty());
            storage.save(original);
        }
        try (JsonSessionStorage storage = JsonSessionStorage.open(file)) {
            ApplicationState restored = storage.load().state();
            assertEquals(SessionData.fromState(original), SessionData.fromState(restored));
            assertEquals(2, restored.getRecipeBook().size());
            assertEquals(2, restored.getMealLog().getMeals().size());
            PlannedMeal meal = restored.getActiveMealPlan().get().orElseThrow().getDays().getFirst()
                    .getPlannedMeals().getFirst();
            assertEquals(3, meal.getPortionCount());
            assertEquals("rice", meal.getIngredients().getFirst().getName());
            assertTrue(meal.hasCustomizedIngredients());
        }
    }

    @Test
    void save_absentAndEmptyGroceriesRemainDistinct() throws Exception {
        ApplicationState state = populatedState();
        try (JsonSessionStorage storage = JsonSessionStorage.open(directory.resolve("state.json"))) {
            state.getActiveMealPlan().setGroceryList(List.of());
            storage.save(state);
            assertEquals(Optional.of(List.of()), storage.load().state().getActiveMealPlan().getGroceryList());
            state.getActiveMealPlan().update(state.getActiveMealPlan().get().orElseThrow()
                    .withAdjustedPortion(0, 0, 2));
            storage.save(state);
            assertTrue(storage.load().state().getActiveMealPlan().getGroceryList().isEmpty());
        }
    }

    @Test
    void load_corruptPrimary_recoversPreviousSnapshotAndPreservesDamagedFile() throws Exception {
        Path file = directory.resolve("state.json");
        try (JsonSessionStorage storage = JsonSessionStorage.open(file)) {
            storage.save(new ApplicationState());
            storage.save(populatedState());
            Files.writeString(file, "broken");
            LoadResult recovered = storage.load();
            assertTrue(recovered.warning().isPresent());
            assertEquals(0, recovered.state().getRecipeBook().size());
            assertTrue(storage.load().warning().isEmpty());
            try (Stream<Path> paths = Files.list(directory)) {
                assertTrue(paths.anyMatch(path -> path.getFileName().toString().contains(".corrupt-")));
            }
        }
    }

    @TestFactory
    Stream<DynamicTest> load_invalidDocuments_quarantinesWithoutPartialRestoration() {
        return Stream.of("null", "{}", "{\"schemaVersion\":99}", "[]", "{} {}",
                "{\"schemaVersion\":1,\"schemaVersion\":1}")
                .map(json -> DynamicTest.dynamicTest(json, () -> {
                    Path subdirectory = Files.createTempDirectory(directory, "invalid-");
                    Path file = subdirectory.resolve("state.json");
                    Files.writeString(file, json);
                    try (JsonSessionStorage storage = JsonSessionStorage.open(file)) {
                        assertTrue(storage.load().warning().isPresent());
                        assertFalse(Files.exists(file));
                        storage.save(new ApplicationState());
                        assertTrue(storage.load().warning().isEmpty());
                    }
                }));
    }

    @Test
    void load_missingPrimary_restoresBackup() throws Exception {
        Path file = directory.resolve("state.json");
        try (JsonSessionStorage storage = JsonSessionStorage.open(file)) {
            storage.save(populatedState());
            Files.delete(file);
            assertTrue(storage.load().warning().isPresent());
            assertEquals(2, storage.load().state().getRecipeBook().size());
        }
    }

    @TestFactory
    Stream<DynamicTest> load_invalidFields_rejectsCompleteSnapshot() throws Exception {
        Path templateFile = directory.resolve("template.json");
        try (JsonSessionStorage storage = JsonSessionStorage.open(templateFile)) {
            storage.save(populatedState());
        }
        String template = Files.readString(templateFile);
        return Stream.of(
                template.replace("\"schemaVersion\" : 1", "\"schemaVersion\" : 99"),
                template.replace("\"calories\" : 100,", ""),
                template.replace("\"calories\" : 100", "\"calories\" : null"),
                template.replace("\"calories\" : 100", "\"calories\" : -1"),
                template.replace("\"calories\" : 100", "\"calories\" : 1.5"),
                template.replace("\"calories\" : 100", "\"calories\" : \"100\""),
                template.replace("\"portionCount\" : 3", "\"portionCount\" : 0"),
                template.replace("\"days\" : 1", "\"days\" : 2"),
                template.replace("\"vegetarian\"", "\"unknown-diet\""),
                template.replace("\"soy\"", "\"soy\", \"soya\""),
                template.replace("\"oil\"", "\"oil\", \"oil\""),
                template.replace("\"oil\"", "\"oil\", \" OIL \""),
                template.replace("\"foodTags\" : [ ]", "\"foodTags\" : [ \"soy\", \"soya\" ]"),
                template.replace("\"foodClassified\" : true", "\"foodClassified\" : \"true\""),
                template.replace("\"quantity\" : \"100.00100\"", "\"quantity\" : \"0\""),
                template.replace("\"calories\" : 100", "\"calories\" : 2147483648"),
                template.replace("\"name\" : \"Bowl\"", "\"name\" : \"  \""),
                template.replace("100.00100", "1E+2147483647"),
                template.replace("\"schemaVersion\"", "\"unexpected\""))
                .map(json -> DynamicTest.dynamicTest("reject malformed field", () -> {
                    assertFalse(json.equals(template), "Mutation must change the fixture");
                    Path file = Files.createTempDirectory(directory, "field-").resolve("state.json");
                    Files.writeString(file, json);
                    try (JsonSessionStorage storage = JsonSessionStorage.open(file)) {
                        LoadResult result = storage.load();
                        assertTrue(result.warning().isPresent());
                        assertEquals(0, result.state().getRecipeBook().size());
                    }
                }));
    }

    @Test
    void load_oversizedDocument_preservesItAndStartsEmpty() throws Exception {
        Path file = directory.resolve("state.json");
        Files.write(file, new byte[10 * 1024 * 1024 + 1]);
        try (JsonSessionStorage storage = JsonSessionStorage.open(file)) {
            assertTrue(storage.load().warning().isPresent());
            assertFalse(Files.exists(file));
        }
    }

    @TestFactory
    Stream<DynamicTest> load_excessiveJsonTokens_rejectsWithinSizeLimit() {
        return Stream.of("[".repeat(101) + "0" + "]".repeat(101),
                "{\"schemaVersion\":" + "9".repeat(1001) + "}",
                "{\"value\":\"" + "x".repeat(1_000_001) + "\"}")
                .map(json -> DynamicTest.dynamicTest("reject parser limit", () -> {
                    Path file = Files.createTempDirectory(directory, "limit-").resolve("state.json");
                    Files.writeString(file, json);
                    try (JsonSessionStorage storage = JsonSessionStorage.open(file)) {
                        assertTrue(storage.load().warning().isPresent());
                        assertFalse(Files.exists(file));
                    }
                }));
    }

    @Test
    void save_invalidLiveState_preservesExistingFiles() throws Exception {
        Path file = directory.resolve("state.json");
        try (JsonSessionStorage storage = JsonSessionStorage.open(file)) {
            storage.save(populatedState());
            String primary = Files.readString(file);
            String backup = Files.readString(directory.resolve("state.backup.json"));
            ApplicationState inconsistent = new ApplicationState();
            inconsistent.getActiveMealPlan().setGroceryList(List.of());
            assertThrows(StorageException.class, () -> storage.save(inconsistent));
            inconsistent.getActiveMealPlan().set(new MealPlan(List.of(new DayPlan(List.of()))));
            assertThrows(StorageException.class, () -> storage.save(inconsistent));
            assertEquals(primary, Files.readString(file));
            assertEquals(backup, Files.readString(directory.resolve("state.backup.json")));
        }
    }

    @Test
    void save_externallyDamagedPrimary_preservesItAndKeepsValidBackup() throws Exception {
        Path file = directory.resolve("state.json");
        try (JsonSessionStorage storage = JsonSessionStorage.open(file)) {
            storage.save(populatedState());
            Files.writeString(file, "external damage");
            storage.save(new ApplicationState());
            assertEquals(0, storage.load().state().getRecipeBook().size());
            Files.delete(file);
            assertEquals(2, storage.load().state().getRecipeBook().size());
            try (Stream<Path> paths = Files.list(directory)) {
                Path damaged = paths.filter(path -> path.toString().contains("corrupt-during-save"))
                        .findFirst().orElseThrow();
                assertEquals("external damage", Files.readString(damaged));
            }
        }
    }

    @Test
    void load_bothFilesDamaged_preservesBothAndStartsEmpty() throws Exception {
        Path file = directory.resolve("state.json");
        Files.writeString(file, "invalid primary");
        Files.writeString(directory.resolve("state.backup.json"), "invalid backup");
        try (JsonSessionStorage storage = JsonSessionStorage.open(file)) {
            LoadResult result = storage.load();
            assertTrue(result.warning().isPresent());
            assertEquals(0, result.state().getRecipeBook().size());
            try (Stream<Path> paths = Files.list(directory)) {
                assertEquals(2, paths.filter(path -> path.toString().contains("corrupt")).count());
            }
        }
    }

    @Test
    void load_filesystemFailure_stopsWithoutMovingBackup() throws Exception {
        Path file = directory.resolve("state.json");
        try (JsonSessionStorage storage = JsonSessionStorage.open(file)) {
            storage.save(populatedState());
            String backup = Files.readString(directory.resolve("state.backup.json"));
            Files.delete(file);
            Files.createDirectory(file);
            assertThrows(StorageException.class, storage::load);
            assertEquals(backup, Files.readString(directory.resolve("state.backup.json")));
        }
    }

    @Test
    void save_failedReplacement_keepsPrimaryAndAllowsRetry() throws Exception {
        Path file = directory.resolve("state.json");
        try (JsonSessionStorage storage = JsonSessionStorage.open(file)) {
            storage.save(populatedState());
            byte[] before = Files.readAllBytes(file);
            Path backup = directory.resolve("state.backup.json");
            Files.delete(backup);
            Files.createDirectory(backup);
            assertThrows(StorageException.class, () -> storage.save(new ApplicationState()));
            assertEquals(new String(before, java.nio.charset.StandardCharsets.UTF_8), Files.readString(file));
            Files.delete(backup);
            storage.save(new ApplicationState());
            assertEquals(0, storage.load().state().getRecipeBook().size());
        }
    }

    @Test
    void lock_secondInstanceRejectedAndCloseReleasesLock() throws Exception {
        Path file = directory.resolve("state.json");
        JsonSessionStorage first = JsonSessionStorage.open(file);
        try {
            assertThrows(StorageException.class, () -> JsonSessionStorage.open(file));
        } finally {
            first.close();
        }
        first.close();
        assertThrows(StorageException.class, first::load);
        try (JsonSessionStorage second = JsonSessionStorage.open(file)) {
            assertTrue(second.load().warning().isEmpty());
        }
    }

    /** Builds snapshots with duplicates, precise decimals, and a case-only ingredient customization. */
    static ApplicationState populatedState() {
        ApplicationState state = new ApplicationState();
        GroceryItem rice = new GroceryItem("Rice", new BigDecimal("100.00100"), "g");
        Recipe recipe = new Recipe("Bowl", 100, 20, 30, 4, List.of(rice), FoodInfo.parseField("none"));
        state.getRecipeBook().add(recipe);
        state.getRecipeBook().add(recipe);
        state.getMealLog().record(recipe);
        state.getMealLog().record(recipe);
        state.getDietarySettings().set(new DietaryPreferences(Diet.VEGETARIAN, Set.of(FoodTag.SOY), Set.of("Oil")));
        state.getTargetSettings().set(new NutritionTargets(2000, null, 100, null));
        PlannedMeal meal = new PlannedMeal(recipe, 3).withIngredients(
                List.of(new GroceryItem("rice", new BigDecimal("100.00100"), "g")));
        state.getActiveMealPlan().setGenerated(new MealPlan(List.of(DayPlan.fromPlannedMeals(List.of(meal)))),
                new GeneratePlanRequest(1, NutritionTargets.none()), Optional.of(meal.getScaledIngredients()));
        return state;
    }
}

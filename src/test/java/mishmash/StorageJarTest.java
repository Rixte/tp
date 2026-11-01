package mishmash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import mishmash.storage.JsonSessionStorage;

/** Exercises autosave, EOF, and restart using only the packaged JAR from an independent working directory. */
class StorageJarTest {
    private static final String ADD_RECIPE =
            "add-recipe n/A cal/100 p/10 c/10 f/1 i/Rice:100.001g food/none";
    @TempDir
    Path directory;

    @Test
    void jar_existingProcessLock_refusesSecondSession() throws Exception {
        try (JsonSessionStorage storage = JsonSessionStorage.open(directory.resolve("data/mishmash.json"))) {
            assertTrue(storage.load().warning().isEmpty());
            String output = runJar("Tester\nbye\n");
            assertTrue(output.contains("already using this data folder"), output);
            assertFalse(output.contains("Who's cooking today?"), output);
        }
    }

    @Test
    void jar_forcedTerminationAfterFeedback_keepsAutosavedChangeAndReleasesLock() throws Exception {
        Path output = Files.createTempFile(directory, "interrupted-", ".txt");
        Process process = startJar(output);
        try {
            process.getOutputStream().write(("Tester\n" + ADD_RECIPE + "\n").getBytes(StandardCharsets.UTF_8));
            process.getOutputStream().flush();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (!Files.readString(output).contains("New recipe added!") && System.nanoTime() < deadline) {
                assertTrue(process.isAlive(), Files.readString(output));
                Thread.sleep(20);
            }
            assertTrue(Files.readString(output).contains("New recipe added!"), "Autosave did not finish");
        } finally {
            process.destroyForcibly();
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "JAR did not terminate");
        }
        String restored = runJar("Tester\nlist-recipes\nbye\n");
        assertTrue(restored.contains("1. A (100 kcal)"), restored);
    }

    @Test
    void jar_failedSave_warnsRetainsMemoryAndPreservesLastGoodFile() throws Exception {
        runJar("Tester\n" + ADD_RECIPE + "\nbye\n");
        Path primary = directory.resolve("data/mishmash.json");
        String saved = Files.readString(primary);
        Path backup = directory.resolve("data/mishmash.backup.json");
        Files.delete(backup);
        Files.createDirectory(backup);
        String output = runJar("Tester\nedit-recipe 1 n/Unsaved\nlist-recipes\nbye\n");
        assertTrue(output.contains("[WARN] Recipe updated!"), output);
        assertTrue(output.contains("1. Unsaved (100 kcal)"), output);
        assertTrue(output.contains("Exiting with unsaved changes."), output);
        assertFalse(output.contains("[DONE] Recipe updated!"), output);
        assertEquals(saved, Files.readString(primary));
        Files.delete(backup);
        assertTrue(runJar("Tester\nlist-recipes\nbye\n").contains("1. A (100 kcal)"));
    }

    @Test
    void jar_corruptData_recoversBackupAndPreservesOriginal() throws Exception {
        runJar("Tester\n" + ADD_RECIPE + "\nbye\n");
        Path file = directory.resolve("data/mishmash.json");
        Files.writeString(file, "damaged primary");
        String output = runJar("Tester\nlist-recipes\nbye\n");
        assertTrue(output.contains("[WARN] Saved data was damaged"), output);
        assertTrue(output.contains("1. A (100 kcal)"), output);
        try (var paths = Files.list(file.getParent())) {
            Path preserved = paths.filter(path -> path.toString().contains(".corrupt-")).findFirst().orElseThrow();
            assertEquals("damaged primary", Files.readString(preserved));
        }
    }

    @Test
    void jar_noOpRejectedAndCancelledCommands_doNotRewriteSave() throws Exception {
        runJar("Tester\n" + ADD_RECIPE + "\nbye\n");
        Path file = directory.resolve("data/mishmash.json");
        java.nio.file.attribute.FileTime marker = java.nio.file.attribute.FileTime.fromMillis(1_000_000);
        Files.setLastModifiedTime(file, marker);
        String output = runJar("Tester\nclear-diet\ncustomize-meal d/1 m/1 i/Rice:1g\n"
                + "adjust-portion d/1 m/1 portions/0\ndelete-recipe 1\nn\nlist-recipes\nbye\n");
        assertTrue(output.contains("[NOTE] Dietary preferences are already clear"), output);
        assertTrue(output.contains("Cancelled. Nothing was changed."), output);
        assertTrue(output.contains("[ERROR]"), output);
        assertEquals(marker, Files.getLastModifiedTime(file));
    }

    @Test
    void jar_failedDietaryRepair_retainsWarningOutcomeAndClearedPlanAcrossRestart() throws Exception {
        String setup = String.join("\n", "Tester", ADD_RECIPE,
                ADD_RECIPE.replace("n/A", "n/B"), ADD_RECIPE.replace("n/A", "n/C"),
                "generate-plan d/2", "generate-list", "record-meal 1", "set-diet exclude/Rice", "yes", "bye", "");
        String first = runJar(setup);
        assertTrue(first.contains("[WARN] Dietary preferences saved, but"), first);
        String restored = runJar("Tester\nview-diet\nview-plan\nview-list\nview-meals\nbye\n");
        assertTrue(restored.contains("Excluded ingredients: rice"), restored);
        assertTrue(restored.contains("No active meal plan."), restored);
        assertTrue(restored.contains("No grocery list"), restored);
        assertTrue(restored.contains("Meal 1: A (100 kcal |"), restored);
    }

    @Test
    void jar_restartsWithAllStateAndDefensiveCustomizations() throws Exception {
        String setup = String.join("\n", "Tester",
                "add-recipe n/A cal/100 p/10 c/10 f/1 i/Rice:100.001g food/none",
                "add-recipe n/B cal/100 p/10 c/10 f/1 i/Beans:100g food/none",
                "add-recipe n/C cal/100 p/10 c/10 f/1 i/Tofu:100g food/none",
                "set-diet diet/vegetarian", "generate-plan d/2", "adjust-portion d/1 m/1 portions/2",
                "customize-meal d/1 m/1 i/rice:12.500g", "generate-list", "record-meal 1",
                "edit-recipe 1 n/Edited", "set-targets cal/2000", "bye", "");
        String first = runJar(setup);
        assertTrue(first.contains("[WARN] Meal 1 on Day 1 ingredients customized."), first);
        assertTrue(Files.exists(directory.resolve("data/mishmash.json")));
        String second = runJar("Tester\nlist-recipes\nview-diet\nview-targets\nview-plan\nview-list\nview-meals\n");
        assertTrue(second.contains("Edited"), second);
        assertTrue(second.contains("vegetarian"), second);
        assertTrue(second.contains("2000"), second);
        assertTrue(second.contains("custom ingredients"), second);
        assertTrue(second.contains("rice: 25g"), second);
        assertTrue(second.contains("Meal 1: A (100 kcal |"), second);
        assertTrue(second.contains("Day 2"), second);
    }

    /** Runs with bounded time and redirected output to avoid pipe deadlocks and native-terminal dependencies. */
    private String runJar(String input) throws Exception {
        Path output = Files.createTempFile(directory, "output-", ".txt");
        Process process = startJar(output);
        try {
            try (var stdin = process.getOutputStream()) {
                stdin.write(input.getBytes(StandardCharsets.UTF_8));
            }
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "JAR process timed out");
            String transcript = Files.readString(output).replaceAll("\u001B\\[[;\\d]*m", "");
            assertEquals(0, process.exitValue(), transcript);
            return transcript;
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    /** Starts the production launcher using Java 25 and an external working directory. */
    private Process startJar(Path output) throws Exception {
        Path java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
        Path jar = Path.of(System.getProperty("mishmash.test.jar")).toAbsolutePath();
        return new ProcessBuilder(java.toString(), "-jar", jar.toString())
                .directory(directory.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
    }
}

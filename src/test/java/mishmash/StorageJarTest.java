package mishmash;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    @TempDir
    Path directory;

    @Test
    void jar_existingProcessLock_refusesSecondSession() throws Exception {
        try (JsonSessionStorage storage = JsonSessionStorage.open(directory.resolve("data/mishmash.json"))) {
            assertTrue(storage.load().warning().isEmpty());
            String output = runJar("Tester\nbye\n");
            assertTrue(output.contains("already using this data folder"), output);
            assertTrue(!output.contains("Who's cooking today?"), output);
        }
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
        Path java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
        Path jar = Path.of(System.getProperty("mishmash.test.jar")).toAbsolutePath();
        Path output = Files.createTempFile(directory, "output-", ".txt");
        Process process = new ProcessBuilder(java.toString(), "-ea", "-jar", jar.toString())
                .directory(directory.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
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
}

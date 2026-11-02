package mishmash.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import mishmash.model.ApplicationState;

/** Verifies load-result contracts and exact preservation of recovery feedback. */
class LoadResultTest {
    @Test
    void factories_preserveStateAndDistinguishNormalFromRecovery() {
        ApplicationState state = new ApplicationState();
        LoadResult normal = LoadResult.normal(state);
        assertSame(state, normal.state());
        assertTrue(normal.warning().isEmpty());
        String warning = "Recovered backup.\nPreserved original data.";
        LoadResult recovered = LoadResult.recovered(state, warning);
        assertSame(state, recovered.state());
        assertEquals(Optional.of(warning), recovered.warning());
    }

    @Test
    void constructor_missingDependencies_rejectsAtBoundary() {
        assertThrows(NullPointerException.class, () -> new LoadResult(null, Optional.empty()));
        assertThrows(NullPointerException.class, () -> new LoadResult(new ApplicationState(), null));
        assertThrows(NullPointerException.class, () -> LoadResult.normal(null));
        assertThrows(NullPointerException.class, () -> LoadResult.recovered(new ApplicationState(), null));
    }

    @Test
    void constructor_blankWarning_rejectsButDoesNotTrimMeaningfulText() {
        for (String blank : new String[]{"", " ", "\n\t"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> LoadResult.recovered(new ApplicationState(), blank));
        }
        assertEquals(Optional.of("  recovered  "),
                LoadResult.recovered(new ApplicationState(), "  recovered  ").warning());
    }
}

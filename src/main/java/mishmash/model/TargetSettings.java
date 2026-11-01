package mishmash.model;

import java.util.Objects;

//@@author neil0618
/**
 * Holds the user's saved daily nutrition targets for the current session.
 *
 * <p>This follows {@link DietarySettings}: one instance is shared by the commands that need it, and the
 * targets are read when a command runs, so a later {@code set-targets} is always respected. The
 * storage layer retains these targets across application sessions.</p>
 */
public final class TargetSettings {
    /** Immutable targets shared by commands in this session; nothing is set at the start. */
    private NutritionTargets targets = NutritionTargets.none();

    /**
     * Returns the saved targets.
     *
     * @return The current targets, never null; a nutrient without a target is null inside them.
     */
    public NutritionTargets get() {
        return targets;
    }

    /**
     * Replaces the saved targets.
     *
     * @param targets The new targets.
     * @throws NullPointerException If the targets are null; the previous targets are preserved.
     */
    public void set(NutritionTargets targets) {
        this.targets = Objects.requireNonNull(targets, "targets");
    }
}
//@@author

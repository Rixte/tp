package mishmash.storage;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import mishmash.model.ActiveMealPlan;
import mishmash.model.ApplicationState;
import mishmash.model.DietarySettings;
import mishmash.model.GeneratePlanRequest;
import mishmash.model.MealLog;
import mishmash.model.MealPlan;
import mishmash.model.NutritionTargets;
import mishmash.model.RecipeBook;
import mishmash.model.TargetSettings;

/** Verifies invalid DTO and holder state cannot become a published application snapshot. */
class SessionDataValidationTest {
    @Test
    void toState_nullRecipeInList_rejectsRatherThanRestoringPartialData() {
        SessionData valid = SessionData.fromState(JsonSessionStorageTest.populatedState());
        SessionData invalid = new SessionData(valid.schemaVersion(), Arrays.asList((SessionData.RecipeData) null),
                valid.dietaryPreferences(), valid.dailyTargets(), valid.activePlan(), valid.recordedMeals());
        assertThrows(IllegalArgumentException.class, invalid::toState);
    }

    @Test
    void toMeal_sourceEquivalentOverride_rejectsRedundantSavedCustomization() {
        SessionData valid = SessionData.fromState(JsonSessionStorageTest.populatedState());
        SessionData.RecipeData source = valid.recipes().getFirst();
        SessionData.PlannedMealData meal = new SessionData.PlannedMealData(source, 1, source.ingredients());
        assertThrows(IllegalArgumentException.class, meal::toMeal);
    }

    @Test
    void fromState_orphanRequest_rejectsInconsistentHolder() {
        ActiveMealPlan inconsistent = new ActiveMealPlan() {
            @Override
            public Optional<GeneratePlanRequest> getRequest() {
                return Optional.of(new GeneratePlanRequest(1, NutritionTargets.none()));
            }

            @Override
            public Optional<MealPlan> get() {
                return Optional.empty();
            }
        };
        ApplicationState state = new ApplicationState(new RecipeBook(), inconsistent,
                new DietarySettings(), new TargetSettings(), new MealLog());
        assertThrows(IllegalStateException.class, () -> SessionData.fromState(state));
    }
}

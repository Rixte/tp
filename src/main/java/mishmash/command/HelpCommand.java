package mishmash.command;

import mishmash.model.Diet;
import mishmash.model.FoodTag;
import mishmash.model.RecipeBook;
import mishmash.parser.DietaryPreferencesParser;
import mishmash.parser.Parser;

/**
 * Displays every supported command with its purpose and syntax.
 */
public class HelpCommand implements Command {
    @Override
    public String execute(RecipeBook recipeBook) {
        return String.join(System.lineSeparator(),
                "MISHMASH KITCHEN MANUAL",
                "Available commands (names are case-sensitive):",
                "",
                describeRecipes(), "", describePlanning(), "", describePlanChanges(), "",
                describeShopping(), "", describeDiet(), "", describeTargets(), "",
                describeMealTracking(), "", describeGeneralActions());
    }

    /**
     * Describes recipe creation, inspection, editing, and deletion.
     */
    private String describeRecipes() {
        return String.join(System.lineSeparator(),
                "[ RECIPES ]",
                "add-recipe - Add a recipe with nutrition and ingredients.",
                Parser.ADD_RECIPE_USAGE,
                "",
                "list-recipes - List all recipes, or show one recipe in full.",
                Parser.LIST_RECIPE_USAGE,
                "",
                "edit-recipe - Update selected fields of a recipe by its displayed index.",
                Parser.EDIT_RECIPE_USAGE,
                "Existing plans and groceries keep their snapshots; regenerate them to use the edit.",
                "",
                "delete-recipe - Delete a recipe by its displayed index, after confirmation.",
                Parser.DELETE_RECIPE_USAGE);
    }

    /**
     * Describes generation, viewing, portions, and occurrence-specific ingredients.
     */
    private String describePlanning() {
        return String.join(System.lineSeparator(),
                "[ MEAL PLANNING ]",
                "generate-plan - Generate a meal plan with optional daily nutrition targets.",
                Parser.GENERATE_PLAN_USAGE,
                "",
                "view-plan - View the active meal plan, or one day.",
                Parser.VIEW_PLAN_USAGE,
                "",
                AdjustPortionCommand.COMMAND_WORD
                        + " - Change the whole-recipe portion count of one planned meal.",
                Parser.ADJUST_PORTION_USAGE,
                "Day and meal numbers come from view-plan. Updated portions clear generated groceries.",
                "",
                CustomizeMealCommand.COMMAND_WORD
                        + " - Replace the ingredients of one planned meal occurrence.",
                Parser.CUSTOMIZE_MEAL_USAGE,
                "Quantities describe one portion. Source recipes and nutrition values are unchanged.");
    }

    /**
     * Describes replacement, reset, and nutrition checking for an active plan.
     */
    private String describePlanChanges() {
        return String.join(System.lineSeparator(),
                //@@author neil0618
                ReplaceMealCommand.COMMAND_WORD
                        + " - Replace one planned meal with a recipe from your recipe book.",
                Parser.REPLACE_MEAL_USAGE,
                "Day and meal numbers come from view-plan; recipe numbers come from list-recipes.",
                "The new meal starts at 1 portion. It must fit your dietary preferences. Groceries are cleared.",
                "",
                //@@author
                //@@author Rixte
                ResetPlanCommand.COMMAND_WORD + " - Clear the current meal plan and its grocery list.",
                ResetPlanCommand.USAGE,
                "Recipes, dietary preferences, saved daily targets, and recorded meals are kept.",
                "",
                //@@author
                CheckShortfallCommand.COMMAND_WORD
                        + " - Check each day's planned nutrition against explicit daily targets.",
                Parser.CHECK_SHORTFALL_USAGE,
                "Supply at least one target; omitted nutrients are not checked.");
    }

    /**
     * Describes grocery generation and viewing.
     */
    private String describeShopping() {
        return String.join(System.lineSeparator(),
                "[ SHOPPING ]",
                "generate-list - Generate a grocery list from the active meal plan.",
                Parser.GENERATE_LIST_USAGE,
                "",
                "view-list - View the generated grocery list.",
                Parser.VIEW_LIST_USAGE);
    }

    /**
     * Describes dietary profiles, classifications, and ingredient exclusions.
     */
    private String describeDiet() {
        return String.join(System.lineSeparator(),
                "[ DIET AND TARGETS ]",
                "set-diet - Replace dietary preferences and update conflicting planned days.",
                DietaryPreferencesParser.USAGE,
                "Supply at least one field. Values are case-insensitive; field prefixes are case-sensitive.",
                Diet.getInputHelp(),
                "Food tags: " + FoodTag.getSupportedLabels(),
                "Recipes must declare every applicable category, including those in compound ingredients.",
                "Unclassified recipes are excluded while restrictions are active.",
                "Ingredient exclusions match complete names, ignoring case and extra spaces.",
                "",
                "view-diet - View current dietary preferences.",
                "Format: view-diet",
                "",
                ClearDietCommand.COMMAND_WORD + " - Remove all dietary preferences and restrictions.",
                Parser.CLEAR_DIET_USAGE,
                "Recipes, plans, groceries, targets, and recorded meals are unchanged.");
    }

    /**
     * Describes saved daily targets and their precedence during generation.
     */
    private String describeTargets() {
        return String.join(System.lineSeparator(),
                //@@author neil0618
                SetTargetsCommand.COMMAND_WORD
                        + " - Save daily calorie and macronutrient targets for future meal plans.",
                Parser.SET_TARGETS_USAGE,
                "Supply at least one field. Use 'none' to remove a target; omitted fields keep their saved value.",
                "generate-plan uses saved targets for nutrients you leave out; targets typed in it take priority.",
                "",
                ViewTargetsCommand.COMMAND_WORD + " - View your saved daily targets.",
                Parser.VIEW_TARGETS_USAGE);
        //@@author
    }

    /**
     * Describes recorded meals and date-specific intake corrections.
     */
    private String describeMealTracking() {
        return String.join(System.lineSeparator(),
                "[ MEAL TRACKING ]",
                RecordMealCommand.COMMAND_WORD + " - Record a recipe you have eaten, by its displayed index.",
                Parser.RECORD_MEAL_USAGE,
                "The date defaults to today and the servings default to 1.",
                "",
                ViewMealsCommand.COMMAND_WORD + " - View the meals you have recorded, with nutrition totals.",
                Parser.VIEW_MEALS_USAGE,
                "Supply a date to see that date's meals and totals.",
                "",
                EditMealCommand.COMMAND_WORD + " - Correct the date or servings of a recorded meal.",
                Parser.EDIT_MEAL_USAGE,
                "Supply at least one field. Meal numbers come from view-meals.",
                "",
                DeleteMealCommand.COMMAND_WORD + " - Remove a recorded meal, after confirmation.",
                Parser.DELETE_MEAL_USAGE);
    }

    /**
     * Describes help, exit, and the notation shared by command formats.
     */
    private String describeGeneralActions() {
        return String.join(System.lineSeparator(),
                "[ GENERAL ]",
                "help - Show all commands and what they do.",
                "Format: help",
                "bye - Exit MishMash.",
                "Format: bye",
                "",
                "Square brackets indicate optional arguments. Use list-recipes to find recipe indexes.");
    }

    @Override
    public CommandResult executeWithResult(RecipeBook recipeBook) {
        return CommandResult.help(execute(recipeBook));
    }
}

# Justin Lee - Project Portfolio Page

## Overview

**Project: MishMash**

MishMash is a Java command-line meal-prep planner. Users can manage recipes, generate meal plans and grocery lists, and track nutrition against their targets.

## Summary of Contributions

**Code contributed:** [tP Code Dashboard (oilybikechain)](https://nus-cs2113-ay2627-s1.github.io/tp-dashboard/?search=oilybikechain&breakdown=true).

### Enhancements implemented

- **Grocery-list generation (`generate-list`).** Consolidates planned ingredients into shopping quantities, avoiding manual addition across meals. Implemented the grocery model, aggregation, parsing, and shared-state integration. `BigDecimal` preserves decimal precision; a composite ingredient-name/unit key prevents incompatible units from being combined. Results preserve first-appearance order. Later adapted aggregation to `generateForPlan`, including each occurrence's portions and customised ingredients. Failed generation preserves the previous grocery snapshot. ([#14](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/14), [#21](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/21), [#32](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/32), [#108](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/108))

- **Daily nutrition totals and excess warnings.** Added immutable `NutritionTotals` and daily aggregation for calories, protein, carbohydrates, and fats, helping users assess a complete day. Used `long` totals and retained generation targets with the plan. Warnings show actual values, targets, and excesses for displayed days. Kept strict target comparison separate from the generator's tolerance, including partial targets and dietary plan repair. ([#69](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/69), [#74](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/74))

- **Planned-meal portions and ingredient customisation (`adjust-portion`, `customize-meal`).** Introduced immutable `PlannedMeal` occurrences so users can adapt one meal without editing its source recipe or other occurrences. Portions scale ingredients and nutrition; ingredient overrides retain the source nutrition values. Integrated these changes with plan viewing, dietary repair, and grocery regeneration. Validation rejects invalid indexes, quantities, overflow, and excluded ingredients before mutation. Successful changes invalidate stale groceries; failed changes preserve state. Added warnings when customised ingredient names cannot be checked against category-based dietary rules. ([#108](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/108), [#109](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/109), [#118](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/118))

- **Clearing dietary settings (`clear-diet`).** Removes the selected diet, avoided categories, and ingredient exclusions while preserving recipes, plans, groceries, targets, and recorded meals. This lets users remove obsolete restrictions without rebuilding their work. Added argument validation and distinct feedback when nothing needs clearing. ([#110](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/110))

- **CLI feedback and recovery.** Added the kitchen-themed startup screen, grouped help, and clearer confirmation/error output. Replaced command-class heuristics with explicit result statuses so failures and unchanged state are labelled accurately. Added command-specific usage on execution errors and handled blank feedback defensively to keep the session running. ([#107](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/107), [#118](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/118), [fixes](https://github.com/AY2627S1-CS2113-W13-4/tp/commit/58eb76f))

- **Cross-session persistence, including packaged-JAR launches.** Implemented restoration and autosave of recipes, settings, plans, groceries, and meal logs using versioned JSON outside the JAR. Preserved independent recipe snapshots, decimal quantities, dated entries, and serving counts. Addressed partial writes, concurrent writers, and damaged data through validated temporary writes, atomic replacement where supported, backups, quarantine, and a process-lifetime file lock. Added save retries and legacy-schema migration with a preserved original backup. **Credit:** Jackson supplies JSON parsing/serialisation; I implemented the state mapping, validation, recovery, and application integration. ([#121](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/121))

### Testing and code quality

Strengthened generator preconditions and exception-cause propagation for recoverable errors ([#41](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/41)). Added JUnit tests for repeated recipes across days, occurrence isolation, portion scaling after dietary repair, parser boundaries, and unchanged state after rejected commands. Added separate-process JAR restart tests for retention and recovery. Extended direct tests of `PortionAdjustment`, `JsonSessionStorage`, `LoadResult`, `GroceryItem`, and `IngredientParser`.

Introduced injectable file operations for deterministic storage failures, including simultaneous acquisition and cleanup failures: the original exception remains primary and cleanup is suppressed. Added JaCoCo checks requiring **more than 95% line and branch coverage for each of 26 source files**, including their nested DTOs and helpers, and documented how to run them. ([#121](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/121), [#138](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/138))

### Documentation

- **User Guide:** Wrote the [grocery-generation](../UserGuide.md#generating-a-grocery-list), [nutrition-warning](../UserGuide.md#nutrition-target-warnings), [portion-adjustment](../UserGuide.md#adjusting-a-planned-meals-portions), [ingredient-customisation](../UserGuide.md#customising-a-planned-meals-ingredients), [clear-diet](../UserGuide.md#clearing-dietary-preferences-and-restrictions), and [data-retention](../UserGuide.md#data-retention) sections, plus daily-total explanations and recovery guidance. Included examples, limitations, and state effects. ([#75](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/75), [#121](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/121))
- **Developer Guide:** Documented these implementations, design choices, use cases, and manual tests. Added [14 grocery/nutrition/diagnostic UML diagrams](../diagrams/oilybikechain/README.md) and [storage class/autosave diagrams](../DeveloperGuide.md#persistence-and-recovery), including PlantUML sources and SVGs. Updated grocery/nutrition diagrams for `PlannedMeal` and `generateForPlan`. ([#85](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/85), [#118](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/118), [#121](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/121))

### Team tasks and reviews

- Standardised Developer Guide UML across the team's features, correcting course notation and splitting dense diagrams into focused views with regenerated SVGs. ([#98](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/98))
- Reviewed dietary repair ([#73](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/73#pullrequestreview-5637520510)), meal-log changes ([#120](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/120#pullrequestreview-5728470879)), and launcher tests ([#135](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/135#pullrequestreview-5736945499)). My meal-log review identified parser edge cases, consistency with generator constraints, and numbering after filtered deletions as regression risks. Also merged teammates' changes, including dietary planning ([#73](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/73)) and meal-log corrections ([#120](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/120)).

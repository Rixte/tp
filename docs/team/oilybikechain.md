# Justin Lee - Project Portfolio Page

## Overview

**Project: MishMash**

MishMash is a Java command-line meal-prep planner. Users can manage recipes, generate meal plans and grocery lists, and track nutrition against their targets.

## Summary of Contributions

**Code contributed:** [tP Code Dashboard (oilybikechain)](https://nus-cs2113-ay2627-s1.github.io/tp-dashboard/?search=oilybikechain&breakdown=true).

### Enhancements implemented

- **Grocery-list generation (`generate-list`).** Implemented the grocery model, aggregation, parser and shared-state integration to turn meal plans into shopping quantities. `BigDecimal` preserves precision; exact name/unit keys prevent incompatible units from merging. Results retain first-appearance order. Extended `generateForPlan` for portions and customised ingredients; failures preserve the previous snapshot. ([#14](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/14), [#21](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/21), [#32](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/32), [#108](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/108))

- **Daily nutrition totals and excess warnings.** Added immutable `NutritionTotals` and daily calorie/macronutrient aggregation so users can assess complete days. Used `long` arithmetic to avoid integer overflow. Warnings show actual values, targets and excesses, using retained generation targets and strict comparisons separately from generator tolerance, including partial targets and dietary repair. ([#69](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/69), [#74](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/74))

- **Planned-meal portions and ingredient customisation (`adjust-portion`, `customize-meal`).** Introduced immutable `PlannedMeal` occurrences so one meal can change independently of its source recipe and other occurrences. Portions scale ingredients and nutrition; overrides retain source nutrition. Integrated viewing, dietary repair and grocery regeneration. Validation checks indexes, quantities, exclusions and overflow before mutation; only successful changes clear stale groceries. Warnings explain the limits of category-based checks on customised ingredient names. ([#108](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/108), [#109](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/109), [#118](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/118))

- **Clearing dietary settings (`clear-diet`).** Removes the selected diet, avoided categories, and ingredient exclusions while preserving recipes, plans, groceries, targets, and recorded meals. This lets users remove obsolete restrictions without rebuilding their work. Added argument validation and distinct feedback when nothing needs clearing. ([#110](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/110))

- **CLI feedback and input safety.** Added the kitchen-themed startup screen, grouped help, explicit result statuses, command-specific usage, and recoverable blank-feedback handling. Later rejected unsupported controls before trimming so NUL-padded input cannot become an exit command. Escaped terminal controls in displayed text while preserving printable Unicode, and rejected Unicode-blank grocery labels. ([#107](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/107), [#118](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/118), [fixes](https://github.com/AY2627S1-CS2113-W13-4/tp/commit/58eb76f), [#148](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/148))

- **Cross-session persistence, including packaged-JAR launches.** Implemented restoration and autosave of recipes, settings, plans, groceries, and meal logs using versioned JSON outside the JAR. Preserved independent recipe snapshots, decimal quantities, dated entries, and serving counts. Addressed partial writes, concurrent writers, and damaged data through validated temporary writes, atomic replacement where supported, backups, quarantine, and a process-lifetime file lock. Added save retries and legacy-schema migration with a preserved original backup. **Credit:** the Jackson Java library supplies JSON parsing/serialisation; I implemented the state mapping, validation, recovery, and application integration. ([#121](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/121))

### Testing and code quality

Strengthened generator preconditions and exception-cause propagation ([#41](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/41)). Added direct tests of `PortionAdjustment`, `JsonSessionStorage`, `LoadResult`, `GroceryItem`, and `IngredientParser`, plus regressions for occurrence isolation, scaling after dietary repair, and preserved state after errors. Separate-process JAR tests verify retention, recovery, and literal Unicode/symbols across restarts; UTF-8 transcripts make these portable.

Introduced injectable file operations for deterministic storage failures, including simultaneous acquisition and cleanup failures: the original exception remains primary and cleanup is suppressed. Added JaCoCo checks requiring **more than 95% line and branch coverage for each of 26 source files**, including their nested DTOs and helpers, and documented how to run them. ([#121](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/121), [#138](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/138))

Added a **244-case hostile-input suite** covering negative/oversized numbers, non-ASCII digits, terminal controls, literal injection-like text, and malformed inputs up to 100,000 characters. Checks retain plans, generation requests, groceries, recipes, meal logs, dietary settings, and targets after rejection. Also wrote eight documentation-validator tests, including a real Pandoc regression for a heading missing its required blank line. These refinements are in [#148](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/148), currently under review.

**Local validation, 3 November 2026:** after adapting the fixture to the merged `MealEntry` API, the project-wide suite passes **2,181 tests**, Checkstyle, and coverage gates. The project total includes teammates' tests; the 244-case suite above identifies my added hostile-input coverage.

### Documentation

- **User Guide:** Wrote the [grocery-generation](../UserGuide.md#generating-a-grocery-list), [nutrition-warning](../UserGuide.md#nutrition-target-warnings), [portion-adjustment](../UserGuide.md#adjusting-a-planned-meals-portions), [ingredient-customisation](../UserGuide.md#customising-a-planned-meals-ingredients), [clear-diet](../UserGuide.md#clearing-dietary-preferences-and-restrictions), and [data-retention](../UserGuide.md#data-retention) sections. Added calculations, limitations, state effects, recovery guidance, and [Unicode/control-character rules](../UserGuide.md#reading-command-formats). ([#75](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/75), [#121](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/121), [#148](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/148))
- **Developer Guide:** Documented implementations, design choices, use cases, and manual tests. Added [14 grocery/nutrition/diagnostic diagrams](../diagrams/oilybikechain/README.md) and [storage class/autosave diagrams](../DeveloperGuide.md#persistence-and-recovery), with PlantUML sources and SVGs. Updated them for `PlannedMeal`/`generateForPlan` and inspected grocery readability in the exported PDF. Added [diagram and export verification](../diagrams/README.md#verification), covering source/SVG pairs, includes, links, and rendered HTML anchors before PDF printing. ([#85](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/85), [#118](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/118), [#121](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/121), [#148](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/148))

### Team tasks and reviews

- Standardised Developer Guide UML across the team's features, correcting course notation and splitting dense diagrams into focused views with regenerated SVGs. ([#98](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/98))
- Aligned the DG with AB3 and course-required Appendices A–E, repaired Appendix A's exported navigation, refreshed both guide PDFs, and cleaned up About Us avatars and portfolio links. Preserved contributor credits throughout the shared documentation review. ([#148](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/148))
- Reviewed dietary repair ([#73](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/73#pullrequestreview-5637520510)), meal-log changes ([#120](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/120#pullrequestreview-5728470879)), and launcher tests ([#135](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/135#pullrequestreview-5736945499)). My meal-log review identified parser edge cases, consistency with generator constraints, and numbering after filtered deletions as regression risks. Also merged teammates' changes, including dietary planning ([#73](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/73)) and meal-log corrections ([#120](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/120)).

# Justin Lee - Project Portfolio Page

## Overview

### Project: MishMash

MishMash is a Java command-line meal-prep planner. Users can manage recipes, generate meal plans and grocery lists, and track nutrition against their targets.

## Summary of Contributions

**Code contributed:** [My tP Code Dashboard report](https://nus-cs2113-ay2627-s1.github.io/tp-dashboard/?search=oilybikechain&breakdown=true).

### Enhancements implemented

- **New feature: Generating grocery lists (`generate-list`).**
  - **What it does:** Collects the ingredients from every meal occurrence in the active plan and combines exact ingredient-name and unit matches into a stored grocery list.
  - **Justification:** A meal plan is not directly actionable for shopping; users need consolidated quantities for all planned meals.
  - **Highlights:** Implemented the grocery model and aggregation logic, then connected it to shared plan state, command parsing, and CLI recovery. `BigDecimal` preserves decimal quantities, while a composite name-unit key prevents incompatible units from being combined. Results retain first-appearance order, and validation failures leave the previous grocery snapshot unchanged.
  - **Evidence:** [#14](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/14), [#21](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/21), [#32](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/32).

- **New feature: Combined daily nutrition totals.**
  - **What it does:** Calculates calories, protein, carbohydrates, and fats for each planned day.
  - **Justification:** Per-recipe values do not show whether a complete day's meals meet the user's needs.
  - **Highlights:** Added an immutable `NutritionTotals` value and a model-level `DayPlan` aggregation API. Per-nutrient sums use `long` values so adding several valid integer recipe values cannot overflow when target and shortfall logic reads them.
  - **Evidence:** [#69](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/69).

- **New feature: Nutrition target warnings.**
  - **What it does:** Shows an ordered warning for every displayed daily total that is strictly above its saved generation target, including the excess, actual value, and target.
  - **Justification:** The generator accepts values within a tolerance, so an accepted plan can still exceed an exact target; users need that distinction when reviewing all days or one selected day.
  - **Highlights:** Retained each plan's targets with its state, supplied current targets to the viewer at execution time, and separated exact excess calculation from the generator's tolerance rule. Partial targets, dietary plan repair, missing plans, and values beyond the integer range are handled without changing the viewed plan.
  - **Evidence:** [#74](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/74).

### Testing and code quality

Added JUnit tests for grocery aggregation, decimal quantities, incompatible units, repeated meals, parser boundaries, snapshot replacement, and unchanged state after errors. Nutrition tests cover empty and repeated days, overflow-safe totals, partial or absent targets, strict comparison boundaries, selected-day output, dietary repair, and CLI integration. Strengthened generator preconditions and preserved original exception causes so invalid commands remain recoverable while diagnostics retain their root cause. Evidence: [#21](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/21), [#32](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/32), [#41](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/41), [#69](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/69), [#74](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/74).

### Contributions to documentation

- **User Guide:** Wrote [Generating a Grocery List](../UserGuide.md#generating-a-grocery-list), the [Daily Nutrition Totals](../UserGuide.md#viewing-a-meal-plan) explanation, [Nutrition Target Warnings](../UserGuide.md#nutrition-target-warnings), and [Recovering from Invalid Plan or Recipe Arguments](../UserGuide.md#recovering-from-invalid-plan-or-recipe-arguments). These sections include syntax, worked calculations, state effects, boundary behaviour, and recovery guidance. Evidence: [#75](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/75).
- **Developer Guide:** Wrote [Generating a grocery list](../DeveloperGuide.md#generating-a-grocery-list), [Combined daily nutrition totals](../DeveloperGuide.md#combined-daily-nutrition-totals), [Detecting exceeded nutrition targets](../DeveloperGuide.md#detecting-exceeded-nutrition-targets), and [Defensive checks and generation diagnostics](../DeveloperGuide.md#defensive-checks-and-generation-diagnostics), together with related use cases and manual tests. Contributed 14 [grocery, nutrition, warning, and diagnostic diagrams](../diagrams/oilybikechain/README.md), with PlantUML sources and SVGs. Evidence: [#75](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/75), [#85](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/85).

### Contributions to team tasks

- Standardised and simplified the team's Developer Guide UML across grocery, nutrition, recipe, meal-recording, target, shortfall, and plan-management features. Split dense diagrams into focused class and sequence views, corrected course notation, and regenerated guide-width SVGs: [#98](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/98).
- Added my profile and portfolio destination to the shared About Us page: [#10](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/10).

### Review and integration contributions

Helped integrate teammates' recipe listing, logging, Java-convention cleanup, CLI help, dietary planning, viewing tests, and add-recipe documentation by merging [#22](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/22), [#29](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/29), [#31](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/31), [#33](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/33), [#40](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/40), [#73](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/73), and [#94](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/94) into the shared repository.

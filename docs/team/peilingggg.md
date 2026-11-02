# Lee Pei Ling - Project Portfolio Page

## Overview

### Project: MishMash

MishMash is a Java command-line meal-prep planner for managing recipes, planning meals, generating grocery lists, and tracking nutrition against personal targets.

## Summary of Contributions

**Code contributed:** [My tP Code Dashboard report](https://nus-cs2113-ay2627-s1.github.io/tp-dashboard/?search=peilingggg&breakdown=true).

- **New feature: Adding recipes (`add-recipe`)** ([#16](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/16), [#19](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/19), [#34](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/34)).
  - **What it does:** Adds recipes with nutrition values, multiple ingredients, decimal quantities, and optional food categories.
  - **Justification:** Lets users build a recipe collection for personalised meal planning.
  - **Highlights:** Implemented the original recipe models, recipe book, parser, command, and CLI integration. Added validation at parsing and model boundaries, precise ingredient quantities using `BigDecimal`, and error recovery that preserves existing recipes.

- **New feature: Setting dietary preferences (`set-diet`)** ([#73](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/73)).
  - **What it does:** Sets a named diet, avoided food categories, and excluded ingredients. Filters future meal generation and repairs conflicting existing days.
  - **Justification:** Lets users change restrictions while keeping compatible planned meals.
  - **Highlights:** Implemented shared dietary rules, immutable preferences, recipe classification through `food/` tags, and repair using the original nutrition targets. Successful repair refreshes existing groceries; failed repair retains the new preferences and clears incompatible plans and groceries.
  - **Credits:** Built on the team's meal-plan and grocery generators.

- **New feature: Viewing dietary preferences (`view-diet`)** ([#81](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/81)).
  - **What it does:** Displays the active diet and alphabetically sorted exclusions, using `none` for empty fields.
  - **Justification:** Helps users check their restrictions before planning meals.
  - **Highlights:** Reads the latest shared preferences at execution time, rejects extra arguments, and leaves session data unchanged.

- **New feature: Command help and consistent prompts** ([#40](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/40)).
  - Added `help` with command descriptions and syntax, plus consistent input prompts after successful commands, errors, and cancellations, to improve command discovery.

- **Enhancements and testing:**
  - Improved `add-recipe` diagnostics, exception-cause preservation, logging, and usage guidance ([#34](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/34)).
  - Expanded JUnit and integration tests for parsing, stored recipe fields, confirmation results, failure-state preservation, and session recovery. Added tests for JLine's `add-recipe` prefix completion ([#123](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/123), [#125](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/125)).
  - Expanded `FoodTag` and `DietaryPlanner` tests for aliases, validation, generation, repair, and preserved customisations. Tested combined restrictions, grocery refresh, failed-repair recovery, and `view-diet` state preservation; added explicit three-meals-per-day assertions ([#112](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/112), [#124](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/124)).

- **Documentation:**
  - **User Guide:** Wrote [Adding a Recipe](../UserGuide.md#adding-a-recipe), [Setting Dietary Preferences](../UserGuide.md#setting-dietary-preferences), and [Viewing Dietary Preferences](../UserGuide.md#viewing-dietary-preferences), covering syntax, examples, errors, and recovery ([#87](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/87), [#93](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/93)).
  - **Developer Guide:** Wrote implementation explanations and PlantUML/SVG diagrams for [Adding a recipe](../DeveloperGuide.md#adding-a-recipe), [Applying dietary restrictions](../DeveloperGuide.md#applying-dietary-restrictions), and [Viewing dietary preferences](../DeveloperGuide.md#viewing-dietary-preferences), including responsibilities, workflows, defensive checks, and alternatives ([#67](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/67), [#93](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/93), [#94](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/94)).
  - Updated shared architecture, requirements, use cases, and manual tests. Later aligned the guide with completed features and persistence, reorganised navigation, and replaced numbered figure references ([#52](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/52), [#127](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/127)).

- **Team contributions:**
  - Resolved startup integration conflicts while retaining the greeting and shared scanner ([#18](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/18)).
  - Reviewed reset behaviour ([#88](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/88)), UML clarity ([#98](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/98)), CLI changes ([#116](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/116)), and storage recovery and autosave behaviour ([#121](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/121)).

# Lee Pei Ling - Project Portfolio Page

## Overview

### Project: MishMash

MishMash is a Java command-line meal-prep planner. Users can manage recipes, generate meal plans and grocery lists, and track nutrition against their targets.

## Summary of Contributions

**Code contributed:** [My tP Code Dashboard report](https://nus-cs2113-ay2627-s1.github.io/tp-dashboard/?search=peilingggg&breakdown=true).

### Enhancements implemented

- **New feature: Adding recipes (`add-recipe`).**
  - **What it does:** Creates recipes with nutrition, multiple ingredients, decimal quantities, and optional food categories.
  - **Justification:** Users need their own recipes for personalised meal planning.
  - **Highlights:** Implemented recipe storage, parsing, validation, and CLI integration. Parser and model checks reject invalid data before insertion and protect direct Java callers. `BigDecimal` preserves ingredient-quantity precision.
  - **Evidence:** [#16](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/16), [#19](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/19), [#34](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/34).

- **New feature: Dietary preferences and plan repair (`set-diet`).**
  - **What it does:** Saves a named diet, avoided food categories, and excluded ingredients; filters generation, repairs conflicting days, and refreshes existing groceries.
  - **Justification:** Users can change dietary requirements while retaining compatible planned days.
  - **Highlights:** Added immutable preferences, recipe classification, and normalised exclusions. Repair preserves compatible days and original nutrition targets. Failed repair saves the preferences and clears incompatible plans and groceries. Coordinating these states required explicit failure handling.
  - **Credits:** Reused the team's meal-plan and grocery generators.
  - **Evidence:** [#73](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/73).

- **New feature: Viewing dietary preferences (`view-diet`).**
  - **What it does:** Displays the active diet and sorted exclusions, with `none` for unrestricted fields.
  - **Justification:** Users can verify their settings before planning meals.
  - **Highlights:** Reads shared settings at execution time to display the latest profile. Rejects extra arguments and preserves session data.
  - **Evidence:** [#81](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/81).

### Testing and code quality

Added JUnit tests for parsing boundaries, model validation, unchanged state after errors, and CLI recovery. Dietary tests cover classification, exclusions, compatible-day preservation, repair failure, grocery refresh, and current-profile viewing. Improved exception-cause preservation, logging, null checks, assertions, and usage guidance. Evidence: [#19](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/19), [#34](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/34), [#73](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/73), [#81](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/81).

### Contributions to documentation

- **User Guide:** Wrote [Adding a Recipe](../UserGuide.md#adding-a-recipe), [Setting Dietary Preferences](../UserGuide.md#setting-dietary-preferences), and [Viewing Dietary Preferences](../UserGuide.md#viewing-dietary-preferences), including syntax, examples, expected output, validation, and recovery. Evidence: [#87](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/87), [#93](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/93).
- **Developer Guide:** Wrote [Adding a recipe](../DeveloperGuide.md#adding-a-recipe), [Applying dietary restrictions](../DeveloperGuide.md#applying-dietary-restrictions), and [Viewing dietary preferences](../DeveloperGuide.md#viewing-dietary-preferences), explaining responsibilities, alternatives, errors, and verification. Contributed three [add-recipe diagrams](../diagrams/peilingggg/add-recipe/README.md) and four [dietary diagrams](../diagrams/peilingggg/dietary/README.md), with PlantUML sources and SVGs; refined readability and course notation. Evidence: [#67](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/67), [#93](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/93), [#94](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/94).

### Contributions to team tasks

- Updated shared Developer Guide design, practices, requirements, and manual tests: [#52](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/52).
- Added `help` and consistent CLI prompts: [#40](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/40).
- Resolved startup integration conflicts, preserving the greeting and shared scanner: [#18](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/18).

### Review contributions

- Checked reset behaviour, retained settings, and recoverable exceptions: [#88 review](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/88#pullrequestreview-5671975477).
- Reviewed UML readability and suggested removing attributes duplicated by associations: [#98 review](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/98#pullrequestreview-5700818931).
- Reviewed documentation consistency, examples, and recovery instructions: [#80 review](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/80#pullrequestreview-5654293009).

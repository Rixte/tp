# Samantha Sam's Project Portfolio Page

### Project: MishMash

MishMash is a Java command-line meal-prep planner. Users manage recipes, generate meal plans and grocery lists, track meals and nutrition targets, and save their data between sessions.

Given below are my contributions to the project.

- **Code contributed:** [My individual tP Code Dashboard report](https://nus-cs2113-ay2627-s1.github.io/tp-dashboard/?search=rixte&breakdown=true).

- **New feature: Meal-plan and grocery-list viewing (v1.0)** ([#15](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/15), [#23](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/23)).
  - **What it does:** `view-plan` displays all days or a selected day, meals and nutrition totals; `view-list` displays generated groceries with quantities and item counts.
  - **Justification:** Users can inspect their plan and shopping needs without regenerating or changing data.
  - **Highlights:** Implemented viewers, commands and parsing. Execution-time suppliers prevent stale results; absent groceries differ from a generated empty list. Added selection, precision and repeated-use regressions ([#22](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/22)).

- **New feature: Daily nutrition shortfall checking (v2.0)** ([#65](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/65), [#68](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/68)).
  - **What it does:** `check-shortfall` reports each day's deficits against supplied calorie and macronutrient targets; omitted nutrients remain unchecked.
  - **Justification:** Users can identify under-planned days before preparing meals.
  - **Highlights:** Reused the team's nutrition models while implementing strict comparisons independent of generation tolerance. Kept days independent and used long arithmetic. Added 144 regression cases in #68, including all 24 target orders, 15 target combinations, numeric boundaries and recovery.

- **New feature: Partial recipe editing (v2.0)** ([#76](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/76)).
  - **What it does:** `edit-recipe` updates selected fields, including nutrition, ingredients and food classifications, while retaining omitted fields.
  - **Justification:** Users can correct recipes without deleting and re-entering them.
  - **Highlights:** Introduced immutable `RecipeChanges`, reused recipe validation and constructed the replacement and feedback before publication. This avoids partial updates while existing plans, groceries and recorded meals retain their snapshots. Tested command reuse and failed regeneration.

- **New feature: Meal-plan reset (v2.0b)** ([#88](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/88), [#117](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/117)).
  - **What it does:** `reset-plan` clears the active plan, generation request and groceries, retaining recipes, dietary preferences, saved personal targets and recorded meals.
  - **Justification:** Users can restart planning without losing unrelated work.
  - **Highlights:** Made resets repeatable and failures recoverable. Added 92 reset-specific cases covering commands, parsing, diagnostics and integrated workflows, including the distinction between discarded plan targets and retained personal targets.

- **Enhancement: Confirmation before plan changes (v2.0b)** ([#91](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/91)).
  - **What it does:** Requests agreement before reset, replacement generation or dietary changes that can repair or clear a plan.
  - **Justification:** Prevents accidental loss of the current plan and groceries.
  - **Highlights:** Kept prompting separate from execution; only `y`/`yes` confirms, while other answers and EOF cancel. Added 41 cases for cancellation, invalid syntax, failed generation and dietary repair.
  - **Credits:** Extended the team's [existing confirmation workflow](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/25).

- **New feature: Interactive command entry (v2.1)** ([#116](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/116), [#130](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/130)).
  - **What it does:** Provides Tab completion, editable input and session history for long commands.
  - **Justification:** Reduces repeated typing and makes corrections easier.
  - **Highlights:** Preserving syntax and confirmation safety across terminal modes required separate console adapters. Implemented usage-derived completion, plain-input fallback and interruption/EOF handling. Kept greeting and confirmation answers out of history/completion; tested the real editor using in-memory terminals.
  - **Credits:** Integrated [JLine](https://jline.org/), which supplies the editing and history engine; my contribution covers its application-specific integration and tests.

- **Enhancements to existing features:**
  - Added startup branding ([#17](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/17)), diagnostic logging and package organisation ([#26](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/26), [#31](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/31)). Refactored shared parsing, help, formatting and storage workflows, and strengthened Checkstyle enforcement ([#33](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/33), [#130](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/130)).
  - Separated session execution from global console/logging setup; preserved error causes and introduced reusable logging and response-order test helpers ([#35](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/35), [#83](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/83)). Added assertion-enabled/disabled regressions ([#114](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/114)).
  - Introduced 100% line/branch coverage gates for three plan-changing commands ([#91](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/91)); collected separate-JVM coverage, tested EOF save failures and terminal fallback, and extended the gates to `MishMash` and `ConsoleInputs` ([#135](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/135)). At #135, those two files reached 100% line/branch coverage.

- **Documentation:**
  - **User Guide:** Created the shared guide structure, quick start and troubleshooting ([#27](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/27)). Wrote [plan viewing](../UserGuide.md#viewing-a-meal-plan), [grocery viewing](../UserGuide.md#viewing-a-grocery-list), [shortfall](../UserGuide.md#checking-nutrition-shortfalls), [editing](../UserGuide.md#editing-a-recipe), [reset](../UserGuide.md#resetting-a-meal-plan), [confirmation](../UserGuide.md#confirming-plan-changes) and [interactive-input](../UserGuide.md#interactive-command-entry) instructions. Updated worked examples, persistence, meal workflows and recovery guidance ([#66](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/66), [#130](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/130)).
  - **Developer Guide:** Wrote design, alternatives and verification for [editing](../DeveloperGuide.md#editing-a-recipe), [shortfalls](../DeveloperGuide.md#checking-nutrition-shortfalls), [reset](../DeveloperGuide.md#resetting-a-meal-plan), [confirmation](../DeveloperGuide.md#confirming-plan-changes), [plan viewing](../DeveloperGuide.md#viewing-a-meal-plan) and [grocery viewing](../DeveloperGuide.md#viewing-a-grocery-list). Maintained [ten UML diagrams](../diagrams/rixte/README.md): two class, six sequence, one activity and one state machine, with editable sources and SVGs ([#97](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/97), [#101](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/101)). Reorganised the guides to the AB3 structure, added [developer setup](../SettingUp.md) and documented [terminal testing](../DeveloperGuide.md#interactive-command-entry) ([#101](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/101), [#130](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/130)).

- **Project management:**
  - Created team checklists for [guide corrections](https://github.com/AY2627S1-CS2113-W13-4/tp/issues/102), [final UG work](https://github.com/AY2627S1-CS2113-W13-4/tp/issues/129), [PPP completion](https://github.com/AY2627S1-CS2113-W13-4/tp/issues/132) and [per-file coverage](https://github.com/AY2627S1-CS2113-W13-4/tp/issues/133).
  - Cross-checked original commits and restored teammates' attribution after file moves and refactoring ([#48](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/48)).

- **Community:**
  - Suggested [checking unchanged state when formatting fails](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/34#discussion_r4318110743) and [asserting meal counts rather than relying on sets](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/112#discussion_r4432780025).
  - Flagged [broken DG references](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/127#pullrequestreview-5733262068) and clarified [empty-log versus unmatched-date output](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/134#discussion_r4442449031).
  - Identified [lost acquisition errors during cleanup](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/138#discussion_r4443858492) and [test-channel chaining defects](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/138#discussion_r4443872190); verified the fixes and regression tests before approval.

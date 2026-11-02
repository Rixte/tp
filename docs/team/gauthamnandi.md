# Gautham - Project Portfolio Page

## Overview

### Project: MishMash

MishMash is a Java command-line meal-prep planner. Users can manage recipes, generate meal plans and grocery lists, and track nutrition against their targets.

## Summary of Contributions

**Code contributed:** [My tP Code Dashboard report](https://nus-cs2113-ay2627-s1.github.io/tp-dashboard/?search=gauthamnandi&breakdown=true).

### Enhancements implemented

- **Meal-plan generation (`generate-plan`) - merged.** Builds a plan of one to seven days, each with three distinct recipes, from a user's calorie and macronutrient targets. This turns the recipe book into a usable plan. Each day must be within 15% of every supplied target. The main challenge was keeping generation fast as the recipe book grows. The generator checks every three-recipe combination for up to 5,000 combinations (about 32 recipes) and otherwise samples 5,000 at random, so the work stays fixed and small books never miss a valid day. Randomness is injected, so tests use a seeded generator and get repeatable plans. I also added the parser, request, plan and day models, and the shared `ActiveMealPlan` holder. Evidence: [#24](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/24), user story [#5](https://github.com/AY2627S1-CS2113-W13-4/tp/issues/5).
- **Meal recording (`record-meal`, `view-meals`) - merged.** Lets users log the meals they actually ate and see each meal's calories and macros with running totals. This is kept separate from the plan, which holds meals the user intends to eat. `MealLog` stores the immutable `Recipe` itself rather than its index, so recorded meals keep their nutrition after `edit-recipe` or `delete-recipe`. Totals are computed from the log on demand with `long` arithmetic, so there is no second copy to drift out of step and no overflow. `Parser` owns the log and passes it to the commands, so the `Command` interface and existing commands did not change. Evidence: [#82](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/82), closing [#43](https://github.com/AY2627S1-CS2113-W13-4/tp/issues/43) and [#44](https://github.com/AY2627S1-CS2113-W13-4/tp/issues/44).
- **In progress, not yet merged:** an explanation of why plan generation fails ([#59](https://github.com/AY2627S1-CS2113-W13-4/tp/issues/59)).

### Testing

[#82](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/82) adds 66 JUnit test methods in five classes (`MealLogTest`, `MealLogViewerTest`, `RecordMealCommandTest`, `ViewMealsCommandTest`, `ParserRecordMealTest`), plus console-workflow tests in `MishMashTest`. They cover totals beyond the `int` range, exact output, every invalid index, the empty log and empty recipe book, recipes edited or deleted after recording, and failed commands leaving the log unchanged. [#24](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/24) adds eight test classes for generation, including seeded-random plans and parser and target validation.

### Contributions to documentation

- **User Guide:** I wrote [Generating a Meal Plan](../UserGuide.md#generating-a-meal-plan), [Recording a Meal](../UserGuide.md#recording-a-meal) and [Viewing Recorded Meals](../UserGuide.md#viewing-recorded-meals), with command formats, examples and error recovery. I also added the quick-start workflow steps, troubleshooting rows, FAQ entry and at-a-glance rows for these commands. Evidence: [#79](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/79), addressing [#64](https://github.com/AY2627S1-CS2113-W13-4/tp/issues/64).
- **Developer Guide:** I wrote [Recording meals and tracking nutrition](../DeveloperGuide.md#recording-meals-and-tracking-nutrition), covering architecture, error handling, a table of rejected design alternatives, limitations, and use cases UC11 and UC12. I also wrote the [Generating a meal plan](../DeveloperGuide.md#generating-a-meal-plan) design section and the [manual test steps](../DeveloperGuide.md#record-and-view-meals). Evidence: [#79](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/79), addressing [#39](https://github.com/AY2627S1-CS2113-W13-4/tp/issues/39).
- **UML diagrams:** I created the first [meal-recording diagrams](../diagrams/record-meal/README.md): a class diagram and sequence diagrams for recording and viewing meals, with sources and rendered SVGs committed together. A teammate later revised them to match the course notation and split them further in [#98](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/98).

### Team tasks and review contributions

- I reviewed and approved teammates' pull requests after checking that tests and Checkstyle pass: [#23](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/23#pullrequestreview-5492571767) (full diff read, 402 tests run, and manual CLI checks with malformed day arguments), [#25](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/25#pullrequestreview-5500083966), [#76](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/76#pullrequestreview-5643716142), [#86](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/86#pullrequestreview-5671545868), [#91](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/91#pullrequestreview-5677312226).
- On [#83](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/83#pullrequestreview-5670745091) I requested changes after finding two session-crashing bugs: a null error message that would throw inside a `catch` block, and new checks that made a single bad command fatal. I approved after they were fixed ([approval](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/83#pullrequestreview-5671220249)).
- On [#93](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/93#pullrequestreview-5695495408) I checked the `set-diet` and `view-diet` documentation against the implementation, including error messages, referenced test classes, `describe()` output and the User Guide example numbers, and validated the SVGs.

**Current scope:** `generate-plan`, `record-meal` and `view-meals`, with their guides and diagrams, are merged. Work on [#59](https://github.com/AY2627S1-CS2113-W13-4/tp/issues/59) is not yet merged.

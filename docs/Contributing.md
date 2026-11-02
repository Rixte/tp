# Contributing to MishMash

## Claiming and correcting authorship with RepoSense

RepoSense normally uses Git history to identify contributions. Use `@@author` tags when necessary to credit your work or restore the original author's credit after moving or refactoring content. These tags change RepoSense attribution, not Git commit history.

Use the comment syntax appropriate to the file:

| File type | Start tag | End tag |
| --- | --- | --- |
| Java | `// @@author oilybikechain` | `// @@author` |
| Markdown | `<!-- @@author oilybikechain -->` | `<!-- @@author -->` |

1. Identify the code or documentation belonging to the contributor. Replace `oilybikechain` with the appropriate RepoSense author identifier when crediting someone else.
2. Put the start tag on its own line immediately before that contribution, including its heading or explanatory comments.
3. Put the end tag on its own line immediately after it. Without an end tag, attribution continues to the next start tag or the end of the file.
4. To fix incorrect credit, correct the existing start tag or add a correctly scoped pair around the original contribution. Keep unrelated contributions outside the pair.
5. Commit and push the change to the branch analysed by the tracker, then check the next refreshed report.

Do not add an end tag without a corresponding start tag; it can leave subsequent content unattributed. Markdown HTML comments remain hidden in the rendered guide.

Reference: [RepoSense authorship-tag guide](https://reposense.org/ug/usingAuthorTags.html).

<!-- @@author oilybikechain -->
### Contribution history: oilybikechain

*Section author: oilybikechain*

The following non-merge commits reachable from the current branch establish the scope of these
contributions. Merge authorship does not imply ownership of the merged feature; the original
meal-plan generator, plan viewer, grocery formatter, dietary repair, and shortfall command have
separate contributors. The sections below describe the current integration with those components.

| Commit | Contribution documented here |
| --- | --- |
| `667d642` | Ingredient quantity model and grocery aggregation by exact name and unit. |
| `c3f6e95` | Grocery aggregation regression tests and API documentation. |
| `6b960ad` | `generate-list` parsing, command execution, and shared grocery snapshot integration. |
| `ba80853` | Preserved exception causes, generator assertions, and generation diagnostics. |
| `4c82e81` | Combined daily nutrition totals through `DayPlan` and `NutritionTotals`. |
| `f81dc10` | Daily warnings when planned nutrition exceeds exact generation targets. |

Earlier implementations on other branches are not additional features: `5ff6c3f` is the earlier
grocery integration, and `b814afb` is the pre-update nutrition-warning implementation. The current
guide describes their integrated replacements above. Profile and README edits are not product features.

<!-- @@author -->


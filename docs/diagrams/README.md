# Developer Guide diagrams

Editable PlantUML sources live here; matching generated SVGs live under
[`docs/images`](../images). Contributor folders contain feature subfolders where useful.
Meal-recording diagrams live under `gauthamnandi`; shared storage diagrams use `storage`.
The [Developer Guide](../DeveloperGuide.md) embeds the checked-in SVGs directly.

| Contributor or feature | Sources and rendering instructions | Matching SVG folder | Pairs |
| --- | --- | --- | --- |
| oilybikechain: groceries, nutrition, warnings, diagnostics | [oilybikechain](oilybikechain/README.md) | [images/oilybikechain](../images/oilybikechain) | 14 |
| GauthamNandi: dated meal recording, viewing, corrections and deletion | [gauthamnandi](gauthamnandi/README.md) | [images/gauthamnandi](../images/gauthamnandi) | 6 |
| neil0618: listing recipes | [list-recipes](neil0618/list-recipes/README.md) | [images/neil0618/list-recipes](../images/neil0618/list-recipes) | 3 |
| neil0618: deleting recipes | [delete-recipe](neil0618/delete-recipe/README.md) | [images/neil0618/delete-recipe](../images/neil0618/delete-recipe) | 3 |
| neil0618: nutrition targets | [targets](neil0618/targets/README.md) | [images/neil0618/targets](../images/neil0618/targets) | 6 |
| peilingggg: recipe addition and dietary preferences | [add-recipe](peilingggg/add-recipe/README.md), [dietary](peilingggg/dietary/README.md) | [images/peilingggg](../images/peilingggg) | 7 |
| Rixte: editing, plan confirmation/reset, shortfalls and viewers | [rixte](rixte/README.md) | [images/rixte](../images/rixte) | 10 |
| oilybikechain: persistence integration | [storage](storage/README.md) | [images/storage](../images/storage) | 2 |

There are 51 source/SVG pairs. The architecture overview is maintained directly as
[SVG](../images/architecture/overview.svg), so it has no PlantUML counterpart.
Keep existing author markers, section-author labels, original-commit mappings, and
shared-style credits when changing diagrams. Folder organisation does not transfer authorship.

## Final integration review

Reviewed on 3 November 2026 against team master
[`d0b5723`](https://github.com/AY2627S1-CS2113-W13-4/tp/commit/d0b5723fb2d1ac92144c268156a715ad95e2f22c)
plus this documentation change. This completes the technical review requested by
[#131](https://github.com/AY2627S1-CS2113-W13-4/tp/issues/131), following
[#102](https://github.com/AY2627S1-CS2113-W13-4/tp/issues/102).

### Organisation and owner evidence

- **GauthamNandi:** [merged PR #134](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/134)
  explicitly confirms organisation, source/SVG pairs, links and implementation checks.
  The meal-recording diagrams are organised under `gauthamnandi`.
- **neil0618:** [merged PR #139](https://github.com/AY2627S1-CS2113-W13-4/tp/pull/139)
  organises all 12 diagrams under `neil0618/`, updates includes and incoming links,
  and integrates the no-op target-setting outcome. This review rechecks those paths.
- **oilybikechain:** this change verifies all 14 grocery/nutrition/diagnostic pairs
  and both storage pairs, keeps their established folders, and records the normal-size
  PDF review below. The [contribution map](oilybikechain/README.md) retains original credits.

The original duplicate-association, activity-decision and meal-viewer construction-order
fixes remain present and have not been repeated. Retired target-value diagrams are not restored.

### Final implementation checks

| Area | Result |
| --- | --- |
| Planned portions and custom ingredients | The source model contains `PlannedMeal`, its portion count and optional override. Collection uses `getPlannedMeals()` and `getScaledIngredients()` before exact name/unit aggregation. |
| Grocery publication and command results | The command sequence now follows `executeWithResult`, publishes only completed groceries, and returns a `DONE` result. Missing-plan and failure behaviour remains in the adjacent DG prose. |
| Nutrition warnings | Daily sums read scaled `PlannedMeal` values. Viewer warnings use meal summaries and retained generation targets. Omitted warning-label parameters are marked with `...`. |
| Console recovery | The recovery sequence now calls `ConsoleInput.readCommand(prompt)`, replacing the removed `displayCommandPrompt` method. |
| Dated records and corrections | The meal-recording diagrams show `MealEntry`, dates, servings, edit/delete commands and per-date totals. Viewer construction still precedes totals lookup. Empty and unmatched-date outcomes remain documented. |
| Meal replacement | Replacement creates one uncustomised portion and uses `ActiveMealPlan.update`, clearing stale groceries. The existing occurrence-based grocery and nutrition diagrams apply; the replacement contract is described in the DG. |
| Persistence | Storage diagrams show shared application roots and autosave after `DONE`/`WARNING` results. The DG covers dated entries, schema migration, backup recovery, locking and packaged-JAR restarts. |
| Recipe deletion | Corrected a remaining activity label: range errors display only `delete-recipe` usage. Confirmation is described without an obsolete prompt transcript. |

### Grocery readability decisions

Both decisions are based on a locally exported Developer Guide PDF at normal A4 page
size, using 11-point body text. Follow the [PDF export instructions](#pdf-export) to
recreate it. Locate figures by their titles, not page numbers, which change when the
guide is edited.

- **generate-list: aggregate and publish a completed snapshot — improved.** Removed the
  unused plan lifeline, shortened participant labels, wrapped the title and kept ingredient
  traversal in its existing separate reference diagram. Increased diagram text to 16 pixels.
  The SVG width fell from 1017 to 756 pixels, bringing printed message labels to approximately
  10.6 points against 11-point body text. An opaque white reference frame keeps the lifeline
  and activation behind it from crossing the collection label. Calls, returns, activation bars
  and the reference were inspected in the final PDF; none overlaps its label.
- **Grocery source and snapshot model — improved.** Removed traversal methods from this
  stored-reference view and moved repeated collection annotations into one legend. Retained
  role names, multiplicities, portions and the actual snapshot/override/source associations.
  The long snapshot route remains explicit rather than introducing a fictional model class.
  The figure shrank from 659 by 876 to 503 by 681 pixels; key labels print at 12 points.
  The complete route, arrowheads, role labels and legend were inspected together in the PDF.

The current published guide and all 52 of its local SVG URLs returned HTTP 200 during the review.
The final Markdown export uses the same SVG files as the web guide. The published web
page receives these changes when this documentation is merged and deployed; this review
does not claim an unpublished change is already live. Re-run the checks after any further
implementation or documentation merge. As confirmed for this review, the DG targets current master
for the upcoming submission release. The public [v2.0 release](https://github.com/AY2627S1-CS2113-W13-4/tp/releases/tag/v2.0)
predates the storage and newer meal-editing changes. Verify the final release JAR against this guide
when it is published; this review does not claim those features exist in v2.0.

## Verification

Run the dependency-free link/pair check with Python 3, then syntax-check all sources with
Java 25 and the documented PlantUML **1.2026.9 LGPL** renderer:

```powershell
python docs/diagrams/verify.py
java -version
java -jar $env:PLANTUML_JAR -version
$sources = (Get-ChildItem docs/diagrams -Recurse -Filter *.puml).FullName
java "-Djava.awt.headless=true" -jar $env:PLANTUML_JAR -checkonly @sources
python docs/diagrams/verify.py --plantuml-jar $env:PLANTUML_JAR
```

The script checks pairs, SVG XML, recursive relative includes, and local links/anchors
in both guides, diagram READMEs and team portfolios. With `--plantuml-jar`, it also compares
each available SVG embedded source with its `.puml` file, ignoring blank lines and author
comments outside the diagram. It reports SVGs without embedded sources separately rather
than treating missing metadata as proof of a match. Seven existing Pei Ling SVGs have no
such metadata. That comparison does not establish whether included styles or Java behaviour
changed; it does not replace code comparison, PlantUML validation or visual inspection.
Each feature README gives SVG output paths.

Markdown heading checks are heuristic: they cannot establish which headings Pandoc actually renders.
For submission exports, also check the generated HTML with `--html PATH` as shown below. This catches
contents links to heading-looking text that rendered as an ordinary paragraph. Repeat `--html` to check
multiple exported documents. Run the validator's regression tests, including the missing-blank-line case,
with `python -m unittest discover -s docs/diagrams -p test_verify.py`.

## PDF export

The guide follows the [AB3 structure](https://se-education.org/addressbook-level3/DeveloperGuide.html),
with the course-specific separate Appendices A–E. Main content remains in `DeveloperGuide.md`.
Use Pandoc and Chrome/Chromium to produce a self-contained HTML preview and print it:

```powershell
New-Item -ItemType Directory -Force build/diagram-review
pandoc docs/DeveloperGuide.md --standalone --embed-resources --resource-path=docs --metadata "title=MishMash Developer Guide" --css=docs/styles/guide-print.css --lua-filter=docs/styles/pdf-links.lua --output=build/diagram-review/DeveloperGuide.html
if ($LASTEXITCODE -ne 0) { throw "Pandoc export failed." }
python docs/diagrams/verify.py --html build/diagram-review/DeveloperGuide.html
if ($LASTEXITCODE -ne 0) { throw "Exported HTML verification failed; do not print the PDF." }
$workspacePath = (Get-Location).Path
$chromePath = "C:\Program Files\Google\Chrome\Application\chrome.exe"
$arguments = @("--headless", "--disable-gpu", "--no-pdf-header-footer",
    "--user-data-dir=$workspacePath\build\diagram-review\chrome-profile",
    "--print-to-pdf=$workspacePath\docs\DeveloperGuide.pdf",
    "file:///$($workspacePath.Replace('\', '/'))/build/diagram-review/DeveloperGuide.html")
Start-Process -FilePath $chromePath -ArgumentList $arguments -WindowStyle Hidden -Wait
```

These Windows commands assume the repository path contains no spaces; quote the path-valued
arguments when adapting them for another location. On other platforms, use the installed
Chrome/Chromium executable with the same print options. The print stylesheet retains complete
figures, fits them within A4 margins, and repeats table headers. The Lua filter makes links to
other repository documents portable while preserving same-document navigation.

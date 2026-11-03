# Meal recording UML diagrams

These sources document the `record-meal`, `view-meals`, `edit-meal` and `delete-meal` enhancement in
[the Developer Guide](../../DeveloperGuide.md#recording-meals-and-tracking-nutrition).

| Source | View |
| --- | --- |
| `classes.puml` | Commands and their collaborators |
| `model-classes.puml` | Stored model references and nutrition totals |
| `record-meal-parsing.puml` | Successful parsing of `record-meal` |
| `record-meal.puml` | Successful execution of `record-meal` |
| `view-meals.puml` | Execution of `view-meals` for a non-empty log |
| `flow.puml` | Activity diagram of `record-meal`, including every rejection |

`style.iuml` contains shared visual settings. Generated SVGs are stored under
`docs/images/gauthamnandi` and embedded directly in the guide, as for the other features.

## Regenerating

Use Java 25 and the [official PlantUML distribution](https://plantuml.com/download), or the
PlantUML Integration plugin in IntelliJ (save each preview as SVG with the same name as its
source). From the repository root, with `PLANTUML_JAR` set to the renderer path:

```powershell
java -jar $env:PLANTUML_JAR -checkonly "docs/diagrams/gauthamnandi/*.puml"
java -Djava.awt.headless=true -jar $env:PLANTUML_JAR -tsvg -o ../../images/gauthamnandi "docs/diagrams/gauthamnandi/*.puml"
```

The class diagram selects the built-in Java Smetana layout engine, so no separate Graphviz
installation is required.

After changing the code, check every depicted method, message order, association, and guard
against the implementation, then render again and inspect the images.

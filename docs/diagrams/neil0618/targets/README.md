# Daily targets UML diagrams

These sources document the implemented `set-targets` and `view-targets` commands and the way
`generate-plan` uses the saved targets, in
[the Developer Guide](../../../DeveloperGuide.md#setting-and-viewing-daily-targets).

| Source | View |
| --- | --- |
| `classes.puml` | Class diagram: classes that parse, store, and display saved targets |
| `generate-classes.puml` | Class diagram: classes that apply saved targets to plan generation |
| `flow.puml` | Activity diagram: how `set-targets` treats each token, including every rejection |
| `parsing.puml` | Sequence diagram: parsing `set-targets` |
| `set-targets.puml` | Sequence diagram: executing `set-targets` (refers to the parsing diagram) |
| `generate-plan.puml` | Sequence diagram: merging saved and typed targets, then generating and publishing the plan |

All diagrams use the shared style in `../../rixte/shortfall/style.iuml`; `flow.puml` adds a few
activity-diagram settings of its own. SVGs live under
`docs/images/neil0618/targets` so the guide displays without a PlantUML plugin.

## Regenerating

Use Java 25 and a local PlantUML JAR (the same one used for the other diagrams, or the PlantUML
Integration plugin in IntelliJ, saving each preview as SVG with the same name as its source).
From the repository root, set `PLANTUML_JAR` to the renderer path and run:

```powershell
java -version
java -jar $env:PLANTUML_JAR -checkonly "docs/diagrams/neil0618/targets/*.puml"
java "-Djava.awt.headless=true" -jar $env:PLANTUML_JAR -tsvg -o ../../../images/neil0618/targets "docs/diagrams/neil0618/targets/*.puml"
```

The output path is relative to each source directory. The class diagram uses the built-in Smetana
layout engine, so no separate Graphviz installation is needed.

Keep sources and rendered SVGs together when updating the design. Check depicted methods, message
order, and decision branches against the implementation, then inspect every rendered image.

# Recipe deletion UML diagrams

These sources document the implemented `delete-recipe` command in
[the Developer Guide](../../../DeveloperGuide.md#deleting-a-recipe).

| Source | View |
| --- | --- |
| `classes.puml` | Selected classes, including the confirmation hook on `Command` |
| `execution.puml` | A valid index, confirmed question, and successful deletion |
| `flow.puml` | Activity diagram showing where each outcome is decided, including invalid indexes |

All diagrams use the shared style in `../../rixte/shortfall/style.iuml`; `flow.puml` adds a few
activity-diagram settings of its own. SVGs live under `docs/images/neil0618/delete-recipe` so the guide
displays without a PlantUML plugin.

## Regenerating

Use Java 25 and a local PlantUML JAR (the same one used for the other diagrams, or the PlantUML
Integration plugin in IntelliJ, saving each preview as SVG with the same name as its source).
From the repository root, set `PLANTUML_JAR` to the renderer path and run:

```powershell
java -version
java -jar $env:PLANTUML_JAR -checkonly "docs/diagrams/neil0618/delete-recipe/*.puml"
java "-Djava.awt.headless=true" -jar $env:PLANTUML_JAR -tsvg -o ../../../images/neil0618/delete-recipe "docs/diagrams/neil0618/delete-recipe/*.puml"
```

The output path is relative to each source directory. The class diagram uses the built-in Smetana
layout engine, so no separate Graphviz installation is needed. PlantUML is a documentation tool,
not an application dependency.

Keep sources and rendered SVGs together when updating the design. Check depicted methods, message
order, and decision branches against the implementation, then inspect every rendered image. The
sequence diagrams condense command parsing and logging. The class diagram shows `RecipeBook` as
passed to the command, not stored by it, and shows that `MishMash`, not the command, reads the answer.

# Storage diagrams

Sources follow the course UML notation. `hide circle` suppresses PlantUML's C/I markers.
Class members use textual visibility; associations are solid, dependencies dashed, and realization uses a
dashed hollow triangle. Sequence calls are solid and returns dashed. Feature-irrelevant members are omitted.

Generate SVGs with Java 25 and PlantUML 1.2026.9:

```text
java -jar plantuml-1.2026.9.jar -tsvg -o ../../images/storage docs/diagrams/storage/classes.puml docs/diagrams/storage/autosave.puml
```

Keep both generated SVGs synchronized with these sources.

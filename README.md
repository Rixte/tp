# MishMash: The Meal Prep Planner

MishMash is a command-line meal prep planner for adding recipes and viewing generated meal plans and grocery lists.

## Setting up in Intellij

Prerequisites: JDK 25 (use the exact version), update Intellij to the most recent version.

1. **Ensure Intellij JDK 25 is defined as an SDK**, as described [here](https://www.jetbrains.com/help/idea/sdk.html#set-up-jdk) -- this step is not needed if you have used JDK 25 in a previous Intellij project.
1. **Import the project _as a Gradle project_**, as described [here](https://se-education.org/guides/tutorials/intellijImportGradleProject.html).
1. **Verify the setup**: After importing, locate `src/main/java/mishmash/MishMash.java`, right-click it, and choose `Run MishMash.main()`. You should see the MishMash banner and a prompt asking for your name. Enter your name, then use `add-recipe`, `view-plan`, or `view-list`. Type `bye` to exit.

**Warning:** Keep the `src\main\java` folder as the root folder for Java files (i.e., don't rename those folders or move Java files to another folder outside of this folder path), as this is the default location some tools (e.g., Gradle) expect to find Java files.

## Build automation using Gradle

* This project uses Gradle for build automation and dependency management. It includes a basic build script as well (i.e. the `build.gradle` file).
* If you are new to Gradle, refer to the [Gradle Tutorial at se-education.org/guides](https://se-education.org/guides/tutorials/gradle.html).
* Build with Java 25 using `./gradlew build` (`.\gradlew.bat build` on Windows), then run `java -jar build/libs/MishMash.jar`.

## Testing

The development build automatically retains user data in `data/mishmash.json` under the terminal's working
directory. Restart the JAR from the same directory to restore recipes, preferences, targets, plans, groceries,
and recorded meals. Keep the whole `data` folder when moving or backing up your data. See the
[User Guide](docs/UserGuide.md#data-retention) for recovery and unsaved-change warnings.

`./gradlew check` also builds the shadow JAR and tests retention by launching it twice in a temporary directory.

### JUnit tests

Run Java 25 `./gradlew check jacocoTestReport` to execute JUnit, Checkstyle, and coverage verification.
The report is `build/reports/jacoco/test/html/index.html`, with XML alongside it. Coverage includes the
packaged-JAR subprocess tests. Justin's grocery/nutrition, portion adjustment, meal customization, and storage
files are listed explicitly in the `SOURCEFILE` rule in `build.gradle`; each must exceed 95% for both lines
and branches (a `0.9501` minimum). Nested classes count toward their owning source file; no executable code
is excluded. Files with no branch instructions have no branch percentage to measure.

Storage failure tests use temporary directories and an injectable file-operation adapter to exercise atomic
move fallback, failed writes, quarantine failures, cleanup failures, and lock/channel failures consistently
across operating systems. They verify saved data and preserved causes, not only exception types.

* Tests live under `src/test/java/mishmash` and mirror the production packages. Run them with `./gradlew test` (`.\gradlew.bat test` on Windows).
* If you are new to JUnit, refer to the [JUnit Tutorial at se-education.org/guides](https://se-education.org/guides/tutorials/junit.html).

JLine is part of the console UI, and its automated tests run with JUnit. `ui/JLineConsoleInputTest`
checks editing, completion, history, interruption, and EOF using in-memory terminals or a scripted reader;
`ui/CommandCompleterTest` checks suggestions; `ui/ConsoleInputsTest` checks terminal selection and fallback;
and `JLineSessionTest` runs application workflows through the real JLine adapter. No native terminal is
required for these tests. The User Guide describes [interactive command entry](docs/UserGuide.md#interactive-command-entry).
Check actual terminal keys, display, and native-provider behaviour manually before release.

## Checkstyle

* A sample CheckStyle rule configuration is provided in this project.
* If you are new to Checkstyle, refer to the [Checkstyle Tutorial at se-education.org/guides](https://se-education.org/guides/tutorials/checkstyle.html).

## CI using GitHub Actions

The project uses [GitHub actions](https://github.com/features/actions) for CI. When you push a commit to this repo or PR against it, GitHub actions will run automatically to build and verify the code as updated by the commit/PR.

## Documentation

`/docs` folder contains a skeleton version of the project documentation.

Steps for publishing documentation to the public: 
1. If you are using this project template for an individual project, go your fork on GitHub.<br>
   If you are using this project template for a team project, go to the team fork on GitHub.
1. Click on the `settings` tab.
1. Scroll down to the `GitHub Pages` section.
1. Set the `source` as `master branch /docs folder`.
1. Optionally, use the `choose a theme` button to choose a theme for your documentation.

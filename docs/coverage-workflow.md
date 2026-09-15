# Coverage and Test Workflow

This document is the source of truth for future contributors and agents working on coverage,
tests, or the Kover badge.

## Test responsibilities

Keep coverage focused on the `:kompass` module:

- `commonTest` contains the multiplatform navigation-core contract: reducer behavior, navigation
  state, commands, serialization, deep links, metadata, results, scopes, and gesture state.
- `jvmTest` and `androidUnitTest` contain host/UI smoke and integration coverage: Compose hosts,
  layouts, transitions, tabs, predictive Back, and platform ownership.
- `samples` are usage examples and do not need a separate automated smoke-test task unless this
  policy is explicitly revisited.

Do not add tests only to raise a percentage. Test a behavior that protects a navigation contract.

## Local verification before a pull request

Run the following from the repository root:

```bash
./gradlew :kompass:jvmTest :kompass:koverVerify :kompass:koverHtmlReport
```

`koverVerify` enforces the current 60% total coverage baseline. The HTML report is generated at:

```text
kompass/build/reports/kover/html/index.html
```

The Kover setup intentionally measures `:kompass`; it does not include the sample application.
Run the JVM and Android unit-test tasks explicitly when validating host behavior. iOS framework
linking is not required for this coverage check.

## README badge

The README badge currently shows the latest manually verified class-coverage value, for example:

```markdown
[![Kover](https://img.shields.io/badge/Kover-87.9%25%20class%20coverage-brightgreen)](#testing)
```

Kover does not publish a dynamic badge by itself. After changing tests or production code:

1. Run the local verification command above.
2. Read the class-coverage value from the generated report.
3. Update the README badge manually if the value changed.
4. Mention the verified value in the pull request.

## iOS build memory constraint

On memory-constrained machines, do not link both release iOS frameworks concurrently:

```text
:samples:linkReleaseFrameworkIosArm64
:samples:linkReleaseFrameworkIosSimulatorArm64
```

Run one target at a time. A successful isolated link indicates that a previous
`OutOfMemoryError: Java heap space` was caused by concurrent memory pressure, not necessarily by
the source code. The pre-existing warning about an uninferred bundle ID is unrelated to coverage.

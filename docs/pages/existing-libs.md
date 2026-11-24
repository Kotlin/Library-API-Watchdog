# Adding the plugin to existing libraries

A change to the public API of a published library breaks its users. So the first run of the
Watchdog reports many diagnostics that the author can't act on. The `exemptExistingApi` task
acknowledges every error in one run:

```bash
./gradlew exemptExistingApi
```

Run it on a clean working tree. The task edits the source files.

## What the task changes

The task compiles every compilation except tests and edits every declaration with a diagnostic
whose configured severity is `ERROR`. Each declaration gets the `@Intentionally*` annotation of its
diagnostic with `ExemptionReason.FOR_BACKWARDS_COMPATIBILITY`. The annotation goes below the KDoc
and above the other annotations of the declaration. The task adds the imports.

The task doesn't edit a generated file in the build directory, so add its exemptions in the generator.

A diagnostic with the `WARNING` severity stays in the sources. The console prints the count, and
the [report](./report.md) lists each one. To exempt a check that is a warning, set it to
`WatchdogSeverity.ERROR` in `apiWatchdog` for the run. [`UNDOCUMENTED_PUBLIC_API`](./checks/undocumented-public-api.md)
is a warning by default, so the task doesn't add placeholder KDoc without this setting.

Three diagnostics get a different fix:

- [`UNDOCUMENTED_PUBLIC_API`](./checks/undocumented-public-api.md): a placeholder KDoc instead of
  `@IntentionallyUndocumented`, because documentation doesn't break any user.
- [`SUBCLASS_OPT_IN_WITHOUT_MARKERS`](./checks/subclass-opt-in-without-markers.md): a markerless
  `@SubclassOptInRequired` doesn't restrict any subclass, so the task replaces it with
  [`@IntentionallyOpen`](./checks/open-api-without-subclass-opt-in.md). The class stays open to everyone.
- [`DSL_MARKER_NOOP_TYPE_POSITION`](./checks/special/dsl-marker-noop-type-position.md): no exemption
  annotation exists. The task reports the diagnostic as a warning. Move or remove the marker by hand.

The placeholder KDoc satisfies the check until the real documentation replaces it:

```kotlin
/** TODO: Document this declaration */
public class Config
```

If many declarations expose the types of one dependency, declare the dependency with `api` or exempt it
on [`apiWatchdogExemptNonTransitive`](./checks/special/public-type-from-non-transitive-dependency.md#exempting-a-dependency)
before you run the task. This is a better fix than an annotation on each declaration.

## Review the result

The task writes the [API Watchdog report](./report.md). Its not-applied section lists the
diagnostics that need manual attention. The console prints the same list as warnings. In a build
with the [report aggregation plugin](./report.md#multi-project-builds), the task also writes the
aggregate reports of the root project.

Review the diff and commit it. The checks then report only the API that you add later. For a new
declaration, fix the shape or pick the exemption reason by hand.

A second run doesn't add anything, because an exempted declaration doesn't report a diagnostic.

## Collection mode

`exemptExistingApi` and `generateApiWatchdogReport` compile every compilation except tests in
collection mode. [The report page](./report.md) lists the compilations.
When neither task is in the task graph, the compilations run as configured. Collection mode changes
six things:

- A module without explicit API mode compiles with `-Xexplicit-api=warning`.
- Every enabled configurable check reports a warning, so the compilation passes. The configured
  severity still decides the fix: `exemptExistingApi` exempts a check set to `ERROR` and leaves a
  check set to `WARNING`. A check set to `NONE` in `apiWatchdog` doesn't record anything.
- `allWarningsAsErrors` is off, so the warnings don't fail the compilation.
- [`PUBLIC_TYPE_WITH_INTERNAL_API`](./checks/special/public-type-with-internal-api.md) and
  [`PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY`](./checks/special/public-type-from-non-transitive-dependency.md)
  report a warning, and `exemptExistingApi` exempts them. The second check is off in a source set that
  several targets share, such as `commonMain` with `jvm()` and `js()` or with `jvm()` and an Android
  target. The task doesn't add or remove its exemptions there.
- [`EXEMPTION_WITHOUT_EXPLANATION`](./checks/special/exemption-without-explanation.md) is off.
- In a multiplatform project, one compilation prints the diagnostics of a shared source set, so the
  build output has each of them once. A regular build prints them in every compilation of the source
  set. With an `androidTarget()`, a diagnostic in `androidMain` still prints once for each build
  variant.

`exemptExistingApi` removes a [redundant exemption](./checks/special/redundant-exemption.md), and
`generateApiWatchdogReport` tags it as redundant. In a multiplatform project, the task removes the
copies on the `actual` declarations with the exemption on the `expect` declaration.

Any other compilation error still fails the task. Fix unresolved references and syntax errors first.
The compilations outside collection mode still report the checks that are off for the `exemptExistingApi`
and `generateApiWatchdogReport` tasks. Fix them after you commit the generated exemptions.

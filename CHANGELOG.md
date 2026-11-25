# Changelog

## 0.3.0 - 2026-11-25

### Added

- Support for Kotlin 2.2.20 and later, and for IntelliJ IDEA 2025.2 and later.
- [API filters](https://kotlin.github.io/Library-API-Watchdog/exemptions#api-filters): the watchdog applies the
  filters of the ABI validation of the Kotlin Gradle plugin or of the Binary Compatibility Validator to its checks.
  In a project with filters, the update can add errors of
  [Internal API types in public API](https://kotlin.github.io/Library-API-Watchdog/checks/special/public-type-with-internal-api)
  and warnings of [Redundant exemptions](https://kotlin.github.io/Library-API-Watchdog/checks/special/redundant-exemption).
  `apiWatchdog { useAbiValidationFilters = false }` turns the filters off.
- [`ignoreDeprecatedApi`](https://kotlin.github.io/Library-API-Watchdog/configuration#deprecated-api): the checks skip
  every `@Deprecated` declaration.
- [`explicitApiRequirement`](https://kotlin.github.io/Library-API-Watchdog/configuration#explicit-api-mode): the checks
  can run in a project without explicit API mode.
- `exemptExistingApi` adds the exemption of
  [Public types from non-transitive dependencies](https://kotlin.github.io/Library-API-Watchdog/checks/special/public-type-from-non-transitive-dependency).

### Changed

- The packages and the compiler plugin id start with `kotlinx.library.watchdog`, not with
  `org.jetbrains.kotlinx.library.api.watchdog`.
- The artifact ids don't have the `kotlin-` prefix, for example `library-api-watchdog-plugin-annotations`.
- The setup doesn't need the dev kit EAP repository.
- The checks report the type of a `private` or `internal` `val` or `var` parameter of a public constructor, such as
  `class Cache(private val items: MutableList<String>)`.

<!-- Release notes generated using configuration in .github/release.yml at e7946bdb238d4177c6cfb5c5d53626076cca253b -->

### Other changes
* Run every check in the Release workflow and publish from Linux by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/37
* Document the dev kit EAP repository in the setup by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/38
* Add the report aggregation setup to the configuration page by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/39
* Make the explicit API mode requirement configurable by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/48
* Fix the daily compiler plugin tests and the merge of the dev kit update by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/47
* Update the dev kit to 0.0.4-dev-375 in https://github.com/Kotlin/Library-API-Watchdog/pull/51
* Group the Renovate updates by ecosystem by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/55
* Update the dev kit to 0.0.4-dev-376 in https://github.com/Kotlin/Library-API-Watchdog/pull/63
* Apply the ABI validation filters to the watchdog checks by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/59
* Print each Watchdog diagnostic once in exemptExistingApi and report runs by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/54
* Update the dev kit to 0.0.4-dev-380 in https://github.com/Kotlin/Library-API-Watchdog/pull/68
* Publish releases outside GitHub Actions, between the Pre-release and Post-publish workflows by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/70
* Point the repository and website links to Kotlin/Library-API-Watchdog by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/71
* Read the signing properties from the extra properties of the project by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/74
* Support 2.2.20+ and IDEA 2025.2+ by @kyay10 in https://github.com/Kotlin/Library-API-Watchdog/pull/73
* Rename the packages and artifact ids, order the report entries by position, and show the setup step of the IDE links by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/77
* Add the ignoreDeprecatedApi option to skip deprecated declarations by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/79
* Accept a type parameter with function bounds as a trailing lambda parameter by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/78
* Keep the report of `exemptExistingApi` for an abbreviated or relative task name by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/80
* Report every pull request check through one required check, `all_green` by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/82
* Compile against Kotlin 2.5.20-dev-5318 by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/83
* Get every plugin and Maven dependency from the kt-lib Space proxies by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/76
* Fix the undocumented API check in IntelliJ IDEA 2027.1 nightly builds by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/81
* Use the Develocity plugin 4.4.3, the newest that the server accepts by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/85
* Run fewer tests on a pull request and fix the flaky CI jobs by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/84
* Compile against Kotlin 2.5.20-dev-5510 by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/87
* Rename the Pre-release workflow to Pre-publish by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/86
* Split WatchdogProjectTest to balance the two forks of the functional tests by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/88
* Update the dev kit to 0.0.4-dev-403 by @api-watchdog[bot] in https://github.com/Kotlin/Library-API-Watchdog/pull/75
* Ignore fork pull requests in the workflow lookups and fix six smaller workflow defects by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/89
* Fix six defects of the aggregate HTML and SARIF reports by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/90
* Fix wrong edits and failure handling of the exemption fixer by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/92
* Compile against Kotlin 2.5.20-dev-5899 by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/110
* Fix eight build setups where the Gradle plugin failed or gave a wrong result by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/94
* Fix the redundant exemption verdicts for inferred types and for places that no check reads by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/95
* Fix nine checker results that don't match the public API by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/93
* Don't read a type-usage exemption through a type alias by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/96
* Update the dev kit to 0.0.4-dev-407 by @api-watchdog[bot] in https://github.com/Kotlin/Library-API-Watchdog/pull/98
* Check the supertypes that only an `actual` class declares by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/97
* Add the non-transitive dependency exemption in `exemptExistingApi`, and fix build tooling and docs defects by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/91
* Update the dev kit to 0.1.0 from Maven Central by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/115
* List the important changes of 0.3.0 in the changelog by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/116

### New Contributors
* @api-watchdog[bot] made their first contribution in https://github.com/Kotlin/Library-API-Watchdog/pull/75

**Full Changelog**: https://github.com/Kotlin/Library-API-Watchdog/compare/v0.2.0...v0.3.0

## 0.2.0 - 2026-11-06

<!-- Release notes generated using configuration in .github/release.yml at 20a11d69284725b68705ed22ee65497b1f61bdd9 -->

### Other changes
* Update the dev kit to 0.0.4-dev-356 by @github-actions[bot] in https://github.com/Kotlin/Library-API-Watchdog/pull/27
* Open the bot pull requests with a GitHub App token by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/28
* Update Gradle plugins, AGP, and the docs tooltip, and ignore Kotlin in Renovate by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/30
* Run the benchmark baseline check in one JVM by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/29
* Move the changelog after the checks in the docs sidebar by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/32
* Write llms.txt and a Markdown copy of each docs page by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/33
* Add the backwards compatibility exemption of PUBLIC_TYPE_WITH_INTERNAL_API by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/31
* Link diagnostic names in the website changelog by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/35


**Full Changelog**: https://github.com/Kotlin/Library-API-Watchdog/compare/v0.1.1...v0.2.0

## 0.1.1 - 2026-11-05

### Changed

- [Inconsistent parameter order in overloads](https://kotlin.github.io/Library-API-Watchdog/checks/inconsistent-parameter-order-in-overloads):
  a last parameter that accepts a trailing lambda can stay last in each overload. Such a parameter has a
  function type, a `fun interface` type, or a Java SAM interface type.
- [Required parameters after optional ones](https://kotlin.github.io/Library-API-Watchdog/checks/required-parameter-after-optional):
  the trailing-lambda exception also applies to a Java SAM interface in the last position.

<!-- Release notes generated using configuration in .github/release.yml at 2a8e491056a220fcc662ac583fd94b32f526911d -->

### Other changes
* Sign the commits of the Release and Dev kit update workflows by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/25

### New Contributors
* @github-actions[bot] made their first contribution in https://github.com/Kotlin/Library-API-Watchdog/pull/24

**Full Changelog**: https://github.com/Kotlin/Library-API-Watchdog/compare/v0.1.0...v0.1.1

## 0.1.0 - 2026-11-05

### Added

- Public API checks for Kotlin libraries, with configurable severity and exemption annotations.
- Automatic exemptions for existing API with the `exemptExistingApi` Gradle task.
- HTML and SARIF reports, with a Gradle plugin to combine reports from several modules.
  `exemptExistingApi` writes the combined reports too.
- Documentation for each check and an API reference for the annotations and Gradle plugins.
- [`REDUNDANT_EXEMPTION`](https://kotlin.github.io/Library-API-Watchdog/checks/special/redundant-exemption): a warning on an `@Intentionally*` annotation that its check doesn't need.
  The report tags the annotation as redundant, and `exemptExistingApi` removes it.

<!-- Release notes generated using configuration in .github/release.yml at 661e3e033d2b89ca40a585ee471f7dac4cd15703 -->

### Other changes
* Clean up by @kyay10 in https://github.com/Kotlin/Library-API-Watchdog/pull/2
* Versioned dumps by @kyay10 in https://github.com/Kotlin/Library-API-Watchdog/pull/3
* Restore Kotlin group by @kyay10 in https://github.com/Kotlin/Library-API-Watchdog/pull/4
* Update dev kit and adopt kotlinx library API packages by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/5
* Re-enable 2.5.20 tests using newer devkit by @kyay10 in https://github.com/Kotlin/Library-API-Watchdog/pull/10
* Remove `gradle-public-api` exclusion by @kyay10 in https://github.com/Kotlin/Library-API-Watchdog/pull/9
* Move to dev kit 0.0.4-dev-334 and the template's composite build, and make Kotlin 2.4.0 the minimum by @Mr3zee in https://github.com/Kotlin/Library-API-Watchdog/pull/23

### New Contributors
* @kyay10 made their first contribution in https://github.com/Kotlin/Library-API-Watchdog/pull/2

**Full Changelog**: https://github.com/Kotlin/Library-API-Watchdog/commits/v0.1.0
